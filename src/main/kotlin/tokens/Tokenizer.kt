package se.wingez.tokens

import se.wingez.SupportTypePeekIterator
import java.io.Reader

class TokenError(message: String) : Exception(message)

enum class TokenType {
    EOL,
    Identifier,
    NumericConstant,
    LeftParenthesis,
    RightParenthesis,
    LeftBracket,
    RightBracket,
    Comma,
    Colon,
    Equals,
    Dot,
    KeywordDef,
    KeywordWhile,
    KeywordIf,
    KeywordElse,
    KeywordReturn,
    KeywordStruct,
    KeywordNew,
    KeywordVal,
    BeginBlock,
    EndBlock,
    PlusSign,
    MinusSign,
    GreaterSign,
    LesserSign,
    NotEqual,
    DoubleEqual,
    Deref,
    String,
    Break,
}

data class Token(
    override val type: TokenType,
    val additionalData: String
) : SupportTypePeekIterator<TokenType> {
    override fun toString(): String {
        return "Token type:$type"
    }

    fun asConstant(): Int {
        assert(type == TokenType.NumericConstant)
        return additionalData.toInt()
    }

    fun isExpressionSeparator(): Boolean {
        return type in listOf(
            TokenType.EOL,
            TokenType.LeftParenthesis,
            TokenType.RightParenthesis,
            TokenType.RightBracket,
            TokenType.Comma,
            TokenType.Colon,
            TokenType.Equals
        )
    }

    fun isSingleOperation(): Boolean {
        return type in listOf(
            TokenType.Dot,
            TokenType.PlusSign,
            TokenType.MinusSign,
            TokenType.NotEqual,
            TokenType.DoubleEqual,
        )
    }
}

val TokenEOL = Token(TokenType.EOL, "")
val TokenLeftParenthesis = Token(TokenType.LeftParenthesis, "")
val TokenRightParenthesis = Token(TokenType.RightParenthesis, "")
val TokenLeftBracket = Token(TokenType.LeftBracket, "")
val TokenRightBracket = Token(TokenType.RightBracket, "")
val TokenComma = Token(TokenType.Comma, "")
val TokenColon = Token(TokenType.Colon, "")
val TokenAssign = Token(TokenType.Equals, "")
val TokenDot = Token(TokenType.Dot, "")
val TokenDeref = Token(TokenType.Deref, "")
val TokenKeywordDef = Token(TokenType.KeywordDef, "")
val TokenKeywordWhile = Token(TokenType.KeywordWhile, "")
val TokenKeywordIf = Token(TokenType.KeywordIf, "")
val TokenKeywordElse = Token(TokenType.KeywordElse, "")
val TokenKeywordReturn = Token(TokenType.KeywordReturn, "")
val TokenKeywordStruct = Token(TokenType.KeywordStruct, "")
val TokenKeywordNew = Token(TokenType.KeywordNew, "")
val TokenKeywordBreak = Token(TokenType.Break, "")
val TokenKeywordVal = Token(TokenType.KeywordVal, "")
val TokenBeginBlock = Token(TokenType.BeginBlock, "")
val TokenEndBlock = Token(TokenType.EndBlock, "")
val TokenPlusSign = Token(TokenType.PlusSign, "")
val TokenMinusSign = Token(TokenType.MinusSign, "")
val TokenGreaterSign = Token(TokenType.GreaterSign, "")
val TokenLesserSign = Token(TokenType.LesserSign, "")
val TokenNotEqual = Token(TokenType.NotEqual, "")
val TokenDoubleEqual = Token(TokenType.DoubleEqual, "")

private val ALWAYS_DELIMITER = listOf('(', ')', ',', ':', '"')

fun isNumeric(str: String) = str.all { it in '0'..'9' }
fun isAlNumeric(str: String) = str.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it == '_' }

fun getIndentation(line: String): Pair<Int, String> {
    var indentation = 0
    var hasTabs = false
    var hasSpaces = false
    var current = line

    while (true) {
        var indentLetters = 0

        if (current.startsWith("\t")) {
            if (hasSpaces)
                throw TokenError("Cannot mix tabs and spaces")
            hasTabs = true
            indentLetters = 1
        }
        if (current.startsWith("  ")) {
            if (hasTabs)
                throw TokenError("Cannot mix tabs and spaces")
            hasSpaces = true
            indentLetters = 2
        }

        if (current[0] == ' ' && current[1] != ' ')
            throw TokenError("Mismatched spaces")

        if (indentLetters > 0) {
            indentation++
            current = current.substring(indentLetters)
        } else {
            return Pair(indentation, current)
        }
    }
}

