package se.wingez.ast.expression

import ast.expression.*
import se.wingez.ast.AstNode
import se.wingez.ast.FunctionType
import se.wingez.ast.NodeTypes
import se.wingez.ast.ParserError
import se.wingez.tokens.TokenType

private val priorities = object {

    val functionCall = 48

    val arrayAccess = 40
    val instanceFunction = 40

    val addressOf = 30
    val negate = 29

    val binaryPlusMinus = 28


    val binaryComparisons = 10


    val bracketsBlockToArray = 0

    //last try to extract a value from a single parenthesis
    val extractSingleValueFromParenthesis = 0

}
val binaryOperationPriorities = mapOf(
    TokenType.PlusSign to priorities.binaryPlusMinus,
    TokenType.LesserSign to priorities.binaryComparisons,
    TokenType.GreaterSign to priorities.binaryComparisons,
    TokenType.DoubleEqual to priorities.binaryComparisons,
    TokenType.NotEqual to priorities.binaryComparisons,
)
val binaryOperatorToNodesType = mapOf(
    TokenType.PlusSign to OperatorBuiltIns.Addition,
    TokenType.MinusSign to OperatorBuiltIns.Subtraction,
    TokenType.LesserSign to OperatorBuiltIns.LessThan,
    TokenType.GreaterSign to OperatorBuiltIns.GreaterThan,
    TokenType.DoubleEqual to OperatorBuiltIns.Equal,
    TokenType.NotEqual to OperatorBuiltIns.NotEqual,
)


private interface Reducer {

    val priority: Int

    val requiredValueCount: Int
    fun tryReduce(values: List<Value>): Value?
}


private abstract class MatchingReducer(
    private val matchers: List<ValueMatcher>
) : Reducer {
    final override val requiredValueCount = matchers.size

    final override fun tryReduce(values: List<Value>): Value? {
        for (i in 0 until requiredValueCount) {
            if (!matchers[i].match(values[i])) {
                return null
            }
        }
        return tryReduceMatched(values)
    }

    abstract fun tryReduceMatched(values: List<Value>): Value?
}


private class BinaryOperatorReducer(
    private val tokenType: TokenType,
) : MatchingReducer(
    listOf(anyNodeMatcher, TokenMatcher(tokenType), anyNodeMatcher)
) {
    override val priority = binaryOperationPriorities.getValue(tokenType)

    override fun tryReduceMatched(values: List<Value>): Value {
        return Value(
            ValueType.Node,
            node = AstNode.fromBinaryOperation(
                tokenType,
                values[0].valueNode,
                values[2].valueNode,
            )
        )
    }
}

private class UnaryOperatorReducer(
    tokenType: TokenType,
    override val priority: Int,
) : MatchingReducer(
    listOf(
        TokenMatcher(tokenType), anyNodeMatcher
    )
) {
    override fun tryReduceMatched(values: List<Value>): Value? {
        return Value(
            ValueType.Node, node =
            AstNode.fromCall(
                OperatorBuiltIns.Negate, FunctionType.Operator, listOf(values[1].valueNode)
            )
        )
    }
}

private class ExtractSingleValueParenthesis : MatchingReducer(
    listOf(
        TypeMatcher(ValueType.ParenthesisBlock),
    )
) {
    override val priority = priorities.extractSingleValueFromParenthesis

    override fun tryReduceMatched(values: List<Value>): Value? {
        val content = values[0].valueNodeList
        if (content.size != 1) {
            return null
        }

        return Value(ValueType.Node, node = content.first())
    }
}

private class FunctionCallReduce : MatchingReducer(
    listOf(anyNodeMatcher, TypeMatcher(ValueType.ParenthesisBlock))
) {
    override val priority: Int = priorities.functionCall

    override fun tryReduceMatched(values: List<Value>): Value? {
        val identifierNode = values[0].valueNode
        if (identifierNode.type != NodeTypes.Identifier) {
            return null
        }
        return Value(
            ValueType.Node,
            node = AstNode.fromCall(identifierNode.asIdentifier(), FunctionType.Normal, values[1].valueNodeList)
        )
    }
}

