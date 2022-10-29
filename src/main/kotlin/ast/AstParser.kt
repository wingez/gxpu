package se.wingez.ast

import se.wingez.tokens.*

class ParserError(message: String) : Exception(message)


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
            TokenType.KeywordDef -> result.add(parseFunctionDefinition())
            TokenType.KeywordStruct -> result.add(parseStruct())

            TokenType.KeywordIf -> result.add(parseIfStatement())
            TokenType.KeywordWhile -> result.add(parseWhileStatement())

            TokenType.KeywordReturn -> result.add(parseReturnStatement())
            TokenType.Break -> result.add(parseBreakStatement())

            TokenType.KeywordVal -> result.addAll(parseNewValDeclaration())

            else -> result.addAll(parseExpression())
        }

        return result
    }

    fun parseNewValDeclaration(): List<AstNode> {
        consumeType(TokenKeywordVal)

        val memberName = consumeIdentifier()
        val optionalTypeDefinition: TypeDefinition?

        if (peekIs(TokenColon)) {
            optionalTypeDefinition = parseTypeDefinition(allowNoTypeName = false)
        } else {
            optionalTypeDefinition = null
        }

        val optionalTypeHint: AstNode?
        if (peekIs(TokenAssign, consumeMatch = true)) {
            optionalTypeHint = parseExpressionUntilSeparator()
        } else {
            optionalTypeHint = null
        }

        if (optionalTypeDefinition == null && optionalTypeHint == null) {
            throw ParserError("Either typeDefinition or something to assign must be provided")
        }

        val newVariableNode = AstNode.fromNewVariable(memberName, optionalTypeDefinition, optionalTypeHint)

        if (optionalTypeHint == null) {
            // Only new variable
            return listOf(newVariableNode)
        }
        // Also assign default value
        return listOf(newVariableNode, AstNode.fromAssign(AstNode.fromIdentifier(memberName), optionalTypeHint))
    }

    private fun parsePrimitiveMemberDeclaration(): AstNode {
        /**
        Parses 'val:type' or 'val' or 'val:new type'
         */

        val memberName = consumeIdentifier()
        val typeDefinition = parseTypeDefinition(allowNoTypeName = false)

        return AstNode.fromNewVariable(memberName, typeDefinition, null)
    }

    fun parseFunctionDefinition(): AstNode {
        consumeType(TokenKeywordDef)

        val parameters = mutableListOf<AstNode>()

        val type: FunctionType
        if (peekIs(TokenLeftParenthesis, consumeMatch = true)) {
            val classType = parsePrimitiveMemberDeclaration()
            parameters.add(classType)
            type = FunctionType.Instance
            consumeType(TokenRightParenthesis)
        } else {
            type = FunctionType.Normal
        }

        val name = consumeType<TokenIdentifier>().target
        consumeType(TokenLeftParenthesis)

        while (!peekIs(TokenRightParenthesis, true)) {
            val member = parsePrimitiveMemberDeclaration()
            parameters.add(member)

            peekIs(TokenComma, true)
        }

        val returnTypeDef = parseOptionalTypeDefinition(allowNoTypeName = true)
            ?: throw ParserError("Invalid return type")

        consumeType(TokenEOL)
        consumeType(TokenBeginBlock)

        val statements = parseStatementsUntilEndblock()

        return AstNode.fromFunction(name, type, parameters, statements, returnTypeDef)
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

    fun parseBreakStatement(): AstNode {
        consumeType(TokenKeywordBreak)
        return AstNode.fromBreak()
    }

    fun parseTypeDefinition(allowNoTypeName: Boolean): TypeDefinition {
        return parseOptionalTypeDefinition(allowNoTypeName)
            ?: throw ParserError("")
    }

    fun parseOptionalTypeDefinition(allowNoTypeName: Boolean): TypeDefinition? {
        if (!peekIs(TokenColon, consumeMatch = true)) {
            return null
        }
        val explicitNew = peekIs(TokenKeywordNew, consumeMatch = true)

        val type: String = if (!allowNoTypeName) {
            consumeIdentifier()
        } else {
            if (peekIs<TokenIdentifier>()) {
                consumeIdentifier()
            } else {
                return TypeDefinition(VOID_TYPE_NAME)
            }
        }

        val isArray = peekIs(TokenLeftBracket)
        if (isArray) {
            consumeType(TokenLeftBracket)
            consumeType(TokenRightBracket)
        }
        return TypeDefinition(type, explicitNew, isArray)
    }

    fun parseExpression(): List<AstNode> {
        var first = parseExpressionUntilSeparator()

        if (peekIs(TokenEOL)) {
            return listOf(first)
        }

        if (peekIs(TokenAssign, consumeMatch = true)) {
            val right = parseExpressionUntilSeparator()

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
        } else if (peekIs<TokenString>()) {
            val stringToken = consumeType<TokenString>()
            return AstNode.fromString(stringToken.value)
        } else if (peekIs<TokenIdentifier>()) {

            val identifier = consumeIdentifier()

            if (peekIs(TokenLeftParenthesis, consumeMatch = true)) {
                val parameters = mutableListOf<AstNode>()

                while (!peekIs(TokenRightParenthesis, true)) {
                    val paramValue = parseExpressionUntilSeparator()
                    parameters.add(paramValue)

                    peekIs(TokenComma, true)
                }

                return AstNode.fromCall(identifier, FunctionType.Normal, parameters)
            }

            return AstNode.fromIdentifier(identifier)
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