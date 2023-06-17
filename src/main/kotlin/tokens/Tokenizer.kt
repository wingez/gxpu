package se.wingez.tokens

import se.wingez.PeekIterator
import se.wingez.SupportTypePeekIterator
import java.io.Reader

class TokenError(message: String) : Exception(message)

private val rowColNotSet = -1

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
    Star,
    KeywordDef,
    KeywordWhile,
    KeywordIf,
    KeywordElse,
    KeywordReturn,
    KeywordStruct,
    KeywordVal,
    KeywordBreak,
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
    Ampersand,
}

data class Token(
    override val type: TokenType,
    val additionalData: String,
    val lineRow: Int,
    val lineCol: Int,
) : SupportTypePeekIterator<TokenType> {

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

private val ALWAYS_DELIMITER = listOf('(', ')', ',', ':', '"')

fun isNumeric(str: String) = str.all { it in '0'..'9' }
fun isAlNumeric(str: String) = str.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it == '_' }

data class IndentationResult(
    val indentationLevel: Int,
    val lineStartsAtIndex: Int,
    val line: String,
)

fun getIndentation(line: String): IndentationResult {
    var indentation = 0
    var current = line

    val indentLetters = "  ".length

    while (true) {
        if (current.startsWith("\t")) {
            throw TokenError("Tabs are not allowed to indent a line with")
        }
        if (current.startsWith("  ")) {
            indentation++
            current = current.substring(indentLetters)
        } else {
            if (current[0] == ' ' && current[1] != ' ')
                throw TokenError("Mismatched spaces")

            return IndentationResult(indentation, indentation * indentLetters, current)
        }
    }
}

fun parseFile(input: Reader): List<Token> {

    val result = mutableListOf<Token>()
    var currentIndentation = -1
    var baseIndentation = 0


    val lines = mutableListOf<String>()
    input.forEachLine { lines.add(it) }

    for ((lineIndex, lineRaw) in lines.withIndex()) {
        val lineToParse = lineRaw.trim('\n')
        if (lineToParse.trim(' ', '\t').isEmpty()) {
            continue
        }

        // Parse line assuming it starts on row 0 and has 0 indentation
        // Then add the correct offset to all resulting tokens when we're done parsing the line

        val (indentation, lineStartAt, line) = getIndentation(lineToParse)
        if (currentIndentation == -1) {
            baseIndentation = indentation
        } else {
            if (indentation > currentIndentation) {
                if (indentation != currentIndentation + 1)
                    throw TokenError("Cannot increment indentation by more than one step")
                result.add(Token(TokenType.BeginBlock, "", rowColNotSet, 0))
            } else if (indentation < currentIndentation) {
                for (i in indentation until currentIndentation) {
                    result.add(Token(TokenType.EndBlock, "", rowColNotSet, 0))
                }
            }
        }
        currentIndentation = indentation

        // Add line + indentation offsets
        result.addAll(parseLine(line)
            .map { Token(it.type, it.additionalData, lineIndex, it.lineCol + lineStartAt) })
    }

    if (currentIndentation != -1) {
        for (i in 0 until (currentIndentation - baseIndentation)) {
            result.add(Token(TokenType.EndBlock, "", lines.size - 1, (lines.last()).length - 1))
        }
    }

    return result
}

fun parseLine(line: String): List<Token> {
    val feeder = PeekIterator(line.toList())

    val result = mutableListOf<Token>()

    while (feeder.hasMore()) {
        val symbolPeek = feeder.peek()

        val isComment = symbolPeek == '#'
        if (isComment) {
            break
        }

        val isSpace = symbolPeek == ' '
        if (isSpace) {
            feeder.consume()
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
    if (result.isNotEmpty() && result.any { it.lineCol == rowColNotSet }) {
        throw TokenError("")
    }

    result.add(Token(TokenType.EOL, "", rowColNotSet, feeder.getCurrentIndex()))
    return result
}


private fun consumeString(feeder: PeekIterator<Char>): Token {

    val startIndex = feeder.getCurrentIndex()

    assert(feeder.consume() == '"')

    var current = ""
    while (feeder.hasMore()) {
        val symbol = feeder.consume()
        if (symbol == '"') {
            return Token(TokenType.String, current, rowColNotSet, startIndex)
        }
        current += symbol
    }
    throw TokenError("Got end of line while parsing string")
}

private fun consumeConstant(feeder: PeekIterator<Char>): Token {

    var current = ""
    val startIndex = feeder.getCurrentIndex()
    while (feeder.hasMore()) {
        val symbolPeek = feeder.peek()
        if (isAlNumeric(symbolPeek.toString())) {
            current += symbolPeek
            feeder.consume()
        } else {
            break
        }
    }

    val token = toToken(current)
        ?: throw TokenError("Something")
    return token.copy(lineCol = startIndex)
}

private fun consumeOperator(feeder: PeekIterator<Char>): Iterable<Token> {

    val startIndex = feeder.getCurrentIndex()
    var current = feeder.consume().toString()

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
        feeder.consume()
    }

    return parseOperator(current, startIndex)
}

fun parseOperator(line: String, lineIndex: Int): Iterable<Token> {

    for (length in line.length downTo 1) {
        val toParse = line.substring(0, length)
        val maybeValidToken = toToken(toParse)
            ?: continue

        val validToken = maybeValidToken.copy(lineCol = lineIndex)

        val rest = line.substring(length, line.length)
        if (rest.isEmpty()) {
            return listOf(validToken)
        }
        return listOf(validToken) + parseOperator(rest, lineIndex + length)
    }
    throw TokenError("Cannot parse $line to operator")
}

val operatorsToType = mapOf(
    "(" to TokenType.LeftParenthesis,
    ")" to TokenType.RightParenthesis,
    "[" to TokenType.LeftBracket,
    "]" to TokenType.RightBracket,
    ":" to TokenType.Colon,
    "," to TokenType.Comma,
    "=" to TokenType.Equals,
    "!=" to TokenType.NotEqual,
    "==" to TokenType.DoubleEqual,
    "+" to TokenType.PlusSign,
    "-" to TokenType.MinusSign,
    ">" to TokenType.GreaterSign,
    "<" to TokenType.LesserSign,
    "." to TokenType.Dot,
    "->" to TokenType.Deref,
    "*" to TokenType.Star,
    "&" to TokenType.Ampersand,
)

val keywords = mapOf(
    "def" to TokenType.KeywordDef,
    "while" to TokenType.KeywordWhile,
    "if" to TokenType.KeywordIf,
    "else" to TokenType.KeywordElse,
    "return" to TokenType.KeywordReturn,
    "struct" to TokenType.KeywordStruct,
    "break" to TokenType.KeywordBreak,
    "val" to TokenType.KeywordVal,
)

fun toToken(text: String): Token? {
    // sanity check
    if (' ' in text)
        throw TokenError("text contains spaces")

    if (text.isEmpty()) {
        return null
    }

    if (isNumeric(text))
        return Token(TokenType.NumericConstant, text, rowColNotSet, rowColNotSet)

    if (text in operatorsToType) {
        return Token(operatorsToType.getValue(text), "", rowColNotSet, rowColNotSet)
    }

    //We should now only have identifiers and keywords left
    if (!isAlNumeric(text))
        return null

    if (text in keywords) {
        return Token(keywords.getValue(text), "", rowColNotSet, rowColNotSet)
    }

    return Token(TokenType.Identifier, text, rowColNotSet, rowColNotSet)
}
