package se.wingez.ast

import se.wingez.tokens.*

class ParserError(message: String) : Exception(message)


val operationPriorities = mapOf(
    TokenLeftBracket to 10,
    TokenPlusSign to 5,
    TokenMinusSign to 5,
    TokenNotEqual to 1,
    TokenDeref to 10,
    TokenDot to 10,
)


class AstParser(private val tokens: List<Token>) {
    companion object {
        const val VOID_TYPE_NAME = "void"
    }

    private var index = 0

    private fun peek(): Token {
        if (index > tokens.size)
            throw ParserError("End of token-list reached")
        return tokens[index]
    }

    private inline fun <reified T : Token> peekIs(consumeMatch: Boolean = false): Boolean {
        val result = peek() is T
        if (result && consumeMatch) {
            consume()
        }
        return result
    }

    private fun peekIs(token: Token, consumeMatch: Boolean = false): Boolean {
        val result = peek() == token
        if (result && consumeMatch) {
            consume()
        }
        return result
    }

    private fun consume(): Token {
        val result = peek()
        index++
        return result
    }

    private inline fun <reified T : Token> consumeType(): T {
        if (!peekIs<T>())
            throw ParserError("Token was not of expected type")
        return consume() as T
    }

    private fun consumeType(token: Token): Token {
        return consumeType(token, "Expected token to be of type $token")
    }

    private fun consumeType(token: Token, errorMessage: String): Token {
        if (!peekIs(token))
            throw ParserError(errorMessage)
        return consume()
    }

    private fun consumeIdentifier(): String {
        return consumeType<TokenIdentifier>().target
    }

    private fun hasMoreToParse(): Boolean {
        return index < tokens.size
    }

    fun parse(): List<AstNode> {
        val result = mutableListOf<AstNode>()

        while (hasMoreToParse()) {

            // Filter empty line
            if (peekIs(TokenEOL, consumeMatch = true))
                continue

            result.addAll(parseNextNode())
        }
        return result
    }

    fun parseNextNode(): List<AstNode> {
        val result = mutableListOf<AstNode>()

        when (peek().type) {
            TokenType.KeywordPrint -> result.add(parsePrint())
            TokenType.KeywordDef -> result.add(parseFunctionDefinition())
            TokenType.KeywordStruct -> result.add(parseStruct())

            TokenType.KeywordIf -> result.add(parseIfStatement())
            TokenType.KeywordWhile -> result.add(parseWhileStatement())

            TokenType.KeywordReturn -> result.add(parseReturnStatement())

            else -> result.addAll(parseExpression())
        }

        return result
    }

    private fun parsePrimitiveMemberDeclaration(): AstNode {
        /**
        Parses 'val:type' or 'val' or 'val:new type'
         */

        val node = parseExpression()[0]
        if (node.type != NodeTypes.MemberDeclaration) {
            throw ParserError("Expected memberdeclaration, not $node")
        }
        return node

    }

    fun parseFunctionDefinition(): AstNode {
        consumeType(TokenKeywordDef)
        val name = consumeType<TokenIdentifier>().target
        consumeType(TokenLeftParenthesis)

        val parameters = mutableListOf<AstNode>()
        while (!peekIs(TokenRightParenthesis, true)) {
            val member = parsePrimitiveMemberDeclaration()
            parameters.add(member)

            peekIs(TokenComma, true)
        }
        consumeType(TokenColon)
        var returnType = VOID_TYPE_NAME
        if (!peekIs(TokenEOL)) {
            returnType = consumeIdentifier()
        }

        consumeType(TokenEOL)
        consumeType(TokenBeginBlock)

        val statements = parseStatementsUntilEndblock()

        return AstNode.fromFunction(name, parameters, statements, returnType)
    }

    fun parseStatementsUntilEndblock(): List<AstNode> {
        val expressions = mutableListOf<AstNode>()

        while (!peekIs(TokenEndBlock, true)) {
            if (peekIs(TokenEOL, true))
                continue

            val newExpressions = parseNextNode()
            expressions.addAll(newExpressions)
        }

        return expressions

    }

    fun parsePrint(): AstNode {
        consumeType(TokenKeywordPrint)
        consumeType(TokenLeftParenthesis)
        val target = parseExpressionUntilSeparator()
        consumeType(TokenRightParenthesis)
        consumeType(TokenEOL)
        return AstNode.fromPrint(target)
    }

    fun parseIfStatement(): AstNode {
        consumeType(TokenKeywordIf)
        val condition = parseExpressionUntilSeparator()

        consumeType(TokenColon)
        consumeType(TokenEOL)
        consumeType(TokenBeginBlock)

        val statements = parseStatementsUntilEndblock()


        val elseStatements = if (hasMoreToParse() && peekIs(TokenKeywordElse, true)) {
            consumeType(TokenColon)
            consumeType(TokenEOL)
            consumeType(TokenBeginBlock)

            parseStatementsUntilEndblock()
        } else {
            emptyList()
        }

        return AstNode.fromIf(condition, statements, elseStatements)
    }

