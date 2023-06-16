package se.wingez.ast

import ast.expression.OperatorBuiltIns
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

    @Test
    fun testCommaList() {
        assertDoesNotThrow { parseExpression("call()") }
        assertDoesNotThrow { parseExpression("call(5)") }
        assertDoesNotThrow { parseExpression("call(5,6)") }
        assertThrows<ParserError> { parseExpression("call(,)") }
        assertThrows<ParserError> { parseExpression("call(5,,6)") }
    }

    @Test
    fun testTrailingComma() {
        assertDoesNotThrow { parseExpression("call(5,6,)") }

        assertEquals(
            parseExpression("call(5,6)"),
            parseExpression("call(5,6,)"),
        )
    }

    @Test
    fun testNewArray() {
        assertEquals(
            AstNode.newArray(emptyList()),
            parseExpression("[]")
        )
    }

    @Test
    fun testMixedParenthesisBrackets() {
        assertThrows<ParserError> { parseExpression("(") }
        assertThrows<ParserError> { parseExpression("((") }
        assertThrows<ParserError> { parseExpression("([)") }
        assertThrows<ParserError> { parseExpression("[4,(]") }
        assertThrows<ParserError> { parseExpression(",") }
    }

    @Test
    fun testInstanceFunction() {
        assertEquals(
            AstNode.fromCall("hello", FunctionType.Instance, listOf(constant(5), constant(6))),
            parseExpression("5.hello(6)"),
        )
    }

    @Test
    fun testArrayAccess() {
        assertEquals(
            AstNode.fromArrayAccess(identifier("a"), constant(5)),
            parseExpression("a[5]")
        )

        assertEquals(
            AstNode.fromArrayAccess(identifier("test"), constant(5)),
            parseExpression("test[5]")
        )

        assertEquals(
            AstNode.fromArrayAccess(
                AstNode.fromIdentifier("test"),
                AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), constant(5))
            ),
            parseExpression("test[5+5]")
        )
        assertThrows<ParserError> { parseExpression("a[b,c]")  }
        assertThrows<ParserError> { parseExpression("a[]")  }
    }
}

