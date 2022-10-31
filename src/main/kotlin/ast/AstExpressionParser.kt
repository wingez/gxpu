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


fun parseExpressionUntilSeparator(tokens: TokenIterator): AstNode {


    val values = mutableListOf(parseSingleValue(tokens))
    val operations = mutableListOf<Token>()
    while (!tokens.peek().isExpressionSeparator()) {
        val operatorToken = tokens.consume()
        operations.add(operatorToken)


        //Close array access
        if (operatorToken == TokenLeftBracket) {
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
            if (operationPriorities.getValue(token) > highestPriority) {
                highestPriority = operationPriorities.getValue(token)
                index = i
            }
        }
        val first = values.removeAt(index)
        val second = values.removeAt(index)
        val operatorToken = operations.removeAt(index)

        val result: AstNode

        if (operatorToken in operatorToNodesType) {
            result = AstNode.fromOperation(operatorToken, first, second)
        } else {
            result = when (operatorToken) {
                TokenDeref -> handleDeref(first, second)
                TokenDot -> handleMemberAccess(first, second)
                TokenLeftBracket -> AstNode.fromArrayAccess(first, second)
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