    fun parseWhileStatement(): AstNode {
        consumeType(TokenKeywordWhile)
        val condition = parseExpressionUntilSeparator()

        consumeType(TokenColon)
        consumeType(TokenEOL)
        consumeType(TokenBeginBlock)

        val statements = parseStatementsUntilEndblock()

        return AstNode.fromWhile(condition, statements)
    }

    fun parseReturnStatement(): AstNode {
        consumeType(TokenKeywordReturn)

        val value = if (!peekIs(TokenEOL)) parseExpressionUntilSeparator() else null
        consumeType(TokenEOL)
        return AstNode.fromReturn(value)
    }

    fun parseExpression(): List<AstNode> {
        var first = parseExpressionUntilSeparator()

        if (peekIs(TokenEOL)) {
            return listOf(first)
        }


        if (peekIs(TokenColon, consumeMatch = true)) {
            val explicitNew = peekIs(TokenKeywordNew, consumeMatch = true)
            val type = consumeIdentifier()
            var array = false

            if (peekIs(TokenLeftBracket)) {
                consumeType(TokenLeftBracket)
                consumeType(TokenRightBracket)
                array = true
            }
            if (first.type != NodeTypes.Identifier) {
                throw ParserError("Expected membername, not $first")
            }

            first = AstNode.fromMemberDeclaration(
                MemberDeclarationData(
                    first.asIdentifier(), type, explicitNew, array
                )
            )
        }

        if (peekIs(TokenAssign, consumeMatch = true)) {
            val right = parseExpressionUntilSeparator()

            if (first.type == NodeTypes.MemberDeclaration) {

                return listOf(
                    first,
                    AstNode.fromAssign(
                        AstNode.fromIdentifier(first.asMemberDeclaration().name),
                        right,
                    )
                )
            }

            first = AstNode.fromAssign(
                first, right
            )
        }

        return listOf(first)

    }

    private fun parseSingleValue(): AstNode {

        if (peekIs(TokenLeftParenthesis, true)) {
            val result = parseExpressionUntilSeparator()
            consumeType(TokenRightParenthesis, "Mismatched parenthesis")
            return result
        } else if (peekIs<TokenNumericConstant>()) {
            val constant = consumeType<TokenNumericConstant>().value
            return AstNode.fromConstant(constant)
        } else if (peekIs<TokenIdentifier>()) {

            val identifier = consumeIdentifier()

            if (peekIs(TokenLeftParenthesis, consumeMatch = true)) {
                val parameters = mutableListOf<AstNode>()

                while (!peekIs(TokenRightParenthesis, true)) {
                    val paramValue = parseExpressionUntilSeparator()
                    parameters.add(paramValue)

                    peekIs(TokenComma, true)
                }

                return AstNode.fromCall(identifier, parameters)
            }

            return AstNode.fromIdentifier(identifier)
        } else if (peekIs(TokenKeywordPrint, consumeMatch = true)) {
            consumeType(TokenLeftParenthesis)
            val argument = parseExpressionUntilSeparator()
            consumeType(TokenRightParenthesis)
            return AstNode.fromPrint(argument)
        } else {
            throw ParserError("Cannot parse to value provider: ${peek()}")
        }
    }

    fun parseExpressionUntilSeparator(): AstNode {


        val values = mutableListOf(parseSingleValue())
        val operations = mutableListOf<Token>()
        while (!peekIs<ExpressionSeparator>()) {
            val operatorToken = consume()
            operations.add(operatorToken)


            //Close array access
            if (operatorToken == TokenLeftBracket) {
                values.add(parseExpressionUntilSeparator())
                consumeType(TokenRightBracket)
            } else {
                values.add(parseSingleValue())
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

            val result = when (operatorToken) {
                TokenPlusSign -> AstNode.fromOperation(NodeTypes.Addition, first, second)
                TokenMinusSign -> AstNode.fromOperation(NodeTypes.Subtraction, first, second)
                TokenNotEqual -> AstNode.fromOperation(NodeTypes.NotEquals, first, second)
                TokenDeref -> AstNode(NodeTypes.MemberDeref, secondAsIdentifier(), listOf(first))
                TokenDot -> AstNode(NodeTypes.MemberAccess, secondAsIdentifier(), listOf(first))
                TokenLeftBracket -> AstNode.fromArrayAccess(first, second)
                else -> throw ParserError("You have messed up badly... $operatorToken")
            }

            values.add(index, result)

        }

        return values.first()
    }


    fun parseStruct(): AstNode {
        consumeType(TokenKeywordStruct)
        val name = consumeIdentifier()
        consumeType(TokenColon)
        consumeType(TokenEOL)
        consumeType(TokenBeginBlock)

        val members = mutableListOf<AstNode>()

        while (!peekIs(TokenEndBlock, true)) {
            if (peekIs(TokenEOL, true))
                continue

            members.add(parsePrimitiveMemberDeclaration())
        }
        return AstNode.fromStruct(name, members)
    }

}