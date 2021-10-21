package se.wingez

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.StringReader

internal class TokenizerTest {
    val t = Tokenizer()

    @Test
    fun testIndentation() {
        assertEquals(t.getIndentation("test"), Pair(0, "test"))
        assertEquals(t.getIndentation("\ttemp"), Pair(1, "temp"))
        assertEquals(t.getIndentation("rest\t"), Pair(0, "rest\t"))
        for (i in 0..9) {
            assertEquals(t.getIndentation("\t".repeat(i) + "test"), Pair(i, "test"))
            assertEquals(t.getIndentation("  ".repeat(i) + "test"), Pair(i, "test"))
        }

        assertThrows(TokenError::class.java) { t.getIndentation(" test") }
        assertThrows(TokenError::class.java) { t.getIndentation("  \ttemp") }
    }

    @Test
    fun test_indentation_token() {


        assertEquals(t.parseFile(StringReader("""
        var
        print
        """)), listOf(TokenIdentifier("var"), t.TokenEOL, t.TokenKeywordPrint, t.TokenEOL))

        assertEquals(t.parseFile(StringReader("""
            var
              print
            
            
        """)), listOf(TokenIdentifier("var"), t.TokenEOL, t.TokenBeginBlock, t.TokenKeywordPrint, t.TokenEOL, t.TokenEndBlock))

        assertEquals(t.parseFile(StringReader("""
            var
              print
            5
            
        """)), listOf(TokenIdentifier("var"), t.TokenEOL, t.TokenBeginBlock, t.TokenKeywordPrint,
                t.TokenEOL, t.TokenEndBlock, TokenNumericConstant(5), t.TokenEOL))

        assertThrows(TokenError::class.java) {
            t.parseFile(StringReader("""
           var
               print
        """))
        }

        assertEquals(t.parseFile(StringReader("""
        print
          print
            print
  
        var         
        """
        )), listOf(t.TokenKeywordPrint, t.TokenEOL,
                t.TokenBeginBlock, t.TokenKeywordPrint, t.TokenEOL,
                t.TokenBeginBlock, t.TokenKeywordPrint, t.TokenEOL,
                t.TokenEndBlock, t.TokenEndBlock,
                TokenIdentifier("var"), t.TokenEOL))
    }

    @Test
    fun testToToken() {
        assertEquals(t.toToken("("), t.TokenLeftParenthesis)
        assertEquals(t.toToken(")"), t.TokenRightParenthesis)
        assertEquals(t.toToken("test"), TokenIdentifier("test"))
    }

    fun tokenize(vararg items: String): List<Token> {
        val result = mutableListOf<Token>()
        for (item in items) {
            result.add(t.toToken(item))
        }
        result.add(t.TokenEOL)
        return result
    }

    @Test
    fun testParseLine() {
        //Comments
        assertEquals(t.parseLine("#"), listOf(t.TokenEOL))
        assertEquals(t.parseLine("test#"), listOf(TokenIdentifier("test"), t.TokenEOL))

        //Regular words
        assertEquals(t.parseLine("test"), tokenize("test"))
        assertEquals(t.parseLine("test hest"), tokenize("test", "hest"))

        //Function statement
        assertEquals(t.parseLine("def main(test: int, test2: bool, test3: str): "), tokenize(
                "def", "main", "(", "test", ":", "int", ",", "test2", ":", "bool", ",", "test3", ":", "str", ")", ":"
        ))
        assertEquals(t.parseLine("test3"), listOf(TokenIdentifier("test3"), t.TokenEOL))
        assertEquals(t.parseLine("456"), listOf(TokenNumericConstant(456), t.TokenEOL))


        assertEquals(t.parseLine("a:int = 5"), tokenize("a", ":", "int", "=", "5"))
        assertEquals(t.parseLine("a = 5+10"), tokenize("a", "=", "5", "+", "10"))
        assertEquals(t.parseLine("if a+b>10:"), tokenize("if", "a", "+", "b", ">", "10", ":"))
        // assert token.parse_line("if a>=10:") == ["if", "a", ">=", "10", ":"]

    }

    @Test
    fun testAddEnbBlockOnEOF() {
        val content = """
        test
          print
          
          
        """
        assertEquals(t.parseFile(StringReader(content)), listOf(
                TokenIdentifier("test"), t.TokenEOL,
                t.TokenBeginBlock, t.TokenKeywordPrint, t.TokenEOL,
                t.TokenEndBlock,
        ))
    }
}