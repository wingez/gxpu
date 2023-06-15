package se.wingez.ast

import se.wingez.tokens.*


val binaryOperationPriorities = mapOf(
    TokenType.PlusSign to 5,
    TokenType.MinusSign to 5,
    TokenType.LesserSign to 2,
    TokenType.GreaterSign to 2,
    TokenType.DoubleEqual to 2,
    TokenType.NotEqual to 1,
    TokenType.Deref to 10,
    TokenType.Dot to 10,

    )

class OperatorBuiltIns {
    companion object {
        const val Addition = "add"
        const val Subtraction = "sub"
        const val NotEqual = "ne"
        const val Equal = "eq"
        const val LessThan = "lt"
        const val GreaterThan = "gt"
        const val ArrayRead = "arrayread"
        const val ArrayWrite = "arraywrite"
    }
}

val binaryOperatorToNodesType = mapOf(
    TokenType.PlusSign to OperatorBuiltIns.Addition,
    TokenType.MinusSign to OperatorBuiltIns.Subtraction,
    TokenType.NotEqual to OperatorBuiltIns.NotEqual,
    TokenType.LesserSign to OperatorBuiltIns.LessThan,
    TokenType.GreaterSign to OperatorBuiltIns.GreaterThan,
    TokenType.DoubleEqual to OperatorBuiltIns.Equal,
)


private enum class ValueType {
    Token,
    Node,
    ParenthesisBlock,
}

private class Value(
    val type: ValueType,
    private val token: Token? = null,
    private val node: AstNode? = null,
    private val parenthesisBlock: Token? = null,
) {
    val valueToken get() = token!!
    val valueNode get() = node!!
    val valueParenthesisBlock get() = parenthesisBlock!!

    override fun toString(): String {
        return when (type) {
            ValueType.Node -> valueNode.toString()
            ValueType.Token -> valueToken.toString()
            ValueType.ParenthesisBlock -> valueParenthesisBlock.toString()
        }

    }
}

private data class ReduceResult(
    val result: Value
)

private const val priorityHighest = 100
private const val priorityLowest = 0

private interface Reducer {

    val priority: Int

    val requiredValueCount: Int
    fun tryReduce(values: List<Value>): ReduceResult?
}

private interface ValueMatcher {
    fun match(value: Value): Boolean
}

private object anyNodeMatcher : ValueMatcher {
    override fun match(value: Value): Boolean {
        return value.type == ValueType.Node
    }
}

private class TokenMatcher(
    private val tokenType: TokenType
) : ValueMatcher {
    override fun match(value: Value): Boolean {
        return value.type == ValueType.Token && value.valueToken.type == tokenType
    }
}

private class MultiTokenMatcher(
    private val tokens: Set<TokenType>
) : ValueMatcher {
    override fun match(value: Value): Boolean {
        if (value.type != ValueType.Token) {
            return false
        }
        return value.valueToken.type in tokens
    }
}

private abstract class MatchingReducer(
    private val matchers: List<ValueMatcher>
) : Reducer {
    final override val requiredValueCount = matchers.size

    final override fun tryReduce(values: List<Value>): ReduceResult? {
        for (i in 0 until requiredValueCount) {
            if (!matchers[i].match(values[i])) {
                return null
            }
        }
        return tryReduceMatched(values)
    }

    abstract fun tryReduceMatched(values: List<Value>): ReduceResult?
}

private class BinaryOperatorReducer(
    private val tokenType: TokenType,
) : MatchingReducer(
    listOf(anyNodeMatcher, TokenMatcher(tokenType), anyNodeMatcher)
) {
    override val priority = binaryOperationPriorities.getValue(tokenType)

    override fun tryReduceMatched(values: List<Value>): ReduceResult {
        return ReduceResult(
            Value(
                ValueType.Node,
                node = AstNode.fromBinaryOperation(
                    tokenType,
                    values[0].valueNode,
                    values[2].valueNode,
                )
            )
        )
    }
}

private class SingleValueParenthesis : MatchingReducer(
    listOf(TokenMatcher(TokenType.LeftParenthesis), anyNodeMatcher, TokenMatcher(TokenType.RightParenthesis))
) {
    override val priority = priorityLowest

    override fun tryReduceMatched(values: List<Value>): ReduceResult {
        return ReduceResult(Value(ValueType.Node, node = values[1].valueNode))
    }

}

private val reducers: List<Reducer> = listOf(
    SingleValueParenthesis()
) + binaryOperationPriorities.keys.map { BinaryOperatorReducer(it) }

private val reducersOrdered = reducers.sortedBy { -it.priority }

private fun applyReductions(values: List<Value>): AstNode {

    val valuesMutable = values.toMutableList()

    val maxIterations = valuesMutable.size

    for (i in 0..maxIterations) {
        if (valuesMutable.size == 1) {
            break
        }

        val valuesCount = valuesMutable.size

        var didReduce = false
        for (reducer in reducersOrdered) {
            for (startIndex in valuesMutable.indices) {
                if (startIndex + reducer.requiredValueCount > valuesCount) {
                    continue
                }

                val reduceResult =
                    reducer.tryReduce(valuesMutable.subList(startIndex, startIndex + reducer.requiredValueCount))

                if (reduceResult != null) {
                    val newValue = reduceResult.result

                    for (j in 0 until reducer.requiredValueCount) {
                        valuesMutable.removeAt(startIndex)
                    }
                    valuesMutable.add(startIndex, newValue)
                    didReduce = true
                }
                if (didReduce) {
                    break
                }
            }
            if (didReduce) {
                break
            }
        }
        if (!didReduce) {
            throw ParserError("could not parse expression")
        }


    }





    assert(valuesMutable.size == 1)
    val resultValue = valuesMutable.first()
    assert(resultValue.type == ValueType.Node)

    return resultValue.valueNode
}

