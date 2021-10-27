package se.wingez.tokens

import java.io.Reader

class TokenError(message: String) : Exception(message)

open class Token(
    val type: TokenType,
) {
    override fun toString(): String {
        return "Token type:$type"
    }
}

open class ExpressionSeparator(type: TokenType) : Token(type)
open class TokenSingleOperation(type: TokenType) : Token(type)

data class TokenIdentifier(val target: String) : Token(TokenType.Identifier)
data class TokenNumericConstant(val value: Int) : Token(TokenType.NumericConstant)

enum class TokenType {
    EOL,
    Identifier,
    NumericConstant,
    LeftParenthesis,
    RightParenthesis,
    Comma,
    Colon,
    Equals,
    Dot,
    KeywordDef,
    KeywordPrint,
    KeywordWhile,
    KeywordIf,
    KeywordElse,
    KeywordReturn,
    KeywordStruct,
    KeywordNew,
    BeginBlock,
    EndBlock,
    PlusSign,
    MinusSign,
    GreaterSign,
    NotEqual,
    Deref,
}

val TokenEOL = ExpressionSeparator(TokenType.EOL)
val TokenLeftParenthesis = ExpressionSeparator(TokenType.LeftParenthesis)
val TokenRightParenthesis = ExpressionSeparator(TokenType.RightParenthesis)
val TokenComma = ExpressionSeparator(TokenType.Comma)
val TokenColon = ExpressionSeparator(TokenType.Colon)
val TokenAssign = ExpressionSeparator(TokenType.Equals)
val TokenDot = TokenSingleOperation(TokenType.Dot)
val TokenDeref = TokenSingleOperation(TokenType.Deref)
val TokenKeywordDef = Token(TokenType.KeywordDef)
val TokenKeywordPrint = Token(TokenType.KeywordPrint)
val TokenKeywordWhile = Token(TokenType.KeywordWhile)
val TokenKeywordIf = Token(TokenType.KeywordIf)
val TokenKeywordElse = Token(TokenType.KeywordElse)
val TokenKeywordReturn = Token(TokenType.KeywordReturn)
val TokenKeywordStruct = Token(TokenType.KeywordStruct)
val TokenKeywordNew = Token(TokenType.KeywordNew)
val TokenBeginBlock = Token(TokenType.BeginBlock)
val TokenEndBlock = Token(TokenType.EndBlock)
val TokenPlusSign = TokenSingleOperation(TokenType.PlusSign)
val TokenMinusSign = TokenSingleOperation(TokenType.MinusSign)
val TokenGreaterSign = Token(TokenType.GreaterSign)
val TokenNotEqual = TokenSingleOperation(TokenType.NotEqual)

private val ALWAYS_DELIMITER = listOf('(', ')', ',', ':')

fun isNumeric(str: String) = str.all { it in '0'..'9' }
fun isAlNumeric(str: String) = str.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }

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
                throw  TokenError("Cannot mix tabs and spaces")
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

private enum class TokenParserState {
    Nothing,
    Operator,
    Identifier,
}

fun parseLine(line: String): List<Token> {
    val result = mutableListOf<Token>()
    var current = ""

    var state = TokenParserState.Nothing

    for (symbol in line) {
        val isComment = symbol == '#'
        val isSpace = symbol == ' '
        val isLetter = isAlNumeric(symbol.toString())

        if (state == TokenParserState.Nothing && !isComment && !isSpace) {
            state = when (isLetter) {
                true -> TokenParserState.Identifier
                false -> TokenParserState.Operator
            }
        }
        val shouldBreak = when {
            isComment -> true
            isSpace -> true
            state == TokenParserState.Operator && isLetter -> true
            state == TokenParserState.Identifier && !isLetter -> true
            state == TokenParserState.Operator && symbol in ALWAYS_DELIMITER -> true
            else -> false
        }

        if (shouldBreak) {
            if (current.isNotEmpty()) {
                result.addAll(parseOperator(current))
                current = ""
            }
            if (isComment)
            // Comment, ignore the rest of the line
                break
            if (isSpace) {
                state = TokenParserState.Nothing
                continue
            }
            state = when (isLetter) {
                true -> TokenParserState.Identifier
                false -> TokenParserState.Operator
            }
        }
        current += symbol
    }

    if (current.isNotEmpty()) {
        // current could be empty if last symbol was a delimiter
        result.addAll(parseOperator(current))
    }

    result.add(TokenEOL)
    return result
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
        return TokenNumericConstant(text.toInt())

    when (text) {
        "(" -> TokenLeftParenthesis
        ")" -> TokenRightParenthesis
        ":" -> TokenColon
        "," -> TokenComma
        "=" -> TokenAssign
        "!=" -> TokenNotEqual
        "+" -> TokenPlusSign
        "-" -> TokenMinusSign
        ">" -> TokenGreaterSign
        "." -> TokenDot
        "->" -> TokenDeref
        else -> null
    }?.also { return it }

    //We should now only have identifiers and keywords left
    if (!isAlNumeric(text))
        throw TokenError("Invalid operator $text")

    return when (text) {
        "def" -> TokenKeywordDef
        "print" -> TokenKeywordPrint
        "while" -> TokenKeywordWhile
        "if" -> TokenKeywordIf
        "else" -> TokenKeywordElse
        "return" -> TokenKeywordReturn
        "struct" -> TokenKeywordStruct
        "new" -> TokenKeywordNew
        else -> TokenIdentifier(text)
    }
}
