package se.wingez.ast

import se.wingez.tokens.Token
import se.wingez.tokens.TokenIdentifier

class TokenIterator(private val tokens: List<Token>) {

    private var index = 0

    fun peek(): Token {
        if (index > tokens.size)
            throw ParserError("End of token-list reached")
        return tokens[index]
    }

    inline fun <reified T : Token> peekIs(consumeMatch: Boolean = false): Boolean {
        val result = peek() is T
        if (result && consumeMatch) {
            consume()
        }
        return result
    }

    fun peekIs(token: Token, consumeMatch: Boolean = false): Boolean {
        val result = peek() == token
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

    inline fun <reified T : Token> consumeType(): T {
        if (!peekIs<T>())
            throw ParserError("Token was not of expected type")
        return consume() as T
    }

    fun consumeType(token: Token): Token {
        return consumeType(token, "Expected token to be of type $token")
    }

    fun consumeType(token: Token, errorMessage: String): Token {
        if (!peekIs(token))
            throw ParserError(errorMessage)
        return consume()
    }

    fun consumeIdentifier(): String {
        return consumeType<TokenIdentifier>().target
    }

    fun hasMore(): Boolean {
        return index < tokens.size
    }

}