private class BracketBlockToArray : MatchingReducer(
    listOf(TypeMatcher(ValueType.BracketsBlock))
) {
    override val priority: Int = priorities.bracketsBlockToArray

    override fun tryReduceMatched(values: List<Value>): Value {
        return Value(
            ValueType.Node,
            node = AstNode.newArray(values[0].valueNodeList)
        )
    }
}

private class InstanceFunction : MatchingReducer(
    listOf(anyNodeMatcher, TokenMatcher(TokenType.Dot), NodeMatcher(NodeTypes.Call))
) {
    override val priority = priorities.instanceFunction
    override fun tryReduceMatched(values: List<Value>): Value? {

        val callInfo = values[2].valueNode.asCall()
        if (callInfo.functionType != FunctionType.Normal) {
            return null
        }


        return Value(
            ValueType.Node, node = AstNode.fromCall(
                callInfo.targetName, FunctionType.Instance, listOf(values[0].valueNode) + callInfo.parameters
            )
        )
    }
}

private class SubtractionReducer : MatchingReducer(
    listOf(anyNodeMatcher, NodeMatcher(NodeTypes.Call))
) {
    // Special case since we also have the 'negate' reducer which has higher priority
    // original:       (5-3)
    // negate applied: (5(-3))
    // this            5-3
    override val priority = priorities.binaryPlusMinus
    override fun tryReduceMatched(values: List<Value>): Value? {

        val mustBeNegate = values[1].valueNode.asCall()
        if (mustBeNegate.targetName != OperatorBuiltIns.Negate || mustBeNegate.functionType != FunctionType.Operator) {
            return null
        }

        return Value(
            ValueType.Node, node = AstNode.fromCall(
                OperatorBuiltIns.Subtraction,
                FunctionType.Operator,
                listOf(values[0].valueNode, mustBeNegate.parameters.first())
            )
        )
    }
}

private class ArrayAccess : MatchingReducer(
    listOf(anyNodeMatcher, TypeMatcher(ValueType.BracketsBlock))
) {
    override val priority = priorities.arrayAccess

    override fun tryReduceMatched(values: List<Value>): Value? {
        if (values[1].valueNodeList.size != 1) {
            return null
        }
        return Value(
            ValueType.Node,
            node = AstNode.fromArrayAccess(values[0].valueNode, values[1].valueNodeList.first())
        )
    }
}

private class AddressOfReducer : MatchingReducer(
    listOf(TokenMatcher(TokenType.Ampersand), anyNodeMatcher)
) {
    override val priority = priorities.addressOf
    override fun tryReduceMatched(values: List<Value>): Value {
        return Value(ValueType.Node, node = AstNode.fromAddressOf(values[1].valueNode))
    }
}

private val reducers: List<Reducer> = listOf(
    ExtractSingleValueParenthesis(),
    FunctionCallReduce(),
    BracketBlockToArray(),
    InstanceFunction(),
    ArrayAccess(),

    SubtractionReducer(),

    AddressOfReducer(),
    UnaryOperatorReducer(TokenType.MinusSign, priorities.negate),

    ) + binaryOperationPriorities.keys.map { BinaryOperatorReducer(it) }
private val reducersOrdered = reducers.sortedBy { -it.priority }
fun applyReductions(values: List<Value>): AstNode {

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

                val value =
                    reducer.tryReduce(valuesMutable.subList(startIndex, startIndex + reducer.requiredValueCount))

                if (value != null) {
                    for (j in 0 until reducer.requiredValueCount) {
                        valuesMutable.removeAt(startIndex)
                    }
                    valuesMutable.add(startIndex, value)
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
            throw ParserError("could not parse expression. Got to $valuesMutable")
        }
    }

    assert(valuesMutable.size == 1)
    val resultValue = valuesMutable.first()
    assert(resultValue.type == ValueType.Node) { resultValue.type.toString() }

    return resultValue.valueNode
}