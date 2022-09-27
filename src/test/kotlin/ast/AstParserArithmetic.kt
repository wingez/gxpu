package se.wingez.ast

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import se.wingez.tokens.TokenNotEqual
import se.wingez.tokens.TokenPlusSign

class AstParserArithmetic {
    @Test
    fun testBasic() {
        assertEquals(
            parserFromFile("5+10").parseExpressionUntilSeparator(),
            AstNode.fromOperation(TokenPlusSign, constant(5), constant(10))
        )
    }


    @Test
    fun testWithIdentifier() {
        assertEquals(
            parserFromFile("2+test").parseExpressionUntilSeparator(),
            AstNode.fromOperation(TokenPlusSign, constant(2), identifier("test"))
        )
    }

    @Test
    fun notEqual() {
        assertEquals(
            parserFromFile("2!=0").parseExpressionUntilSeparator(),
            AstNode.fromOperation(TokenNotEqual, constant(2), constant(0))
        )
    }

    @Test
    fun testParenthesis() {
        assertEquals(
            parserFromLine("(5)").parseExpressionUntilSeparator(),
            constant(5)
        )
        assertEquals(
            parserFromLine("(5+var)").parseExpressionUntilSeparator(),
            AstNode.fromOperation(TokenPlusSign, constant(5), identifier("var"))
        )

        assertThat(
            assertThrows<ParserError> { parserFromLine("(5+10").parseExpressionUntilSeparator() })
            .hasMessageContaining("Mismatched parenthesis")

        assertDoesNotThrow { parserFromLine("(5+10)+(3+3)").parseExpressionUntilSeparator() }

    }

    @Test
    fun testTriple() {
        assertEquals(
            parserFromFile("(5+5)+10").parseExpressionUntilSeparator(),

            AstNode.fromOperation(
                TokenPlusSign,
                AstNode.fromOperation(TokenPlusSign, constant(5), constant(5)),
                constant(10)
            )
        )
    }

    @Test
    fun testAssociation() {
        assertEquals(
            AstNode.fromOperation(
                TokenPlusSign,
                AstNode.fromOperation(TokenPlusSign, constant(5), constant(6)),
                constant(7)
            ),
            value("5+6+7")
        )
        assertEquals(
            AstNode.fromOperation(
                TokenNotEqual,
                AstNode.fromOperation(TokenPlusSign, constant(5), constant(6)),
                AstNode.fromOperation(TokenPlusSign, constant(7), constant(8)),
            ),
            value("5+6!=7+8")
        )
    }


}