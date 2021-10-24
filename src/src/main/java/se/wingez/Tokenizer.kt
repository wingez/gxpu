package se.wingez

import java.io.Reader


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
}


val TokenEOL = ExpressionSeparator(TokenType.EOL)
val TokenLeftParenthesis = ExpressionSeparator(TokenType.LeftParenthesis)
val TokenRightParenthesis = ExpressionSeparator(TokenType.RightParenthesis)
val TokenComma = ExpressionSeparator(TokenType.Comma)
val TokenColon = ExpressionSeparator(TokenType.Colon)
val TokenEquals = Token(TokenType.Equals)
val TokenDot = Token(TokenType.Dot)
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

fun parseLine(line: String): List<Token> {
    val result = mutableListOf<Token>()
    var current = ""


    var isParsingIdentifier = isAlNumeric(line[0].toString())
    var isParsingOperator = !isParsingIdentifier
    var isParsingNothing = true

    for (symbolIndex in line.withIndex()) {
        val symbol = symbolIndex.value
        var shouldDelimit = false
        val isComment = symbol == '#'
        val isSpace = symbol == ' '
        val isLast = symbolIndex.index == line.length - 1
        val isLetter = isAlNumeric(symbol.toString())

        if (isComment || isSpace) {
            // Comment, ignore the rest of the line
            shouldDelimit = true
        } else if (isParsingNothing) {
            isParsingIdentifier = isLetter
            isParsingOperator = !isLetter
            isParsingNothing = false
        }


        if (isParsingOperator && isLetter) {
            shouldDelimit = true
        }
        if (isParsingIdentifier && !isLetter) {
            shouldDelimit = true
        }
        if (isParsingOperator && symbol in ALWAYS_DELIMITER) {
            shouldDelimit = true
        }

        if (shouldDelimit) {
            if (current.isNotEmpty()) {
                result.add(toToken(current))
            }
            current = ""
            if (isComment)
                break
            if (isSpace) {
                isParsingOperator = false
                isParsingIdentifier = false
                isParsingNothing = true
                continue
            }
            if (isParsingOperator && isLetter) {
                isParsingOperator = false
                isParsingIdentifier = true
            } else if (isParsingIdentifier && !isLetter) {
                isParsingOperator = true
                isParsingIdentifier = false
            } else {
//                throw AssertionError("We should not reach this")
            }
        }
        current += symbol


    }


    if (current.isNotEmpty()) {
        // current could be empty if last symbol was an delimiter
        result.add(toToken(current))
    }

    result.add(TokenEOL)
    return result
}

fun toToken(text: String): Token {
    // sanity check
    if (' ' in text)
        throw TokenError("text contains spaces")

    if (text == "(")
        return TokenLeftParenthesis
    if (text == ")")
        return TokenRightParenthesis
    if (text == ":")
        return TokenColon
    if (text == ",")
        return TokenComma
    if (text == "=")
        return TokenEquals
    if (text == "!=")
        return TokenNotEqual
    if (text == "+")
        return TokenPlusSign
    if (text == "-")
        return TokenMinusSign
    if (text == ">")
        return TokenGreaterSign
    if (text == ".")
        return TokenDot

    if (isNumeric(text))
        return TokenNumericConstant(text.toInt())

    if (!isAlNumeric(text))
        throw TokenError("Invalid operator $text")


    if (text == "def")
        return TokenKeywordDef
    if (text == "print")
        return TokenKeywordPrint
    if (text == "while")
        return TokenKeywordWhile
    if (text == "if")
        return TokenKeywordIf
    if (text == "else")
        return TokenKeywordElse
    if (text == "return")
        return TokenKeywordReturn
    if (text == "struct")
        return TokenKeywordStruct
    if (text == "new")
        return TokenKeywordNew

    return TokenIdentifier(text)
}

fun isNumeric(str: String) = str.all { it in '0'..'9' }
fun isAlNumeric(str: String) = str.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }