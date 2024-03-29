package ast.expression

import ast.AstNode
import ast.TokenIterator
import ast.syntaxerror.throwSyntaxError
import tokens.Token
import tokens.TokenType


class OperatorBuiltIns {
    companion object {
        const val Addition = "add"
        const val Subtraction = "sub"
        const val Negate = "negate"
        const val NotEqual = "ne"
        const val Equal = "eq"
        const val LessThan = "lt"
        const val GreaterThan = "gt"
        const val ArrayRead = "arrayread"
        const val ArrayWrite = "arraywrite"
    }
}

private enum class BlockType {
    Parenthesis,
    Bracket,
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

        val blockType = if (TokenMatcher(TokenType.LeftParenthesis).match(currentValue)) {
            BlockType.Parenthesis
        } else if (TokenMatcher(TokenType.LeftBracket).match(currentValue)) {
            BlockType.Bracket
        } else {
            null
        }

        if (blockType != null) {
            // Enter block
            val blockContent = mutableListOf<AstNode>()
            val closingToken = when (blockType) {
                BlockType.Parenthesis -> TokenType.RightParenthesis
                BlockType.Bracket -> TokenType.RightBracket
            }

            while (true) {


                val (amountConsumed, valuesInBlock) = parseBlocksUntil(
                    valueList.subList(
                        nextToParse,
                        valueList.size
                    ),
                    MultiTokenMatcher(setOf(closingToken, TokenType.Comma))
                )



                nextToParse += amountConsumed
                if (nextToParse >= valueList.size) {
                    throwSyntaxError("Block not closed", currentValue.sourceInfo)
                }
                val commaOrClosingParenthesis = valueList[nextToParse]
                nextToParse++
                if (TokenMatcher(closingToken).match(commaOrClosingParenthesis)) {
                    if (valuesInBlock.isNotEmpty()) {
                        blockContent.add(applyReductions(valuesInBlock))
                    }
                    break
                } else if (TokenMatcher(TokenType.Comma).match(commaOrClosingParenthesis)) {
                    if (valuesInBlock.isEmpty())
                        throwSyntaxError("Missing item before comma", commaOrClosingParenthesis.sourceInfo)
                    blockContent.add(applyReductions(valuesInBlock))
                } else {
                    throw NotImplementedError("somehting messed up")
                }
            }

            val block = when (blockType) {
                BlockType.Parenthesis -> Value.parenthesisBlock(blockContent, currentValue.sourceInfo)
                BlockType.Bracket -> Value.bracketBlock(blockContent, currentValue.sourceInfo)
            }

            result.add(block)
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

    // Do initial reduction
    val valueList = tokens.map { token ->
        when (token.type) {
            TokenType.NumericConstant -> Value.node(AstNode.fromConstant(token.asConstant(), token.sourceInfo))
            TokenType.Identifier -> Value.node(AstNode.fromIdentifier(token))
            TokenType.String -> Value.node(AstNode.fromString(token.additionalData, token.sourceInfo))
            else -> Value.token(token)
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