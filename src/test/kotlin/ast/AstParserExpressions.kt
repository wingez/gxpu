package ast

import ast.expression.OperatorBuiltIns
import ast.syntaxerror.ParserSyntaxError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import tokens.TokenType
import kotlin.test.assertNotEquals

class AstParserExpressions {

    @Test
    fun testSingleToken() {
        assertEquals(
            AstNode.fromConstant(5, na),
            parseExpression("5"),
        )
        assertEquals(
            AstNode.fromIdentifier("hej", na),
            parseExpression("hej"),
        )
    }

    @Test
    fun testBasic() {
        assertEquals(
            AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), constant(10), na),
            parseExpression("5+10"),
        )
    }


    @Test
    fun testWithIdentifier() {
        assertEquals(
            AstNode.fromBinaryOperation(TokenType.PlusSign, constant(2), identifier("test"), na),
            parseExpression("2+test"),
        )
    }

    @Test
    fun notEqual() {
        assertEquals(
            AstNode.fromBinaryOperation(TokenType.NotEqual, constant(2), constant(0), na),
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
            AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), identifier("var"), na),
            parseExpression("(5+var)"),
        )

        assertThat(
            assertThrows<ParserSyntaxError> { parseExpression("(5+10") })
            .hasMessageContaining("Block not closed")

        assertDoesNotThrow { parseExpression("(5+10)+(3+3)") }

    }

    @Test
    fun testTriple() {
        assertEquals(
            AstNode.fromBinaryOperation(
                TokenType.PlusSign,
                AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), constant(5), na),
                constant(10), na
            ),
            parseExpression("(5+5)+10"),
        )
    }

    @Test
    fun testAssociation() {
        assertEquals(
            AstNode.fromBinaryOperation(
                TokenType.PlusSign,
                AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), constant(6), na),
                constant(7),
                na
            ),
            parseExpression("5+6+7")
        )
        assertEquals(
            AstNode.fromBinaryOperation(
                TokenType.NotEqual,
                AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), constant(6), na),
                AstNode.fromBinaryOperation(TokenType.PlusSign, constant(7), constant(8), na),
                na
            ),
            parseExpression("5+6!=7+8")
        )
    }

    @Test
    fun testCallNoArgs() {
        assertEquals(
            AstNode.fromCall("hello", FunctionType.Normal, listOf(), na),
            parseExpression("hello()")
        )
    }

    @Test
    fun testCallWithArgs() {
        assertEquals(
            AstNode.fromCall("hello", FunctionType.Normal, listOf(constant(5), constant(6)), na),
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
                        ),
                        na
                    ),
                    constant(0)
                ),
                na
            ),
            parseExpression("(10-counter)!=0")
        )
    }

    @Test
    fun testCommaList() {
        assertDoesNotThrow { parseExpression("call()") }
        assertDoesNotThrow { parseExpression("call(5)") }
        assertDoesNotThrow { parseExpression("call(5,6)") }
        assertThrows<ParserSyntaxError> { parseExpression("call(,)") }
        assertThrows<ParserSyntaxError> { parseExpression("call(5,,6)") }
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
            AstNode.newArray(emptyList(), na),
            parseExpression("[]")
        )
    }

    @Test
    fun testMixedParenthesisBrackets() {
        assertThrows<ParserSyntaxError> { parseExpression("(") }
        assertThrows<ParserSyntaxError> { parseExpression("((") }
        assertThrows<ParserSyntaxError> { parseExpression("([)") }
        assertThrows<ParserSyntaxError> { parseExpression("[4,(]") }
        assertThrows<ParserSyntaxError> { parseExpression(",") }
    }

    @Test
    fun testInstanceFunction() {
        assertEquals(
            AstNode.fromCall("hello", FunctionType.Instance, listOf(constant(5), constant(6)), na),
            parseExpression("5.hello(6)"),
        )
    }

    @Test
    fun testArrayAccess() {
        assertEquals(
            AstNode.fromArrayAccess(identifier("a"), constant(5), na),
            parseExpression("a[5]")
        )

        assertEquals(
            AstNode.fromArrayAccess(identifier("test"), constant(5), na),
            parseExpression("test[5]")
        )

        assertEquals(
            AstNode.fromArrayAccess(
                identifier("test"),
                AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), constant(5), na),
                na
            ),
            parseExpression("test[5+5]")
        )
        assertThrows<ParserSyntaxError> { parseExpression("a[b,c]") }
        assertThrows<ParserSyntaxError> { parseExpression("a[]") }
    }

    @Test
    fun testNegate() {
        assertEquals(
            AstNode.fromCall(OperatorBuiltIns.Negate, FunctionType.Operator, listOf(constant(5)), na),
            parseExpression("-5")
        )

        assertEquals(
            parseExpression("(-1)+2"),
            parseExpression("-1+2")
        )
    }

    @Test
    fun testAddressOf() {
        assertEquals(
            AstNode.fromAddressOf(constant(5), na),
            parseExpression("&5")
        )

        assertEquals(
            parseExpression("&5+4"),
            parseExpression("(&5)+4"),
        )
        assertNotEquals(
            parseExpression("&(5+4)"),
            parseExpression("&5+4"),
        )
    }

    @Test
    fun testDeref() {
        assertEquals(
            AstNode.fromDeref(constant(5), na),
            parseExpression("*5")
        )
    }

    @Test
    fun testLambda(){
        assertEquals(
            AstNode.fromLambda(listOf(constant(5)), na),
            parseExpression("{5}")
        )
    }

}