private fun newParse(tokens: List<Token>): AstNode {

    // Do some sanity checks
    if (tokens.count{it.type==TokenType.LeftParenthesis} != tokens.count{it.type==TokenType.RightParenthesis}){
        throw ParserError("Mismatched parenthesis")
    }

    // Do initial reduction
    val valueList = tokens.map { token ->
        when (token.type) {
            TokenType.NumericConstant -> Value(ValueType.Node, node = AstNode.fromConstant(token.asConstant()))
            TokenType.Identifier -> Value(ValueType.Node, node = AstNode.fromIdentifier(token.additionalData))
            else -> Value(ValueType.Token, token = token)
        }
    }

    //TODO merge
    return applyReductions(valueList)
}


private fun parseSingleValue(tokens: TokenIterator): AstNode {

    if (tokens.peekIs(TokenType.LeftParenthesis, true)) {
        val result = parseExpressionUntilSeparator(tokens)
        tokens.consumeType(TokenType.RightParenthesis, "Mismatched parenthesis")
        return result
    } else if (tokens.peekIs(TokenType.NumericConstant)) {
        val constant = tokens.consumeType(TokenType.NumericConstant).asConstant()
        return AstNode.fromConstant(constant)
    } else if (tokens.peekIs(TokenType.String)) {
        val string = tokens.consumeType(TokenType.String).additionalData
        return AstNode.fromString(string)
    } else if (tokens.peekIs(TokenType.Identifier)) {

        val identifier = tokens.consumeIdentifier()

        if (tokens.peekIs(TokenType.LeftParenthesis, consumeMatch = true)) {
            val parameters = mutableListOf<AstNode>()

            while (!tokens.peekIs(TokenType.RightParenthesis, true)) {
                val paramValue = parseExpressionUntilSeparator(tokens)
                parameters.add(paramValue)

                tokens.peekIs(TokenType.Comma, true)
            }

            return AstNode.fromCall(identifier, FunctionType.Normal, parameters)
        }

        return AstNode.fromIdentifier(identifier)
    } else {
        throw ParserError("Cannot parse to value provider: ${tokens.peek()}")
    }
}

fun parseExpressionUntil(tokens: TokenIterator, delimiter: TokenType): AstNode {
    return parseExpressionUntil(tokens, listOf(delimiter))
}


fun parseExpressionUntil(tokens: TokenIterator, delimiter: List<TokenType>): AstNode {

    val result = mutableListOf<Token>()

    while (delimiter.all { !tokens.peekIs(it) }) {
        result.add(tokens.consume())
    }

    return newParse(result)
}

private fun parseExpressionUntilSeparator(tokens: TokenIterator): AstNode {


    val values = mutableListOf(parseSingleValue(tokens))
    val operations = mutableListOf<Token>()
    while (!tokens.peek().isExpressionSeparator()) {
        val operatorToken = tokens.consume()
        operations.add(operatorToken)


        //Close array access
        if (operatorToken.type == TokenType.LeftBracket) {
            values.add(parseExpressionUntilSeparator(tokens))
            tokens.consumeType(TokenType.RightBracket)
        } else {
            values.add(parseSingleValue(tokens))
        }
    }

    while (values.size > 1) {
        var highestPriority = 0
        var index = 0

        operations.forEachIndexed { i, token ->
            if (binaryOperationPriorities.getValue(token.type) > highestPriority) {
                highestPriority = binaryOperationPriorities.getValue(token.type)
                index = i
            }
        }
        val first = values.removeAt(index)
        val second = values.removeAt(index)
        val operatorToken = operations.removeAt(index)

        val result: AstNode

        if (operatorToken.type in binaryOperatorToNodesType) {
            result = AstNode.fromBinaryOperation(operatorToken.type, first, second)
        } else {
            result = when (operatorToken.type) {
                TokenType.Deref -> handleDeref(first, second)
                TokenType.Dot -> handleMemberAccess(first, second)
                TokenType.LeftBracket -> AstNode.fromArrayAccess(first, second)
                else -> throw ParserError("You have messed up badly... $operatorToken")
            }
        }

        values.add(index, result)
    }
    return values.first()
}

private fun handleMemberAccess(firstNode: AstNode, secondNode: AstNode): AstNode {
    // Separate case for "a.b" and "a.b()"
    // "a.b" should be mapped to memberaccess node
    // "a.b()" to instance function call

    if (secondNode.type == NodeTypes.Identifier) {
        return AstNode(NodeTypes.MemberAccess, secondNode.data as String, listOf(firstNode))
    }

    if (secondNode.type == NodeTypes.Call) {
        val callNode = secondNode.asCall()
        assert(callNode.functionType == FunctionType.Normal)
        return AstNode.fromCall(callNode.targetName, FunctionType.Instance, listOf(firstNode) + callNode.parameters)
    }

    throw ParserError("Expected either member identifier or instance function. Not $secondNode")
}

private fun handleDeref(firstNode: AstNode, secondNode: AstNode): AstNode {
    if (secondNode.type != NodeTypes.Identifier) {
        throw ParserError("Expected identifier, not $secondNode")
    }
    return AstNode(NodeTypes.MemberDeref, secondNode.data as String, listOf(firstNode))
}