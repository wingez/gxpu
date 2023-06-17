package se.wingez

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import se.wingez.tokens.*
import java.io.StringReader

fun tidentifier(name: String): Token {
    return token(TokenType.Identifier, name)
}

fun tconstant(value: Int): Token {
    return token(TokenType.NumericConstant, value.toString())
}

fun token(type: TokenType, additionalData: String = ""): Token {
    return Token(type, additionalData, -1, -1)
}

fun assertEqualsWithoutLineNo(expected: Token, actual: Token) {
    assertEqualsWithoutLineNo(listOf(expected), listOf(actual))
}

fun assertEqualsWithoutLineNo(expected: Iterable<Token>, actual: Iterable<Token>) {
    assertEquals(
        expected,
        actual.zip(expected).map { (act, exp) -> act.copy(lineRow = exp.lineRow, lineCol = exp.lineCol) }
    )
}

val TokenEOL = Token(TokenType.EOL, "", 0, 0)
val TokenLeftParenthesis = Token(TokenType.LeftParenthesis, "", 0, 0)
val TokenRightParenthesis = Token(TokenType.RightParenthesis, "", 0, 0)
val TokenLeftBracket = Token(TokenType.LeftBracket, "", 0, 0)
val TokenRightBracket = Token(TokenType.RightBracket, "", 0, 0)
val TokenComma = Token(TokenType.Comma, "", 0, 0)
val TokenColon = Token(TokenType.Colon, "", 0, 0)
val TokenAssign = Token(TokenType.Equals, "", 0, 0)
val TokenDot = Token(TokenType.Dot, "", 0, 0)
val TokenDeref = Token(TokenType.Deref, "", 0, 0)
val TokenKeywordDef = Token(TokenType.KeywordDef, "", 0, 0)
val TokenKeywordWhile = Token(TokenType.KeywordWhile, "", 0, 0)
val TokenKeywordIf = Token(TokenType.KeywordIf, "", 0, 0)
val TokenKeywordElse = Token(TokenType.KeywordElse, "", 0, 0)
val TokenKeywordReturn = Token(TokenType.KeywordReturn, "", 0, 0)
val TokenKeywordStruct = Token(TokenType.KeywordStruct, "", 0, 0)
val TokenKeywordBreak = Token(TokenType.KeywordBreak, "", 0, 0)
val TokenKeywordVal = Token(TokenType.KeywordVal, "", 0, 0)
val TokenBeginBlock = Token(TokenType.BeginBlock, "", 0, 0)
val TokenEndBlock = Token(TokenType.EndBlock, "", 0, 0)
val TokenPlusSign = Token(TokenType.PlusSign, "", 0, 0)
val TokenMinusSign = Token(TokenType.MinusSign, "", 0, 0)
val TokenGreaterSign = Token(TokenType.GreaterSign, "", 0, 0)
val TokenLesserSign = Token(TokenType.LesserSign, "", 0, 0)
val TokenNotEqual = Token(TokenType.NotEqual, "", 0, 0)
val TokenDoubleEqual = Token(TokenType.DoubleEqual, "", 0, 0)


internal class TokenizerTest {

    @Test
    fun testIndentation() {
        assertEquals(IndentationResult(0, 0, "test"), getIndentation("test"))
        assertEquals(IndentationResult(0, 0, "test  "), getIndentation("test  "))
        for (i in 0..9) {
            assertEquals(IndentationResult(i, i * 2, "test"), getIndentation("  ".repeat(i) + "test"))
        }

        assertThrows(TokenError::class.java) { getIndentation(" test") }
        assertThrows(TokenError::class.java) { getIndentation("  \ttemp") }
        assertThrows(TokenError::class.java) { getIndentation("\ttemp") }
    }

