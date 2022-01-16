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

    private fun savepoint(): Int {
        return index
    }

    private fun restore(savepoint: Int) {
        index = savepoint
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

            var tok: AstNode?
            tok = tryParse(this::parseFunctionDefinition)
            if (tok != null) {
                result.add(tok)
                continue
            }
            tok = tryParse(this::parseStruct)
            if (tok != null) {
                result.add(tok)
                continue
            }


            throw ParserError("Could not parse")

        }
        return result
    }

    private fun <T> tryParse(toCall: () -> T): T? {

        val savepoint = savepoint()
        try {
            return toCall()
        } catch (e: ParserError) {
            restore(savepoint)
        }

        return null
    }

    private fun parsePrimitiveMemberDeclaration(): PrimitiveMemberDeclaration {
        /**
        Parses 'val:type' or 'val' or 'val:new type'
         */
        val name = consumeIdentifier()

        var explicitNew = false
        var type = ""
        var array = false

        if (peekIs(TokenColon, consumeMatch = true)) {
            explicitNew = peekIs(TokenKeywordNew, consumeMatch = true)
            type = consumeIdentifier()

            if (peekIs(TokenLeftBracket)) {
                consumeType(TokenLeftBracket)
                consumeType(TokenRightBracket)
                array = true
            }

        }
        return PrimitiveMemberDeclaration(name, type, explicitNew, array)
    }

    fun parseFunctionDefinition(): FunctionNode {
        consumeType(TokenKeywordDef)
        val name = consumeType<TokenIdentifier>().target
        consumeType(TokenLeftParenthesis)

        val parameters = mutableListOf<PrimitiveMemberDeclaration>()
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

        return FunctionNode(name, parameters, statements, returnType)
    }

    fun parseStatementsUntilEndblock(): List<AstNode> {
        val expressions = mutableListOf<AstNode>()

        while (!peekIs(TokenEndBlock, true)) {
            if (peekIs(TokenEOL, true))
                continue

            val newStatement = parseStatement()
            expressions.add(newStatement)
        }

        return expressions

    }

    fun parseStatement(): AstNode {
        tryParse(this::parseAssignment)?.also { return it }
        tryParse(this::parseAssignmentNoInit)?.also { return it }
        tryParse(this::parsePrint)?.also { return it }
        tryParse { parseCall(true) }?.also { return it }
        tryParse(this::parseIfStatement)?.also { return it }
        tryParse(this::parseWhileStatement)?.also { return it }
        tryParse(this::parseReturnStatement)?.also { return it }

        throw ParserError("Dont know how to parse ${peek()}")
    }

    fun parseAssignment(): AssignNode {
        val target = parseValueProvider()
        consumeType(TokenAssign)
        val valueNode = parseValueProvider()
        consumeType(TokenEOL)
        return AssignNode(target, valueNode)
    }

    private fun parseAssignmentNoInit(): PrimitiveMemberDeclaration {
        val value = parsePrimitiveMemberDeclaration()
        consumeType(TokenEOL)
        return value
    }

    fun parsePrint(): PrintNode {
        consumeType(TokenKeywordPrint)
        consumeType(TokenLeftParenthesis)
        val target = parseValueProvider()
        consumeType(TokenRightParenthesis)
        consumeType(TokenEOL)
        return PrintNode(target)
    }

    fun parseIfStatement(): IfNode {
        consumeType(TokenKeywordIf)
        val condition = parseValueProvider()

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

        return IfNode(condition, statements, elseStatements)
    }

    fun parseWhileStatement(): WhileNode {
        consumeType(TokenKeywordWhile)
        val condition = parseValueProvider()

        consumeType(TokenColon)
        consumeType(TokenEOL)
        consumeType(TokenBeginBlock)

        val statements = parseStatementsUntilEndblock()

        return WhileNode(condition, statements)
    }

    fun parseReturnStatement(): ReturnNode {
        consumeType(TokenKeywordReturn)

        val value = if (!peekIs(TokenEOL)) parseValueProvider() else null
        consumeType(TokenEOL)
        return ReturnNode(value)
    }

    private fun parseSingleValue(): AstNode {

        if (peekIs(TokenLeftParenthesis, true)) {
            val result = parseValueProvider()
            consumeType(TokenRightParenthesis, "Mismatched parenthesis")
            return result
        } else if (peekIs<TokenNumericConstant>()) {
            return ConstantNode.fromToken(consumeType())
        } else if (peekIs<TokenIdentifier>()) {
            val callNode = tryParse { parseCall(false) }
            return if (callNode != null)
                callNode
            else {
                val member = consumeIdentifier()
                Identifier(member)
            }
        } else {
            throw ParserError("Cannot parse to value provider: ${peek()}")
        }
    }

    fun parseValueProvider(): AstNode {


        val values = mutableListOf(parseSingleValue())
        val operations = mutableListOf<Token>()
        while (!peekIs<ExpressionSeparator>()) {
            val operatorToken = consume()
            operations.add(operatorToken)


            //Close array access
            if (operatorToken == TokenLeftBracket) {
                values.add(parseValueProvider())
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
                TokenDeref -> MemberDeref(first, secondAsIdentifier())
                TokenDot -> MemberAccess(first, secondAsIdentifier())
                TokenLeftBracket -> ArrayAccess(first, second)
                else -> throw ParserError("You have messed up badly... $operatorToken")
            }

            values.add(index, result)

        }

        return values.first()
    }

    fun parseCall(shouldConsumeEol: Boolean): CallNode {
        val targetName = consumeType<TokenIdentifier>().target
        consumeType(TokenLeftParenthesis)

        val parameters = mutableListOf<AstNode>()

        while (!peekIs(TokenRightParenthesis, true)) {
            val paramValue = parseValueProvider()
            parameters.add(paramValue)

            peekIs(TokenComma, true)
        }

        if (shouldConsumeEol)
            consumeType(TokenEOL)

        return CallNode(targetName, parameters)
    }

    fun parseStruct(): StructNode {
        consumeType(TokenKeywordStruct)
        val name = consumeIdentifier()
        consumeType(TokenColon)
        consumeType(TokenEOL)
        consumeType(TokenBeginBlock)

        val members = mutableListOf<PrimitiveMemberDeclaration>()

        while (!peekIs(TokenEndBlock, true)) {
            if (peekIs(TokenEOL, true))
                continue

            members.add(parsePrimitiveMemberDeclaration())
        }
        return StructNode(name, members)
    }

}