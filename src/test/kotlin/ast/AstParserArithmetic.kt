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
            AstNode.fromOperation(TokenPlusSign, constant(5), constant(10)),
            parseExpression("5+10"),
        )
    }


    @Test
    fun testWithIdentifier() {
        assertEquals(
            AstNode.fromOperation(TokenPlusSign, constant(2), identifier("test")),
            parseExpression("2+test"),
        )
    }

    @Test
    fun notEqual() {
        assertEquals(
            AstNode.fromOperation(TokenNotEqual, constant(2), constant(0)),
            parseExpression("2!=0"),
        )
    }

    @Test
    fun testParenthesis() {
        assertEquals(
            constant(5),
            parseExpression("(5)"),
        )
        assertEquals(
            AstNode.fromOperation(TokenPlusSign, constant(5), identifier("var")),
            parseExpression("(5+var)"),
        )

        assertThat(
            assertThrows<Error> { parseExpression("(5+10") })
            .hasMessageContaining("Mismatched parenthesis")

        assertDoesNotThrow { parseExpression("(5+10)+(3+3)") }

    }

    @Test
    fun testTriple() {
        assertEquals(
            AstNode.fromOperation(
                TokenPlusSign,
                AstNode.fromOperation(TokenPlusSign, constant(5), constant(5)),
                constant(10)
            ),
            parseExpression("(5+5)+10"),
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
            parseExpression("5+6+7")
        )
        assertEquals(
            AstNode.fromOperation(
                TokenNotEqual,
                AstNode.fromOperation(TokenPlusSign, constant(5), constant(6)),
                AstNode.fromOperation(TokenPlusSign, constant(7), constant(8)),
            ),
            parseExpression("5+6!=7+8")
        )
    }
}