    @Test
    fun test_indentation_token() {
        assertEqualsWithoutLineNo(
            listOf(tidentifier("var"), TokenEOL, tidentifier("print"), TokenEOL),
            parseFile(
                StringReader(
                    """
        var
        print
        """
                )
            ),
        )

        assertEqualsWithoutLineNo(
            listOf(
                tidentifier("var"), TokenEOL, TokenBeginBlock, tidentifier("print"),
                TokenEOL, TokenEndBlock
            ),
            parseFile(
                StringReader(
                    """
            var
              print
        """
                )
            ),
        )

        assertEqualsWithoutLineNo(
            listOf(
                tidentifier("var"), TokenEOL, TokenBeginBlock, tidentifier("print"),
                TokenEOL, TokenEndBlock, tconstant(5), TokenEOL
            ),
            parseFile(
                StringReader(
                    """
            var
              print
            5
            
        """
                )
            ),
        )

        assertThrows(TokenError::class.java) {
            parseFile(
                StringReader(
                    """
           var
               print
        """
                )
            )
        }

        assertEqualsWithoutLineNo(
            listOf(
                tidentifier("print"), TokenEOL,
                TokenBeginBlock, tidentifier("print"), TokenEOL,
                TokenBeginBlock, tidentifier("print"), TokenEOL,
                TokenEndBlock, TokenEndBlock,
                tidentifier("var"), TokenEOL
            ),
            parseFile(
                StringReader(
                    """
        print
          print
            print
  
        var         
        """
                )
            ),
        )
    }

    @Test
    fun testToToken() {
        assertEqualsWithoutLineNo(TokenLeftParenthesis, toToken("(")!!)
        assertEqualsWithoutLineNo(TokenRightParenthesis, toToken(")")!!)
        assertEqualsWithoutLineNo(tidentifier("test"), toToken("test")!!)
    }

    fun tokenize(vararg items: String): List<Token> {
        val result = mutableListOf<Token>()
        for (item in items) {
            result.add(toToken(item)!!)
        }
        result.add(TokenEOL)
        return result
    }

    @Test
    fun testParseLine() {
        //Comments
        assertEqualsWithoutLineNo(listOf(TokenEOL), parseLine("#"))
        assertEqualsWithoutLineNo(listOf(tidentifier("test"), TokenEOL), parseLine("test#"))

        //Regular words
        assertEqualsWithoutLineNo(tokenize("test"), parseLine("test"))
        assertEqualsWithoutLineNo(tokenize("test", "hest"), parseLine("test hest"))

        //Function statement
        assertEqualsWithoutLineNo(
            tokenize(
                "def", "main", "(", "test", ":", "int", ",", "test2", ":", "bool", ",", "test3", ":", "str", ")", ":"
            ),
            parseLine("def main(test: int, test2: bool, test3: str): "),
        )
        assertEqualsWithoutLineNo(listOf(tidentifier("test3"), TokenEOL), parseLine("test3"))
        assertEqualsWithoutLineNo(listOf(tconstant(456), TokenEOL), parseLine("456"))


        assertEqualsWithoutLineNo(tokenize("a", ":", "int", "=", "5"), parseLine("a:int = 5"))
        assertEqualsWithoutLineNo(tokenize("a", "=", "5", "+", "10"), parseLine("a = 5+10"))
        assertEqualsWithoutLineNo(tokenize("if", "a", "+", "b", ">", "10", ":"), parseLine("if a+b>10:"))
        // assert token.parse_line("if a>=10:") == ["if", "a", ">=", "10", ":"]

    }

    @Test
    fun testAddEnbBlockOnEOF() {
        val content = """
        test
          print
          
          
        """
        assertEqualsWithoutLineNo(
            listOf(
                tidentifier("test"), TokenEOL,
                TokenBeginBlock, tidentifier("print"), TokenEOL,
                TokenEndBlock,
            ),
            parseFile(StringReader(content)),
        )
    }

