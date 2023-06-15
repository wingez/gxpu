package se.wingez.ast

import se.wingez.tokens.*

private typealias Priority = Int

private val priorities = object {


    val mergeCommaSeparatedValues = 50
    val mergeIntoParenthesisBlock = 49

    val functionCall = 48

    val binaryPlusMinus = 28
    val binaryComparisons = 25


    //last try to extract a value from a single parenthesis
    val extractSingleValueFromParenthesis = 0

}


val binaryOperationPriorities = mapOf(
    TokenType.PlusSign to priorities.binaryPlusMinus,
    TokenType.MinusSign to priorities.binaryPlusMinus,
    TokenType.LesserSign to priorities.binaryComparisons,
    TokenType.GreaterSign to priorities.binaryComparisons,
    TokenType.DoubleEqual to priorities.binaryComparisons,
    TokenType.NotEqual to priorities.binaryComparisons,
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
    CommaSeparated,
    ParenthesisBlock,
}

private class Value(
    val type: ValueType,
    private val token: Token? = null,
    private val node: AstNode? = null,
    private val nodeList: List<AstNode>? = null
) {
    val valueToken get() = token!!
    val valueNode get() = node!!

    val valueNodeList get() = nodeList!!

    override fun toString(): String {
        return when (type) {
            ValueType.Node -> valueNode.toString()
            ValueType.Token -> valueToken.toString()
            ValueType.ParenthesisBlock -> valueNodeList.toString()
            ValueType.CommaSeparated -> valueNodeList.toString()
        }
    }
}

private data class ReduceResult(
    val result: Value
)

private interface Reducer {

    val priority: Int

    val requiredValueCount: Int
    fun tryReduce(values: List<Value>): ReduceResult?
}

private interface ValueMatcher {
    fun match(value: Value): Boolean
}

private object anythingMatcher : ValueMatcher {
    override fun match(value: Value) = true
}

private object anyNodeMatcher : ValueMatcher {
    override fun match(value: Value): Boolean {
        return value.type == ValueType.Node
    }
}


private class TypeMatcher(val type: ValueType) : ValueMatcher {
    override fun match(value: Value): Boolean {
        return value.type == type
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

private class MergeCommaSeparatedValues : MatchingReducer(
    listOf(anythingMatcher, TokenMatcher(TokenType.Comma), anythingMatcher)
) {
    override val priority: Int = priorities.mergeCommaSeparatedValues
    override fun tryReduceMatched(values: List<Value>): ReduceResult? {
        val left = when (values[0].type) {
            ValueType.Node -> listOf(values[0].valueNode)
            ValueType.CommaSeparated -> values[0].valueNodeList
            else -> return null
        }

        val right = when (values[2].type) {
            ValueType.Node -> listOf(values[2].valueNode)
            ValueType.CommaSeparated -> values[2].valueNodeList
            else -> return null
        }
        return ReduceResult(Value(ValueType.CommaSeparated, nodeList = left + right))
    }
}

private class MergeEmptyParenthesisBlock : MatchingReducer(
    listOf(TokenMatcher(TokenType.LeftParenthesis), TokenMatcher(TokenType.RightParenthesis))
) {
    override val priority: Int = priorities.mergeIntoParenthesisBlock

    override fun tryReduceMatched(values: List<Value>): ReduceResult {
        return ReduceResult(Value(ValueType.ParenthesisBlock, nodeList = emptyList()))
    }
}

private class MergeParenthesisBlock : MatchingReducer(
    listOf(TokenMatcher(TokenType.LeftParenthesis), anythingMatcher, TokenMatcher(TokenType.RightParenthesis))
) {
    override val priority: Int = priorities.mergeIntoParenthesisBlock

    override fun tryReduceMatched(values: List<Value>): ReduceResult? {
        val content = when (values[1].type) {
            ValueType.Node -> listOf(values[1].valueNode)
            ValueType.CommaSeparated -> values[1].valueNodeList
            else -> return null
        }
        return ReduceResult(Value(ValueType.ParenthesisBlock, nodeList = content))

    }
}

private class ExtractSingleValueParenthesis : MatchingReducer(
    listOf(
        TypeMatcher(ValueType.ParenthesisBlock),
    )
) {
    override val priority = priorities.extractSingleValueFromParenthesis

    override fun tryReduceMatched(values: List<Value>): ReduceResult? {
        val content = values[0].valueNodeList
        if (content.size != 1) {
            return null
        }

        return ReduceResult(Value(ValueType.Node, node = content.first()))
    }
}

private class FunctionCallReduce : MatchingReducer(
    listOf(anyNodeMatcher, TypeMatcher(ValueType.ParenthesisBlock))
) {
    override val priority: Int = priorities.functionCall

    override fun tryReduceMatched(values: List<Value>): ReduceResult? {
        val identiferNode = values[0].valueNode
        if (identiferNode.type != NodeTypes.Identifier) {
            return null
        }
        return ReduceResult(
            Value(
                ValueType.Node,
                node = AstNode.fromCall(identiferNode.asIdentifier(), FunctionType.Normal, values[1].valueNodeList)
            )
        )

    }
}

private val reducers: List<Reducer> = listOf(
    ExtractSingleValueParenthesis(),
    MergeCommaSeparatedValues(),
    MergeParenthesisBlock(),
    MergeEmptyParenthesisBlock(),
    MergeCommaSeparatedValues(),
    FunctionCallReduce(),

    ) + binaryOperationPriorities.keys.map { BinaryOperatorReducer(it) }

private val reducersOrdered = reducers.sortedBy { -it.priority }

private fun applyReductions(values: List<Value>): AstNode {

    val valuesMutable = values.toMutableList()

    val maxIterations = valuesMutable.size

    for (i in 0..maxIterations) {

        if (valuesMutable.size == 1 && valuesMutable.first().type == ValueType.Node) {
            // We've finished early
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
    assert(resultValue.type == ValueType.Node) { resultValue.type.toString() }

    return resultValue.valueNode
}

private fun newParse(tokens: List<Token>): AstNode {

    // Do some sanity checks
    if (tokens.count { it.type == TokenType.LeftParenthesis } != tokens.count { it.type == TokenType.RightParenthesis }) {
        throw ParserError("Mismatched parenthesis")
    }

    // Do initial reduction
    val valueList = tokens.map { token ->
        when (token.type) {
            TokenType.NumericConstant -> Value(ValueType.Node, node = AstNode.fromConstant(token.asConstant()))
            TokenType.Identifier -> Value(ValueType.Node, node = AstNode.fromIdentifier(token.additionalData))
            TokenType.String -> Value(ValueType.Node, node = AstNode.fromString(token.additionalData))
            else -> Value(ValueType.Token, token = token)
        }
    }

    //TODO merge
    return applyReductions(valueList)
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