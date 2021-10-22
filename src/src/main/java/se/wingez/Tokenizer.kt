package se.wingez

import java.io.Reader

class Tokenizer {

    companion object {
        val TokenEOL = ExpressionSeparator()
        val TokenLeftParenthesis = ExpressionSeparator()
        val TokenRightParenthesis = ExpressionSeparator()
        val TokenComma = ExpressionSeparator()
        val TokenColon = ExpressionSeparator()
        val TokenEquals = Token()
        val TokenDot = Token()
        val TokenKeywordDef = Token()
        val TokenKeywordPrint = Token()
        val TokenKeywordWhile = Token()
        val TokenKeywordIf = Token()
        val TokenKeywordElse = Token()
        val TokenKeywordReturn = Token()
        val TokenKeywordStruct = Token()
        val TokenKeywordNew = Token()
        val TokenBeginBlock = Token()
        val TokenEndBlock = Token()
        val TokenPlusSign = TokenSingleOperation()
        val TokenMinusSign = TokenSingleOperation()
        val TokenGreaterSign = Token()
        private val DELIMITERS = listOf(" ", "#", "(", ")", ",", ":", "+", "-", "=", "<", ">", ".")
        private val TOKENS = listOf("(", ")", ",", ":", "=", "-", "+", "<", ">", ".")
    }


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

        run exitLoop@{

            line.forEach { symbol ->
                if (symbol.toString() in DELIMITERS) {
                    if (current.isNotEmpty()) {
                        result.add(toToken(current))
                    }
                    current = ""

                    if (symbol == ' ') {
                        // spaces only delimiters
                        return@forEach
                    }
                    if (symbol == '#') {
                        // Comment, ignore the rest of the line
                        return@exitLoop
                    }
                }
                current += symbol

                if (current in TOKENS) {
                    result.add(toToken(current))
                    current = ""
                }
            }
            if (current.isNotEmpty()) {
                // current could be empty if last symbol was an delimiter
                result.add(toToken(current))
            }
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
        if (text == "+")
            return TokenPlusSign
        if (text == "-")
            return TokenMinusSign
        if (text == ">")
            return TokenGreaterSign
        if (text == ".")
            return TokenDot

        if (text in TOKENS)
            throw TokenError("We should not reach this")

        if (isNumeric(text))
            return TokenNumericConstant(text.toInt())


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

    private fun isNumeric(str: String) = str.all { it in '0'..'9' }
}