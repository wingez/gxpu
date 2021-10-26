package se.wingez.ast

import se.wingez.tokens.*

class ParserError(message: String) : Exception(message)

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
        return tokens[index++]
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

        if (peekIs(TokenColon, consumeMatch = true)) {
            explicitNew = peekIs(TokenKeywordNew, consumeMatch = true)
            type = consumeIdentifier()

        }
        return PrimitiveMemberDeclaration(name, type, explicitNew)
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

    fun parseStatementsUntilEndblock(): List<StatementNode> {
        val expressions = mutableListOf<StatementNode>()

        while (!peekIs(TokenEndBlock, true)) {
            if (peekIs(TokenEOL, true))
                continue

            val newStatement = parseStatement()
            expressions.add(newStatement)
        }

        return expressions

    }

    fun parseStatement(): StatementNode {
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

    fun parseValueProvider(): ValueNode {
        val firstResult: ValueNode
        var hasParenthesis = false
        if (peekIs(TokenLeftParenthesis, true)) {
            hasParenthesis = true
            firstResult = parseValueProvider()
            consumeType(TokenRightParenthesis, "Mismatched parenthesis")

        } else if (peekIs<TokenNumericConstant>()) {
            firstResult = ConstantNode.fromToken(consumeType())
        } else if (peekIs<TokenIdentifier>()) {
            val callNode = tryParse { parseCall(false) }
            firstResult = if (callNode != null)
                callNode
            else {
                val member = consumeIdentifier()
                Identifier(member)
            }
        } else {
            throw ParserError("Cannot parse to value provider: ${peek()}")
        }

        val nextToken = peek()
        if (nextToken is ExpressionSeparator) {
            return firstResult
        }
        if (nextToken is TokenSingleOperation) {
            consume()

            if (nextToken == TokenDot) {
                val target = consumeIdentifier()
                return MemberAccess(firstResult, target)
            }


            val secondResult = parseValueProvider()

            if (!(hasParenthesis || secondResult is Identifier || secondResult is ConstantNode))
                throw ParserError("Operation too complex for now. Use more parentheses")

            val operation = when (nextToken) {
                TokenPlusSign -> Operation.Addition
                TokenMinusSign -> Operation.Subtraction
                TokenNotEqual -> Operation.NotEquals
                else -> throw ParserError("Dont know how to parse $nextToken")
            }
            return SingleOperationNode(operation, firstResult, secondResult)
        }
        throw ParserError("$nextToken was not expected")
    }


    fun parseCall(shouldConsumeEol: Boolean): CallNode {
        val targetName = consumeType<TokenIdentifier>().target
        consumeType(TokenLeftParenthesis)

        val parameters = mutableListOf<ValueNode>()

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