package se.wingez.ast

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import se.wingez.tokens.TokenType

class AstParserExpressions {

    @Test
    fun testSingleToken() {
        assertEquals(
            AstNode.fromConstant(5),
            parseExpression("5"),
        )
        assertEquals(
            AstNode.fromIdentifier("hej"),
            parseExpression("hej"),
        )
    }

    @Test
    fun testBasic() {
        assertEquals(
            AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), constant(10)),
            parseExpression("5+10"),
        )
    }


    @Test
    fun testWithIdentifier() {
        assertEquals(
            AstNode.fromBinaryOperation(TokenType.PlusSign, constant(2), identifier("test")),
            parseExpression("2+test"),
        )
    }

    @Test
    fun notEqual() {
        assertEquals(
            AstNode.fromBinaryOperation(TokenType.NotEqual, constant(2), constant(0)),
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
            AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), identifier("var")),
            parseExpression("(5+var)"),
        )

        assertThat(
            assertThrows<ParserError> { parseExpression("(5+10") })
            .hasMessageContaining("Mismatched parenthesis")

        assertDoesNotThrow { parseExpression("(5+10)+(3+3)") }

    }

    @Test
    fun testTriple() {
        assertEquals(
            AstNode.fromBinaryOperation(
                TokenType.PlusSign,
                AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), constant(5)),
                constant(10)
            ),
            parseExpression("(5+5)+10"),
        )
    }

    @Test
    fun testAssociation() {
        assertEquals(
            AstNode.fromBinaryOperation(
                TokenType.PlusSign,
                AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), constant(6)),
                constant(7),
            ),
            parseExpression("5+6+7")
        )
        assertEquals(
            AstNode.fromBinaryOperation(
                TokenType.NotEqual,
                AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), constant(6)),
                AstNode.fromBinaryOperation(TokenType.PlusSign, constant(7), constant(8)),
            ),
            parseExpression("5+6!=7+8")
        )
    }

    @Test
    fun testCallNoArgs() {
        assertEquals(
            AstNode.fromCall("hello", FunctionType.Normal, listOf()),
            parseExpression("hello()")
        )
    }

    @Test
    fun testCallWithArgs() {
        assertEquals(
            AstNode.fromCall("hello", FunctionType.Normal, listOf(constant(5), constant(6))),
            parseExpression("hello(5,6)")
        )
    }

    @Test
    fun testMoreBlocks() {
        assertEquals(
            AstNode.fromCall(
                OperatorBuiltIns.NotEqual, FunctionType.Operator, listOf(
                    AstNode.fromCall(
                        OperatorBuiltIns.Subtraction, FunctionType.Operator, listOf(
                            constant(10), identifier("counter")
                        )
                    ),
                    constant(0)
                )
            ),
            parseExpression("(10-counter)!=0")
        )
    }

}
