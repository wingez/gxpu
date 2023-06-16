package ast.expression

import se.wingez.ast.*
import se.wingez.ast.expression.*
import se.wingez.tokens.Token
import se.wingez.tokens.TokenType


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


private fun parseBlocksUntil(valueList: List<Value>, matcher: ValueMatcher): Pair<Int, List<Value>> {

    val result = mutableListOf<Value>()

    var nextToParse = 0

    while (nextToParse < valueList.size) {

        val currentValue = valueList[nextToParse]
        if (matcher.match(currentValue)) {
            return nextToParse to result
        }

        nextToParse++

        if (TokenMatcher(TokenType.LeftParenthesis).match(currentValue)) {
            // Enter parenthesis block
            val parenthesisContent = mutableListOf<AstNode>()

            //Todo loop for commas
            val (amountConsumed, valuesInBlock) = parseBlocksUntil(
                valueList.subList(
                    nextToParse,
                    valueList.indices.last
                ),
                TokenMatcher(TokenType.RightParenthesis) //TODO also commas
            )
            if (valuesInBlock.isNotEmpty()) {
                // Todo this should not be allowed for comma separated
                parenthesisContent.add(applyReductions(valuesInBlock))
            }
            nextToParse += amountConsumed

            val mustBeRightParenthesis = valueList[nextToParse]
            nextToParse++
            if (!TokenMatcher(TokenType.RightParenthesis).match(mustBeRightParenthesis)) {
                throw ParserError("expected right parenthesis")
            }

            result.add(Value(ValueType.ParenthesisBlock, nodeList = parenthesisContent))


        } else {

            result.add(currentValue)
        }


    }

    return nextToParse to result
}


private fun parseValuesToExpression(valueList: List<Value>): AstNode {

    val (amountConsumed, values) = parseBlocksUntil(valueList, nothingMatcher)
    assert(amountConsumed == valueList.size)


    return applyReductions(values)
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

    return parseValuesToExpression(valueList)
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