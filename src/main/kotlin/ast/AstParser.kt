package se.wingez.ast

import se.wingez.tokens.*

class ParserError(message: String) : Exception(message)


class AstParser(tokens: List<Token>) {
    companion object {
        const val VOID_TYPE_NAME = "void"
    }

    val tokens = TokenIterator(tokens)


    fun parse(): List<AstNode> {
        val result = mutableListOf<AstNode>()

        while (tokens.hasMore()) {

            // Filter empty line
            if (tokens.peekIs(TokenEOL, consumeMatch = true))
                continue

            result.addAll(parseNextNode())
        }
        return result
    }

    fun parseNextNode(): List<AstNode> {
        val result = mutableListOf<AstNode>()

        when (tokens.peek().type) {
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
        tokens.consumeType(TokenKeywordVal)

        val memberName = tokens.consumeIdentifier()
        val optionalTypeDefinition: TypeDefinition?

        if (tokens.peekIs(TokenColon)) {
            optionalTypeDefinition = parseTypeDefinition(allowNoTypeName = false)
        } else {
            optionalTypeDefinition = null
        }

        val optionalTypeHint: AstNode?
        if (tokens.peekIs(TokenAssign, consumeMatch = true)) {
            optionalTypeHint = parseExpressionUntilSeparator(tokens)
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

    fun parseExpression(): List<AstNode> {
        var first = parseExpressionUntilSeparator(tokens)

        if (tokens.peekIs(TokenEOL)) {
            return listOf(first)
        }

        if (tokens.peekIs(TokenAssign, consumeMatch = true)) {
            val right = parseExpressionUntilSeparator(tokens)

            first = AstNode.fromAssign(
                first, right
            )
        }

        return listOf(first)
    }

    private fun parsePrimitiveMemberDeclaration(): AstNode {
        /**
        Parses 'val:type' or 'val' or 'val:new type'
         */

        val memberName = tokens.consumeIdentifier()
        val typeDefinition = parseTypeDefinition(allowNoTypeName = false)

        return AstNode.fromNewVariable(memberName, typeDefinition, null)
    }

    fun parseFunctionDefinition(): AstNode {
        tokens.consumeType(TokenKeywordDef)

        val parameters = mutableListOf<AstNode>()

        val type: FunctionType
        if (tokens.peekIs(TokenLeftParenthesis, consumeMatch = true)) {
            val classType = parsePrimitiveMemberDeclaration()
            parameters.add(classType)
            type = FunctionType.Instance
            tokens.consumeType(TokenRightParenthesis)
        } else {
            type = FunctionType.Normal
        }

        val name = tokens.consumeType<TokenIdentifier>().target
        tokens.consumeType(TokenLeftParenthesis)

        while (!tokens.peekIs(TokenRightParenthesis, true)) {
            val member = parsePrimitiveMemberDeclaration()
            parameters.add(member)

            tokens.peekIs(TokenComma, true)
        }

        val returnTypeDef = parseOptionalTypeDefinition(allowNoTypeName = true)
            ?: throw ParserError("Invalid return type")

        tokens.consumeType(TokenEOL)
        tokens.consumeType(TokenBeginBlock)

        val statements = parseStatementsUntilEndblock()

        return AstNode.fromFunction(name, type, parameters, statements, returnTypeDef)
    }

    fun parseStatementsUntilEndblock(): List<AstNode> {
        val expressions = mutableListOf<AstNode>()

        while (!tokens.peekIs(TokenEndBlock, true)) {
            if (tokens.peekIs(TokenEOL, true))
                continue

            val newExpressions = parseNextNode()
            expressions.addAll(newExpressions)
        }

        return expressions

    }

    fun parseIfStatement(): AstNode {
        tokens.consumeType(TokenKeywordIf)
        val condition = parseExpressionUntilSeparator(tokens)

        tokens.consumeType(TokenColon)
        tokens.consumeType(TokenEOL)
        tokens.consumeType(TokenBeginBlock)

        val statements = parseStatementsUntilEndblock()


        val elseStatements = if (tokens.hasMore() && tokens.peekIs(TokenKeywordElse, true)) {
            tokens.consumeType(TokenColon)
            tokens.consumeType(TokenEOL)
            tokens.consumeType(TokenBeginBlock)

            parseStatementsUntilEndblock()
        } else {
            emptyList()
        }

        return AstNode.fromIf(condition, statements, elseStatements)
    }

    fun parseWhileStatement(): AstNode {
        tokens.consumeType(TokenKeywordWhile)
        val condition = parseExpressionUntilSeparator(tokens)

        tokens.consumeType(TokenColon)
        tokens.consumeType(TokenEOL)
        tokens.consumeType(TokenBeginBlock)

        val statements = parseStatementsUntilEndblock()

        return AstNode.fromWhile(condition, statements)
    }

    fun parseReturnStatement(): AstNode {
        tokens.consumeType(TokenKeywordReturn)

        val value = if (!tokens.peekIs(TokenEOL)) parseExpressionUntilSeparator(tokens) else null
        tokens.consumeType(TokenEOL)
        return AstNode.fromReturn(value)
    }

    fun parseBreakStatement(): AstNode {
        tokens.consumeType(TokenKeywordBreak)
        return AstNode.fromBreak()
    }

    fun parseTypeDefinition(allowNoTypeName: Boolean): TypeDefinition {
        return parseOptionalTypeDefinition(allowNoTypeName)
            ?: throw ParserError("")
    }

    fun parseOptionalTypeDefinition(allowNoTypeName: Boolean): TypeDefinition? {
        if (!tokens.peekIs(TokenColon, consumeMatch = true)) {
            return null
        }
        val explicitNew = tokens.peekIs(TokenKeywordNew, consumeMatch = true)

        val type: String = if (!allowNoTypeName) {
            tokens.consumeIdentifier()
        } else {
            if (tokens.peekIs<TokenIdentifier>()) {
                tokens.consumeIdentifier()
            } else {
                return TypeDefinition(VOID_TYPE_NAME)
            }
        }

        val isArray = tokens.peekIs(TokenLeftBracket)
        if (isArray) {
            tokens.consumeType(TokenLeftBracket)
            tokens.consumeType(TokenRightBracket)
        }
        return TypeDefinition(type, explicitNew, isArray)
    }


    fun parseStruct(): AstNode {
        tokens.consumeType(TokenKeywordStruct)
        val name = tokens.consumeIdentifier()
        tokens.consumeType(TokenColon)
        tokens.consumeType(TokenEOL)
        tokens.consumeType(TokenBeginBlock)

        val members = mutableListOf<AstNode>()

        while (!tokens.peekIs(TokenEndBlock, true)) {
            if (tokens.peekIs(TokenEOL, true))
                continue

            members.add(parsePrimitiveMemberDeclaration())
        }
        return AstNode.fromStruct(name, members)
    }

}