fun parseFile(input: Reader): List<Token> {

    val result = mutableListOf<Token>()
    var currentIndentation = -1
    var baseIndentation = 0


    input.forEachLine {
        val lineToParse = it.trim('\n')
        if (lineToParse.trim(' ', '\t').isEmpty())
            return@forEachLine

        val (indentation, line) = getIndentation(lineToParse)
        if (currentIndentation == -1) {
            baseIndentation = indentation
        } else {
            if (indentation > currentIndentation) {
                if (indentation != currentIndentation + 1)
                    throw TokenError("Cannot increment indentation by more than one step")
                result.add(TokenBeginBlock)
            } else if (indentation < currentIndentation) {
                for (i in indentation until currentIndentation) {
                    result.add(TokenEndBlock)
                }
            }
        }
        currentIndentation = indentation

        result.addAll(parseLine(line))
    }

    if (currentIndentation != -1) {
        for (i in 0 until (currentIndentation - baseIndentation)) {
            result.add(TokenEndBlock)
        }
    }

    return result
}

fun parseLine(line: String): List<Token> {
    val feeder = OneSymbolAtATime(line)

    val result = mutableListOf<Token>()

    while (feeder.hasMore()) {
        val symbolPeek = feeder.peek()

        val isComment = symbolPeek == '#'
        if (isComment) {
            break
        }

        val isSpace = symbolPeek == ' '
        if (isSpace) {
            feeder.next()
            continue
        }

        val isLetter = isAlNumeric(symbolPeek.toString())
        if (isLetter) {
            result.add(consumeConstant(feeder))
            continue
        }

        val isString = symbolPeek == '"'
        if (isString) {
            result.add(consumeString(feeder))
            continue
        }
        result.addAll(consumeOperator(feeder))

    }

    result.add(TokenEOL)
    return result
}

private class OneSymbolAtATime(
    private val string: String
) {
    private var currentIndex = 0

    fun hasMore(): Boolean {
        return currentIndex < string.length
    }

    fun peek(): Char {
        return string[currentIndex]
    }

    fun next(): Char {
        return string[currentIndex++]
    }
}

private fun consumeString(feeder: OneSymbolAtATime): Token {

    assert(feeder.next() == '"')

    var current = ""
    while (feeder.hasMore()) {
        val symbol = feeder.next()
        if (symbol == '"') {
            return Token(TokenType.String, current)
        }
        current += symbol
    }
    throw TokenError("Got end of line while parsing string")
}

private fun consumeConstant(feeder: OneSymbolAtATime): Token {

    var current = ""
    while (feeder.hasMore()) {
        val symbolPeek = feeder.peek()
        if (isAlNumeric(symbolPeek.toString())) {
            current += symbolPeek
            feeder.next()
        } else {
            break
        }
    }

    return toToken(current)
}

private fun consumeOperator(feeder: OneSymbolAtATime): Iterable<Token> {

    var current = feeder.next().toString()

    while (feeder.hasMore()) {
        val symbolPeek = feeder.peek()
        if (symbolPeek == ' ') {
            break
        }
        if (symbolPeek == '#') {
            break
        }
        if (symbolPeek in ALWAYS_DELIMITER) {
            break
        }
        if (isAlNumeric(symbolPeek.toString())) {
            break
        }

        current += symbolPeek
        feeder.next()
    }

    return parseOperator(current)
}

fun parseOperator(line: String): Iterable<Token> {

    for (length in line.length downTo 1) {
        val toParse = line.substring(0, length)
        val validToken = try {
            toToken(toParse)
        } catch (e: TokenError) {
            continue
        }
        val rest = line.substring(length, line.length)
        if (rest.isEmpty()) {
            return listOf(validToken)
        }
        return listOf(validToken) + parseOperator(rest)
    }
    throw TokenError("Cannot parse $line to operator")
}

fun toToken(text: String): Token {
    // sanity check
    if (' ' in text)
        throw TokenError("text contains spaces")
    if (text.isEmpty())
        throw TokenError("text is empty")

    if (isNumeric(text))
        return Token(TokenType.NumericConstant, text)

    when (text) {
        "(" -> TokenLeftParenthesis
        ")" -> TokenRightParenthesis
        "[" -> TokenLeftBracket
        "]" -> TokenRightBracket
        ":" -> TokenColon
        "," -> TokenComma
        "=" -> TokenAssign
        "!=" -> TokenNotEqual
        "==" -> TokenDoubleEqual
        "+" -> TokenPlusSign
        "-" -> TokenMinusSign
        ">" -> TokenGreaterSign
        "<" -> TokenLesserSign
        "." -> TokenDot
        "->" -> TokenDeref
        else -> null
    }?.also { return it }

    //We should now only have identifiers and keywords left
    if (!isAlNumeric(text))
        throw TokenError("Invalid operator $text")

    return when (text) {
        "def" -> TokenKeywordDef
        "while" -> TokenKeywordWhile
        "if" -> TokenKeywordIf
        "else" -> TokenKeywordElse
        "return" -> TokenKeywordReturn
        "struct" -> TokenKeywordStruct
        "new" -> TokenKeywordNew
        "break" -> TokenKeywordBreak
        "val" -> TokenKeywordVal
        else -> Token(TokenType.Identifier, text)
    }
}
