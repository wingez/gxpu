package se.wingez.ast

import se.wingez.tokens.*


val operationPriorities = mapOf(
    TokenLeftBracket to 10,
    TokenPlusSign to 5,
    TokenMinusSign to 5,
    TokenLesserSign to 2,
    TokenGreaterSign to 2,
    TokenDoubleEqual to 2,
    TokenNotEqual to 1,
    TokenDeref to 10,
    TokenDot to 10,

    )

class OperatorBuiltIns {
    companion object {
        const val Addition = "add"
        const val Subtraction = "sub"
        const val NotEqual = "ne"
        const val Equal = "eq"
        const val LessThan = "lt"
        const val GreaterThan = "gt"
    }
}

val operatorToNodesType = mapOf(
    TokenPlusSign to OperatorBuiltIns.Addition,
    TokenMinusSign to OperatorBuiltIns.Subtraction,
    TokenNotEqual to OperatorBuiltIns.NotEqual,
    TokenLesserSign to OperatorBuiltIns.LessThan,
    TokenGreaterSign to OperatorBuiltIns.GreaterThan,
    TokenDoubleEqual to OperatorBuiltIns.Equal,
)


private fun parseSingleValue(tokens: TokenIterator): AstNode {

    if (tokens.peekIs(TokenLeftParenthesis, true)) {
        val result = parseExpressionUntilSeparator(tokens)
        tokens.consumeType(TokenRightParenthesis, "Mismatched parenthesis")
        return result
    } else if (tokens.peekIs<TokenNumericConstant>()) {
        val constant = tokens.consumeType<TokenNumericConstant>().value
        return AstNode.fromConstant(constant)
    } else if (tokens.peekIs<TokenString>()) {
        val stringToken = tokens.consumeType<TokenString>()
        return AstNode.fromString(stringToken.value)
    } else if (tokens.peekIs<TokenIdentifier>()) {

        val identifier = tokens.consumeIdentifier()

        if (tokens.peekIs(TokenLeftParenthesis, consumeMatch = true)) {
            val parameters = mutableListOf<AstNode>()

            while (!tokens.peekIs(TokenRightParenthesis, true)) {
                val paramValue = parseExpressionUntilSeparator(tokens)
                parameters.add(paramValue)

                tokens.peekIs(TokenComma, true)
            }

            return AstNode.fromCall(identifier, FunctionType.Normal, parameters)
        }

        return AstNode.fromIdentifier(identifier)
    } else {
        throw ParserError("Cannot parse to value provider: ${tokens.peek()}")
    }
}


fun parseExpressionUntilSeparator(tokens: TokenIterator): AstNode {


    val values = mutableListOf(parseSingleValue(tokens))
    val operations = mutableListOf<Token>()
    while (!tokens.peekIs<ExpressionSeparator>()) {
        val operatorToken = tokens.consume()
        operations.add(operatorToken)


        //Close array access
        if (operatorToken == TokenLeftBracket) {
            values.add(parseExpressionUntilSeparator(tokens))
            tokens.consumeType(TokenRightBracket)
        } else {
            values.add(parseSingleValue(tokens))
        }
    }

    while (values.size > 1) {
        var highestPriority = 0
        var index = 0

        operations.forEachIndexed { i, token ->
            if (operationPriorities.getValue(token) > highestPriority) {
                highestPriority = operationPriorities.getValue(token)
                index = i
            }
        }
        val first = values.removeAt(index)
        val second = values.removeAt(index)
        val operatorToken = operations.removeAt(index)

        fun secondAsIdentifier(): String {
            if (second.type != NodeTypes.Identifier) {
                throw ParserError("Expected identifier, not $operatorToken")
            }
            return second.data as String
        }

        val result: AstNode

        if (operatorToken in operatorToNodesType) {
            result = AstNode.fromOperation(operatorToken, first, second)
        } else {
            result = when (operatorToken) {
                TokenDeref -> AstNode(NodeTypes.MemberDeref, secondAsIdentifier(), listOf(first))
                TokenDot -> AstNode(NodeTypes.MemberAccess, secondAsIdentifier(), listOf(first))
                TokenLeftBracket -> AstNode.fromArrayAccess(first, second)
                else -> throw ParserError("You have messed up badly... $operatorToken")
            }
        }

        values.add(index, result)

    }

    return values.first()
}