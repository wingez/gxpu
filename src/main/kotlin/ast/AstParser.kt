package se.wingez.ast

import ast.expression.parseExpressionUntil
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
            TokenType.KeywordBreak -> result.add(parseBreakStatement())

            TokenType.KeywordVal -> result.addAll(parseNewValDeclaration())

            else -> result.addAll(parseExpression())
        }

        return result
    }

    fun parseNewValDeclaration(): List<AstNode> {
        tokens.consumeType(TokenType.KeywordVal)

        val memberName = tokens.consumeIdentifier()
        val optionalTypeDefinition: TypeDefinition?

        if (tokens.peekIs(TokenType.Colon,consumeMatch = true)) {
            optionalTypeDefinition = parseTypeDefinition()
        } else {
            optionalTypeDefinition = null
        }

        val optionalTypeHint: AstNode?
        if (tokens.peekIs(TokenType.Equals, consumeMatch = true)) {
            optionalTypeHint = parseExpressionUntil(tokens, TokenType.EOL)
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
        var first = parseExpressionUntil(tokens, listOf(TokenType.EOL, TokenType.Equals))

        if (tokens.peekIs(TokenType.EOL)) {
            return listOf(first)
        }

        if (tokens.peekIs(TokenType.Equals, consumeMatch = true)) {
            val right = parseExpressionUntil(tokens, TokenType.EOL)

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
        tokens.consumeType(TokenType.Colon)
        val typeDefinition = parseTypeDefinition()

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

        tokens.consumeType(TokenType.Colon)
        val optionalReturnTypeDef = if (!tokens.peekIs(TokenType.EOL)) {
            parseTypeDefinition()
        } else {
            null
        }

        tokens.consumeType(TokenType.EOL)
        tokens.consumeType(TokenType.BeginBlock)

        val statements = parseStatementsUntilEndblock()

        return AstNode.fromFunction(name, type, parameters, statements, optionalReturnTypeDef)
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
        val condition = parseExpressionUntil(tokens, TokenType.Colon)

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
        val condition = parseExpressionUntil(tokens, TokenType.Colon)

        tokens.consumeType(TokenType.Colon)
        tokens.consumeType(TokenType.EOL)
        tokens.consumeType(TokenType.BeginBlock)

        val statements = parseStatementsUntilEndblock()

        return AstNode.fromWhile(condition, statements)
    }

    fun parseReturnStatement(): AstNode {
        tokens.consumeType(TokenType.KeywordReturn)

        val value = if (!tokens.peekIs(TokenType.EOL)) parseExpressionUntil(tokens, TokenType.EOL) else null
        tokens.consumeType(TokenType.EOL)
        return AstNode.fromReturn(value)
    }

    fun parseBreakStatement(): AstNode {
        tokens.consumeType(TokenType.KeywordBreak)
        return AstNode.fromBreak()
    }

    fun parseTypeDefinition(): TypeDefinition {
        val isPointer = tokens.peekIs(TokenType.Star, consumeMatch = true)

        val type: String = tokens.consumeIdentifier()

        val isArray = tokens.peekIs(TokenType.LeftBracket)
        if (isArray) {
            tokens.consumeType(TokenType.LeftBracket)
            tokens.consumeType(TokenType.RightBracket)
        }
        return TypeDefinition(type, isPointer = isPointer, isArray = isArray)
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