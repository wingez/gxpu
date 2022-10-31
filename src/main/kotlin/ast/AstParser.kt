package se.wingez.ast

import se.wingez.TypePeekIterator
import se.wingez.tokens.*

class ParserError(message: String) : Exception(message)

class TokenIterator(tokens: List<Token>) : TypePeekIterator<Token, TokenType>(tokens) {
    fun consumeIdentifier(): String {
        return consumeType(TokenType.Identifier).additionalData
    }
}

class AstParser(tokens: List<Token>) {
    companion object {
        const val VOID_TYPE_NAME = "void"
    }

    val tokens = TokenIterator(tokens)


    fun parse(): List<AstNode> {
        val result = mutableListOf<AstNode>()

        while (tokens.hasMore()) {

            // Filter empty line
            if (tokens.peekIs(TokenType.EOL, consumeMatch = true))
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
        tokens.consumeType(TokenType.KeywordVal)

        val memberName = tokens.consumeIdentifier()
        val optionalTypeDefinition: TypeDefinition?

        if (tokens.peekIs(TokenType.Colon)) {
            optionalTypeDefinition = parseTypeDefinition(allowNoTypeName = false)
        } else {
            optionalTypeDefinition = null
        }

        val optionalTypeHint: AstNode?
        if (tokens.peekIs(TokenType.Equals, consumeMatch = true)) {
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

        if (tokens.peekIs(TokenType.EOL)) {
            return listOf(first)
        }

        if (tokens.peekIs(TokenType.Equals, consumeMatch = true)) {
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
        tokens.consumeType(TokenType.KeywordDef)

        val parameters = mutableListOf<AstNode>()

        val type: FunctionType
        if (tokens.peekIs(TokenType.LeftParenthesis, consumeMatch = true)) {
            val classType = parsePrimitiveMemberDeclaration()
            parameters.add(classType)
            type = FunctionType.Instance
            tokens.consumeType(TokenType.RightParenthesis)
        } else {
            type = FunctionType.Normal
        }

        val name = tokens.consumeType(TokenType.Identifier).additionalData
        tokens.consumeType(TokenType.LeftParenthesis)

        while (!tokens.peekIs(TokenType.RightParenthesis, true)) {
            val member = parsePrimitiveMemberDeclaration()
            parameters.add(member)

            tokens.peekIs(TokenType.Comma, true)
        }

        val returnTypeDef = parseOptionalTypeDefinition(allowNoTypeName = true)
            ?: throw ParserError("Invalid return type")

        tokens.consumeType(TokenType.EOL)
        tokens.consumeType(TokenType.BeginBlock)

        val statements = parseStatementsUntilEndblock()

        return AstNode.fromFunction(name, type, parameters, statements, returnTypeDef)
    }

    fun parseStatementsUntilEndblock(): List<AstNode> {
        val expressions = mutableListOf<AstNode>()

        while (!tokens.peekIs(TokenType.EndBlock, true)) {
            if (tokens.peekIs(TokenType.EOL, true))
                continue

            val newExpressions = parseNextNode()
            expressions.addAll(newExpressions)
        }

        return expressions

    }

    fun parseIfStatement(): AstNode {
        tokens.consumeType(TokenType.KeywordIf)
        val condition = parseExpressionUntilSeparator(tokens)

        tokens.consumeType(TokenType.Colon)
        tokens.consumeType(TokenType.EOL)
        tokens.consumeType(TokenType.BeginBlock)

        val statements = parseStatementsUntilEndblock()


        val elseStatements = if (tokens.hasMore() && tokens.peekIs(TokenType.KeywordElse, true)) {
            tokens.consumeType(TokenType.Colon)
            tokens.consumeType(TokenType.EOL)
            tokens.consumeType(TokenType.BeginBlock)

            parseStatementsUntilEndblock()
        } else {
            emptyList()
        }

        return AstNode.fromIf(condition, statements, elseStatements)
    }

    fun parseWhileStatement(): AstNode {
        tokens.consumeType(TokenType.KeywordWhile)
        val condition = parseExpressionUntilSeparator(tokens)

        tokens.consumeType(TokenType.Colon)
        tokens.consumeType(TokenType.EOL)
        tokens.consumeType(TokenType.BeginBlock)

        val statements = parseStatementsUntilEndblock()

        return AstNode.fromWhile(condition, statements)
    }

    fun parseReturnStatement(): AstNode {
        tokens.consumeType(TokenType.KeywordReturn)

        val value = if (!tokens.peekIs(TokenType.EOL)) parseExpressionUntilSeparator(tokens) else null
        tokens.consumeType(TokenType.EOL)
        return AstNode.fromReturn(value)
    }

    fun parseBreakStatement(): AstNode {
        tokens.consumeType(TokenType.Break)
        return AstNode.fromBreak()
    }

    fun parseTypeDefinition(allowNoTypeName: Boolean): TypeDefinition {
        return parseOptionalTypeDefinition(allowNoTypeName)
            ?: throw ParserError("")
    }

    fun parseOptionalTypeDefinition(allowNoTypeName: Boolean): TypeDefinition? {
        if (!tokens.peekIs(TokenType.Colon, consumeMatch = true)) {
            return null
        }
        val explicitNew = tokens.peekIs(TokenType.KeywordNew, consumeMatch = true)

        val type: String = if (!allowNoTypeName) {
            tokens.consumeIdentifier()
        } else {
            if (tokens.peekIs(TokenType.Identifier)) {
                tokens.consumeIdentifier()
            } else {
                return TypeDefinition(VOID_TYPE_NAME)
            }
        }

        val isArray = tokens.peekIs(TokenType.LeftBracket)
        if (isArray) {
            tokens.consumeType(TokenType.LeftBracket)
            tokens.consumeType(TokenType.RightBracket)
        }
        return TypeDefinition(type, explicitNew, isArray)
    }


    fun parseStruct(): AstNode {
        tokens.consumeType(TokenType.KeywordStruct)
        val name = tokens.consumeIdentifier()
        tokens.consumeType(TokenType.Colon)
        tokens.consumeType(TokenType.EOL)
        tokens.consumeType(TokenType.BeginBlock)

        val members = mutableListOf<AstNode>()

        while (!tokens.peekIs(TokenType.EndBlock, true)) {
            if (tokens.peekIs(TokenType.EOL, true))
                continue

            members.add(parsePrimitiveMemberDeclaration())
        }
        return AstNode.fromStruct(name, members)
    }

}