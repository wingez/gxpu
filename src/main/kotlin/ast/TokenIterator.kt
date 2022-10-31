package se.wingez.ast

import se.wingez.tokens.Token
import se.wingez.tokens.TokenType

class TokenIterator(private val tokens: List<Token>) {

    private var index = 0

    fun peek(): Token {
        if (index > tokens.size)
            throw ParserError("End of token-list reached")
        return tokens[index]
    }

    fun peekIs(tokenType: TokenType, consumeMatch: Boolean = false): Boolean {
        val result = peek().type == tokenType
        if (result && consumeMatch) {
            consume()
        }
        return result
    }

    fun consume(): Token {
        val result = peek()
        index++
        return result
    }

    fun consumeType(tokenType: TokenType): Token {
        return consumeType(tokenType, "Expected token to be of type $tokenType")
    }

    fun consumeType(tokenType: TokenType, errorMessage: String): Token {
        if (!peekIs(tokenType))
            throw ParserError(errorMessage)
        return consume()
    }

    fun consumeIdentifier(): String {
        return consumeType(TokenType.Identifier).additionalData
    }

    fun hasMore(): Boolean {
        return index < tokens.size
    }

}