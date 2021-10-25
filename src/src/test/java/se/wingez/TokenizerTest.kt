package se.wingez

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

internal class TokenizerTest {

    @Test
    fun testIndentation() {
        assertEquals(getIndentation("test"), Pair(0, "test"))
        assertEquals(getIndentation("\ttemp"), Pair(1, "temp"))
        assertEquals(getIndentation("rest\t"), Pair(0, "rest\t"))
        for (i in 0..9) {
            assertEquals(getIndentation("\t".repeat(i) + "test"), Pair(i, "test"))
            assertEquals(getIndentation("  ".repeat(i) + "test"), Pair(i, "test"))
        }

        assertThrows(TokenError::class.java) { getIndentation(" test") }
        assertThrows(TokenError::class.java) { getIndentation("  \ttemp") }
    }

    @Test
    fun test_indentation_token() {


        assertEquals(
            parseFile(
                StringReader(
                    """
        var
        print
        """
                )
            ), listOf(TokenIdentifier("var"), TokenEOL, TokenKeywordPrint, TokenEOL)
        )

        assertEquals(
            parseFile(
                StringReader(
                    """
            var
              print
            
            
        """
                )
            ), listOf(TokenIdentifier("var"), TokenEOL, TokenBeginBlock, TokenKeywordPrint, TokenEOL, TokenEndBlock)
        )

        assertEquals(
            parseFile(
                StringReader(
                    """
            var
              print
            5
            
        """
                )
            ), listOf(
                TokenIdentifier("var"), TokenEOL, TokenBeginBlock, TokenKeywordPrint,
                TokenEOL, TokenEndBlock, TokenNumericConstant(5), TokenEOL
            )
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
            parseFile(
                StringReader(
                    """
        print
          print
            print
  
        var         
        """
                )
            ), listOf(
                TokenKeywordPrint, TokenEOL,
                TokenBeginBlock, TokenKeywordPrint, TokenEOL,
                TokenBeginBlock, TokenKeywordPrint, TokenEOL,
                TokenEndBlock, TokenEndBlock,
                TokenIdentifier("var"), TokenEOL
            )
        )
    }

    @Test
    fun testToToken() {
        assertEquals(toToken("("), TokenLeftParenthesis)
        assertEquals(toToken(")"), TokenRightParenthesis)
        assertEquals(toToken("test"), TokenIdentifier("test"))
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
        assertEquals(parseLine("#"), listOf(TokenEOL))
        assertEquals(parseLine("test#"), listOf(TokenIdentifier("test"), TokenEOL))

        //Regular words
        assertEquals(parseLine("test"), tokenize("test"))
        assertEquals(parseLine("test hest"), tokenize("test", "hest"))

        //Function statement
        assertEquals(
            parseLine("def main(test: int, test2: bool, test3: str): "), tokenize(
                "def", "main", "(", "test", ":", "int", ",", "test2", ":", "bool", ",", "test3", ":", "str", ")", ":"
            )
        )
        assertEquals(parseLine("test3"), listOf(TokenIdentifier("test3"), TokenEOL))
        assertEquals(parseLine("456"), listOf(TokenNumericConstant(456), TokenEOL))


        assertEquals(parseLine("a:int = 5"), tokenize("a", ":", "int", "=", "5"))
        assertEquals(parseLine("a = 5+10"), tokenize("a", "=", "5", "+", "10"))
        assertEquals(parseLine("if a+b>10:"), tokenize("if", "a", "+", "b", ">", "10", ":"))
        // assert token.parse_line("if a>=10:") == ["if", "a", ">=", "10", ":"]

    }

    @Test
    fun testAddEnbBlockOnEOF() {
        val content = """
        test
          print
          
          
        """
        assertEquals(
            parseFile(StringReader(content)), listOf(
                TokenIdentifier("test"), TokenEOL,
                TokenBeginBlock, TokenKeywordPrint, TokenEOL,
                TokenEndBlock,
            )
        )
    }

    @Test
    fun testParseOperator() {
        assertIterableEquals(parseOperator("("), listOf(TokenLeftParenthesis))
        assertIterableEquals(parseOperator("()"), listOf(TokenLeftParenthesis, TokenRightParenthesis))
        assertIterableEquals(parseOperator(")+"), listOf(TokenRightParenthesis, TokenPlusSign))
        assertIterableEquals(parseOperator(")+"), listOf(TokenRightParenthesis, TokenPlusSign))
    }

    @Test
    fun testMultiLetterToken() {
        assertIterableEquals(parseLine("+ ="), listOf(TokenPlusSign, TokenAssign, TokenEOL))
        assertIterableEquals(parseLine("!="), listOf(TokenNotEqual, TokenEOL))

        assertIterableEquals(
            parseLine("if a-2!=0:"), listOf(
                TokenKeywordIf, TokenIdentifier("a"), TokenMinusSign, TokenNumericConstant(2),
                TokenNotEqual, TokenNumericConstant(0), TokenColon, TokenEOL
            )
        )

        assertIterableEquals(parseLine(")+"), listOf(TokenRightParenthesis, TokenPlusSign, TokenEOL))

    }


    @Test
    fun isNumeric() {
        assertEquals(isNumeric("567987"), true)
        assertEquals(isNumeric("a567987"), false)
        assertEquals(isNumeric("56!"), false)

        assertEquals(isAlNumeric("56a"), true)
        assertEquals(isAlNumeric("DFGZa"), true)
        assertEquals(isAlNumeric("!a"), false)
    }

}