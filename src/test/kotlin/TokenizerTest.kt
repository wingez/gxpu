import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tokens.*
import java.io.StringReader

fun tidentifier(name: String): Token {
    return token(TokenType.Identifier, name)
}

fun tconstant(value: Int): Token {
    return token(TokenType.NumericConstant, value.toString())
}

fun token(type: TokenType, additionalData: String = ""): Token {
    return Token(type, additionalData, SourceInfo.notApplicable)
}

fun assertEqualsWithoutLineNo(expected: Token, actual: Token) {
    assertEqualsWithoutLineNo(listOf(expected), listOf(actual))
}

fun assertEqualsWithoutLineNo(expected: Iterable<Token>, actual: Iterable<Token>) {
    assertEquals(
        expected,
        actual.map { it.copy(sourceInfo = SourceInfo.notApplicable) }
    )
}

fun tokenizeLines(lines: String): List<Token> {
    return parseFile(StringReader(lines), "dummyfile")
}

fun tokenizeLine(line: String): List<Token> {
    return parseLine(line, SourceInfo.notApplicable)
}

val TokenEOL = Token(TokenType.EOL, "", SourceInfo.notApplicable)
val TokenLeftParenthesis = Token(TokenType.LeftParenthesis, "", SourceInfo.notApplicable)
val TokenRightParenthesis = Token(TokenType.RightParenthesis, "", SourceInfo.notApplicable)
val TokenLeftBracket = Token(TokenType.LeftBracket, "", SourceInfo.notApplicable)
val TokenRightBracket = Token(TokenType.RightBracket, "", SourceInfo.notApplicable)
val TokenComma = Token(TokenType.Comma, "", SourceInfo.notApplicable)
val TokenColon = Token(TokenType.Colon, "", SourceInfo.notApplicable)
val TokenAssign = Token(TokenType.Equals, "", SourceInfo.notApplicable)
val TokenDot = Token(TokenType.Dot, "", SourceInfo.notApplicable)
val TokenRightArrow = Token(TokenType.RightArrow, "", SourceInfo.notApplicable)
val TokenKeywordDef = Token(TokenType.KeywordDef, "", SourceInfo.notApplicable)
val TokenKeywordWhile = Token(TokenType.KeywordWhile, "", SourceInfo.notApplicable)
val TokenKeywordIf = Token(TokenType.KeywordIf, "", SourceInfo.notApplicable)
val TokenKeywordElse = Token(TokenType.KeywordElse, "", SourceInfo.notApplicable)
val TokenKeywordReturn = Token(TokenType.KeywordReturn, "", SourceInfo.notApplicable)
val TokenKeywordStruct = Token(TokenType.KeywordStruct, "", SourceInfo.notApplicable)
val TokenKeywordBreak = Token(TokenType.KeywordBreak, "", SourceInfo.notApplicable)
val TokenKeywordVal = Token(TokenType.KeywordVal, "", SourceInfo.notApplicable)
val TokenBeginBlock = Token(TokenType.BeginBlock, "", SourceInfo.notApplicable)
val TokenEndBlock = Token(TokenType.EndBlock, "", SourceInfo.notApplicable)
val TokenPlusSign = Token(TokenType.PlusSign, "", SourceInfo.notApplicable)
val TokenMinusSign = Token(TokenType.MinusSign, "", SourceInfo.notApplicable)
val TokenGreaterSign = Token(TokenType.GreaterSign, "", SourceInfo.notApplicable)
val TokenLesserSign = Token(TokenType.LesserSign, "", SourceInfo.notApplicable)
val TokenNotEqual = Token(TokenType.NotEqual, "", SourceInfo.notApplicable)
val TokenDoubleEqual = Token(TokenType.DoubleEqual, "", SourceInfo.notApplicable)


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
            tokenizeLines(
                """
        var
        print
        """
            )
        )

        assertEqualsWithoutLineNo(
            listOf(
                tidentifier("var"), TokenEOL, TokenBeginBlock, tidentifier("print"),
                TokenEOL, TokenEndBlock
            ),
            tokenizeLines(
                """
            var
              print
        """
            )
        )

        assertEqualsWithoutLineNo(
            listOf(
                tidentifier("var"), TokenEOL, TokenBeginBlock, tidentifier("print"),
                TokenEOL, TokenEndBlock, tconstant(5), TokenEOL
            ),
            tokenizeLines(
                """
            var
              print
            5
            
        """
            )
        )

        assertThrows(TokenError::class.java) {
            tokenizeLines(
                """
           var
               print
        """
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
            tokenizeLines(
                """
        print
          print
            print
  
        var         
        """
            )
        )
    }

    @Test
    fun testToToken() {
        assertEqualsWithoutLineNo(TokenLeftParenthesis, toToken("(", SourceInfo.notApplicable)!!)
        assertEqualsWithoutLineNo(TokenRightParenthesis, toToken(")", SourceInfo.notApplicable)!!)
        assertEqualsWithoutLineNo(tidentifier("test"), toToken("test", SourceInfo.notApplicable)!!)
    }

    fun tokenize(vararg items: String): List<Token> {
        val result = mutableListOf<Token>()
        for (item in items) {
            result.add(toToken(item, SourceInfo.notApplicable)!!)
        }
        result.add(TokenEOL)
        return result
    }

    @Test
    fun testParseLine() {
        //Comments
        assertEqualsWithoutLineNo(listOf(TokenEOL), tokenizeLine("#"))
        assertEqualsWithoutLineNo(listOf(tidentifier("test"), TokenEOL), tokenizeLine("test#"))

        //Regular words
        assertEqualsWithoutLineNo(tokenize("test"), tokenizeLine("test"))
        assertEqualsWithoutLineNo(tokenize("test", "hest"), tokenizeLine("test hest"))

        //Function statement
        assertEqualsWithoutLineNo(
            tokenize(
                "def", "main", "(", "test", ":", "int", ",", "test2", ":", "bool", ",", "test3", ":", "str", ")", ":"
            ),
            tokenizeLine("def main(test: int, test2: bool, test3: str): "),
        )
        assertEqualsWithoutLineNo(listOf(tidentifier("test3"), TokenEOL), tokenizeLine("test3"))
        assertEqualsWithoutLineNo(listOf(tconstant(456), TokenEOL), tokenizeLine("456"))


        assertEqualsWithoutLineNo(tokenize("a", ":", "int", "=", "5"), tokenizeLine("a:int = 5"))
        assertEqualsWithoutLineNo(tokenize("a", "=", "5", "+", "10"), tokenizeLine("a = 5+10"))
        assertEqualsWithoutLineNo(tokenize("if", "a", "+", "b", ">", "10", ":"), tokenizeLine("if a+b>10:"))
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
            tokenizeLines(content),
        )
    }

    @Test
    fun testParseOperator() {
        assertEqualsWithoutLineNo(
            listOf(TokenLeftParenthesis),
            parseOperator("(", SourceInfo.notApplicable)
        )
        assertEqualsWithoutLineNo(
            listOf(TokenLeftParenthesis, TokenRightParenthesis),
            parseOperator("()", SourceInfo.notApplicable)
        )
        assertEqualsWithoutLineNo(
            listOf(TokenRightParenthesis, TokenPlusSign),
            parseOperator(")+", SourceInfo.notApplicable)
        )
        assertEqualsWithoutLineNo(
            listOf(TokenRightParenthesis, TokenPlusSign),
            parseOperator(")+", SourceInfo.notApplicable)
        )
    }

    @Test
    fun testMultiLetterToken() {
        assertEqualsWithoutLineNo(listOf(TokenPlusSign, TokenAssign, TokenEOL), tokenizeLine("+ ="))
        assertEqualsWithoutLineNo(listOf(TokenNotEqual, TokenEOL), tokenizeLine("!="))
        assertEqualsWithoutLineNo(listOf(TokenAssign, TokenDoubleEqual, TokenEOL), tokenizeLine("= =="))

        assertEqualsWithoutLineNo(
            listOf(
                TokenKeywordIf, tidentifier("a"), TokenMinusSign, tconstant(2),
                TokenNotEqual, tconstant(0), TokenColon, TokenEOL
            ),
            tokenizeLine("if a-2!=0:"),
        )

        assertEqualsWithoutLineNo(listOf(TokenRightParenthesis, TokenPlusSign, TokenEOL), tokenizeLine(")+"))

        assertEqualsWithoutLineNo(
            listOf(TokenRightParenthesis, TokenRightArrow, tidentifier("test"), TokenEOL),
            tokenizeLine(")->test"),
        )
    }

    @Test
    fun testStringTokens() {
        assertEqualsWithoutLineNo(
            listOf(TokenAssign, token(TokenType.String, "hello"), TokenRightParenthesis, TokenEOL),
            tokenizeLine("=\"hello\") ")
        )
        assertThrows(TokenError::class.java) {
            tokenizeLine("i\"")
        }
        assertEqualsWithoutLineNo(
            listOf(token(TokenType.String, "hello#"), TokenEOL),
            tokenizeLine("\"hello#\"#test")
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
        assertEqualsWithoutLineNo(listOf(tidentifier("hello_world"), TokenEOL), tokenizeLine("hello_world"))
    }

    @Test
    fun testLineNumber() {

        fun line(i: Int): SourceInfo = SourceInfo("testfile", i)

        assertEquals(
            listOf(Token(TokenType.Identifier, "hello", line(0)), Token(TokenType.EOL, "", line(0))),
            parseLine("hello", line(0))
        )
        assertEquals(
            listOf(Token(TokenType.Identifier, "hello", line(0)), Token(TokenType.EOL, "", line(0))),
            parseLine("hello ", line(0))
        )

        assertEquals(
            listOf(Token(TokenType.Identifier, "hello", line(0)), Token(TokenType.EOL, "", line(0))),
            parseLine(" hello ", line(0))
        )

        assertEquals(
            listOf(
                Token(TokenType.Identifier, "test", line(0)), Token(TokenType.Equals, "", line(0)),
                Token(TokenType.NumericConstant, "56", line(0)), Token(TokenType.LeftBracket, "", line(0)),
                Token(TokenType.EOL, "", line(0))
            ) + listOf(
                Token(TokenType.Identifier, "t", line(2)), Token(TokenType.NotEqual, "", line(2)),
                Token(TokenType.PlusSign, "", line(2)), Token(TokenType.NumericConstant, "5", line(2)),
                Token(TokenType.EOL, "", line(2)),
            ),
            parseFile(
                StringReader(
                    """
                test=56 [
                
                t!=+5
            """.trimIndent()
                )
            , "testfile")
        )

    }
}