    @Test
    fun testParseOperator() {
        assertEqualsWithoutLineNo(listOf(TokenLeftParenthesis), parseOperator("(", 0))
        assertEqualsWithoutLineNo(listOf(TokenLeftParenthesis, TokenRightParenthesis), parseOperator("()", 0))
        assertEqualsWithoutLineNo(listOf(TokenRightParenthesis, TokenPlusSign), parseOperator(")+", 0))
        assertEqualsWithoutLineNo(listOf(TokenRightParenthesis, TokenPlusSign), parseOperator(")+", 0))
    }

    @Test
    fun testMultiLetterToken() {
        assertEqualsWithoutLineNo(listOf(TokenPlusSign, TokenAssign, TokenEOL), parseLine("+ ="))
        assertEqualsWithoutLineNo(listOf(TokenNotEqual, TokenEOL), parseLine("!="))
        assertEqualsWithoutLineNo(listOf(TokenAssign, TokenDoubleEqual, TokenEOL), parseLine("= =="))

        assertEqualsWithoutLineNo(
            listOf(
                TokenKeywordIf, tidentifier("a"), TokenMinusSign, tconstant(2),
                TokenNotEqual, tconstant(0), TokenColon, TokenEOL
            ),
            parseLine("if a-2!=0:"),
        )

        assertEqualsWithoutLineNo(listOf(TokenRightParenthesis, TokenPlusSign, TokenEOL), parseLine(")+"))

        assertEqualsWithoutLineNo(
            listOf(TokenRightParenthesis, TokenDeref, tidentifier("test"), TokenEOL),
            parseLine(")->test"),
        )
    }

    @Test
    fun testStringTokens() {
        assertEqualsWithoutLineNo(
            listOf(TokenAssign, token(TokenType.String, "hello"), TokenRightParenthesis, TokenEOL),
            parseLine("=\"hello\") ")
        )
        assertThrows(TokenError::class.java) {
            parseLine("i\"")
        }
        assertEqualsWithoutLineNo(
            listOf(token(TokenType.String, "hello#"), TokenEOL),
            parseLine("\"hello#\"#test")
        )
    }


    @Test
    fun isNumeric() {
        assertEquals(true, isNumeric("567987"))
        assertEquals(false, isNumeric("a567987"))
        assertEquals(false, isNumeric("56!"))

        assertEquals(true, isAlNumeric("56a"))
        assertEquals(true, isAlNumeric("DFGZa"))
        assertEquals(false, isAlNumeric("!a"))
        assertEquals(true, isAlNumeric("sdf_a"))
    }

    @Test
    fun testUnderscoreIdentifier() {
        assertEqualsWithoutLineNo(listOf(tidentifier("hello_world"), TokenEOL), parseLine("hello_world"))
    }

    @Test
    fun testLineNumber() {

        assertEquals(
            listOf(Token(TokenType.Identifier, "hello", -1, 0), Token(TokenType.EOL, "", -1, 5)),
            parseLine("hello")
        )
        assertEquals(
            listOf(Token(TokenType.Identifier, "hello", -1, 0), Token(TokenType.EOL, "", -1, 6)),
            parseLine("hello ")
        )

        assertEquals(
            listOf(Token(TokenType.Identifier, "hello", -1, 1), Token(TokenType.EOL, "", -1, 7)),
            parseLine(" hello ")
        )

        assertEquals(
            listOf(
                Token(TokenType.Identifier, "test", 0, 0), Token(TokenType.Equals, "", 0, 4),
                Token(TokenType.NumericConstant, "56", 0, 5), Token(TokenType.LeftBracket, "", 0, 8),
                Token(TokenType.EOL, "", 0, 9)
            ) + listOf(
                Token(TokenType.Identifier, "t", 2, 0), Token(TokenType.NotEqual, "", 2, 1),
                Token(TokenType.PlusSign, "", 2, 3), Token(TokenType.NumericConstant, "5", 2, 4),
                Token(TokenType.EOL, "", 2, 5),
            ),
            parseFile(
                StringReader(
                    """
                test=56 [
                
                t!=+5
            """.trimIndent()
                )
            )
        )

    }
}