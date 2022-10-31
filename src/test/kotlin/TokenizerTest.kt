package se.wingez

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import se.wingez.tokens.*
import java.io.StringReader

fun tidentifier(name: String): Token {
    return Token(TokenType.Identifier, name)
}

fun tconstant(value: Int): Token {
    return Token(TokenType.NumericConstant, value.toString())
}

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
        assertEquals(
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

        assertEquals(
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

        assertEquals(
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

        assertEquals(
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
        assertEquals(TokenLeftParenthesis, toToken("("))
        assertEquals(TokenRightParenthesis, toToken(")"))
        assertEquals(tidentifier("test"), toToken("test"))
    }

    fun tokenize(vararg items: String): List<Token> {
        val result = mutableListOf<Token>()
        for (item in items) {
            result.add(toToken(item))
        }
        result.add(TokenEOL)
        return result
    }

    @Test
    fun testParseLine() {
        //Comments
        assertEquals(listOf(TokenEOL), parseLine("#"))
        assertEquals(listOf(tidentifier("test"), TokenEOL), parseLine("test#"))

        //Regular words
        assertEquals(tokenize("test"), parseLine("test"))
        assertEquals(tokenize("test", "hest"), parseLine("test hest"))

        //Function statement
        assertEquals(
            tokenize(
                "def", "main", "(", "test", ":", "int", ",", "test2", ":", "bool", ",", "test3", ":", "str", ")", ":"
            ),
            parseLine("def main(test: int, test2: bool, test3: str): "),
        )
        assertEquals(listOf(tidentifier("test3"), TokenEOL), parseLine("test3"))
        assertEquals(listOf(tconstant(456), TokenEOL), parseLine("456"))


        assertEquals(tokenize("a", ":", "int", "=", "5"), parseLine("a:int = 5"))
        assertEquals(tokenize("a", "=", "5", "+", "10"), parseLine("a = 5+10"))
        assertEquals(tokenize("if", "a", "+", "b", ">", "10", ":"), parseLine("if a+b>10:"))
        // assert token.parse_line("if a>=10:") == ["if", "a", ">=", "10", ":"]

    }

    @Test
    fun testAddEnbBlockOnEOF() {
        val content = """
        test
          print
          
          
        """
        assertEquals(
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
        assertIterableEquals(listOf(TokenLeftParenthesis), parseOperator("("))
        assertIterableEquals(listOf(TokenLeftParenthesis, TokenRightParenthesis), parseOperator("()"))
        assertIterableEquals(listOf(TokenRightParenthesis, TokenPlusSign), parseOperator(")+"))
        assertIterableEquals(listOf(TokenRightParenthesis, TokenPlusSign), parseOperator(")+"))
    }

    @Test
    fun testMultiLetterToken() {
        assertIterableEquals(listOf(TokenPlusSign, TokenAssign, TokenEOL), parseLine("+ ="))
        assertIterableEquals(listOf(TokenNotEqual, TokenEOL), parseLine("!="))
        assertEquals(listOf(TokenAssign, TokenDoubleEqual, TokenEOL), parseLine("= =="))

        assertIterableEquals(
            listOf(
                TokenKeywordIf, tidentifier("a"), TokenMinusSign, tconstant(2),
                TokenNotEqual, tconstant(0), TokenColon, TokenEOL
            ),
            parseLine("if a-2!=0:"),
        )

        assertIterableEquals(listOf(TokenRightParenthesis, TokenPlusSign, TokenEOL), parseLine(")+"))

        assertIterableEquals(
            listOf(TokenRightParenthesis, TokenDeref, tidentifier("test"), TokenEOL),
            parseLine(")->test"),
        )
    }

    @Test
    fun testStringTokens() {
        assertEquals(
            listOf(TokenAssign, Token(TokenType.String, "hello"), TokenRightParenthesis, TokenEOL),
            parseLine("=\"hello\") ")
        )
        assertThrows(TokenError::class.java) {
            parseLine("i\"")
        }
        assertEquals(
            listOf(Token(TokenType.String, "hello#"), TokenEOL),
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
        assertEquals(parseLine("hello_world"), listOf(tidentifier("hello_world"), TokenEOL))
    }
}