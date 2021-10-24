package se.wingez.ast

import se.wingez.*
import kotlin.reflect.KFunction0
import kotlin.reflect.typeOf

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
            throw ParserError("Expected token to be of type ${typeOf<T>()}")
        return consume() as T
    }

    private fun consumeType(token: Token): Token {
        if (!peekIs(token))
            throw ParserError("Expected token to be of type $token")
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

    private fun <T> tryParse(toCall: KFunction0<T>): T? {

        val savepoint = savepoint()
        try {
            return toCall()
        } catch (e: ParserError) {
            restore(savepoint)
        }

        return null
    }

    private fun parsePrimitiveMemberDeclaration(allowModifiers: Boolean): AssignTarget {
        /**
        Parses 'val:type' or 'val' or 'val:new type'
         */
        val targetToken = consumeType<TokenIdentifier>()

        val modifiers = mutableListOf<MemberAccessModifier>()
        if (allowModifiers) {
            while (peekIs(TokenDot, consumeMatch = true)) {
                val identifier = consumeType<TokenIdentifier>()
                modifiers.add(MemberAccessModifier(identifier.target))
            }
        }
        val member = MemberAccess(targetToken.target, modifiers)
        if (peekIs(TokenColon, consumeMatch = true)) {
            val explicitNew = peekIs(TokenKeywordNew, consumeMatch = true)
            val typeNode = consumeType<TokenIdentifier>()

            return AssignTarget(member, typeNode.target, explicitNew)
        }
        return AssignTarget(member)
    }

    fun parseFunctionDefinition(): FunctionNode {
        consumeType(TokenKeywordDef)
        val name = consumeType<TokenIdentifier>().target
        consumeType(TokenLeftParenthesis)

        val parameters = mutableListOf<AssignTarget>()
        while (!peekIs(TokenRightParenthesis, true)) {
            val member = parsePrimitiveMemberDeclaration(false)
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
        tryParse(this::parseCall)?.also { return it }
        tryParse(this::parseIfStatement)?.also { return it }
        tryParse(this::parseWhileStatement)?.also { return it }
        tryParse(this::parseReturnStatement)?.also { return it }

        throw ParserError("Dont know how to parse ${peek()}")
    }

    fun parseAssignment(): AssignNode {
        val assignment = parsePrimitiveMemberDeclaration(true)
        consumeType(TokenEquals)
        val valueNode = parseValueProvider()
        consumeType(TokenEOL)
        return AssignNode(assignment, valueNode)
    }

    private fun parseAssignmentNoInit(): AssignNode {
        val value = parsePrimitiveMemberDeclaration(false)
        consumeType(TokenEOL)
        return AssignNode(value, null)
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

    fun parseValueProvider(): ValueProviderNode {
        val firstResult: ValueProviderNode
        if (peekIs<TokenNumericConstant>()) {
            firstResult = ConstantNode.fromToken(consumeType())
        } else if (peekIs<TokenIdentifier>()) {
            val savepoint = savepoint()
            firstResult = try {
                parseCall(false)
            } catch (e: ParserError) {
                restore(savepoint)
                val identifier = consumeIdentifier()
                if (peekIs(TokenDot, true)) {
                    val member = consumeIdentifier()
                    MemberAccess(identifier, listOf(MemberAccessModifier(member)))
                } else {
                    MemberAccess(identifier)
                }
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

            val secondResult = parseValueProvider()

            if (!((secondResult is MemberAccess) || (secondResult is ConstantNode)))
                throw ParserError("Operation to complex for now")

            val operation = when (nextToken) {
                TokenPlusSign -> Operation.Plus
                TokenMinusSign -> Operation.Minus
                else -> throw ParserError("Dont know how to parse $nextToken")
            }
            return SingleOperationNode(operation, firstResult, secondResult)
        }
        throw ParserError("$nextToken was not expected")
    }

    fun parseCall(): CallNode {
        return parseCall(true)
    }

    fun parseCall(shouldConsumeEol: Boolean): CallNode {
        val targetName = consumeType<TokenIdentifier>().target
        consumeType(TokenLeftParenthesis)

        val parameters = mutableListOf<ValueProviderNode>()

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

        val members = mutableListOf<AssignTarget>()

        while (!peekIs(TokenEndBlock, true)) {
            if (peekIs(TokenEOL, true))
                continue

            members.add(parsePrimitiveMemberDeclaration(allowModifiers = false))
        }
        return StructNode(name, members)
    }

}