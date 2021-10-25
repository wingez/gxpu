package se.wingez.ast

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows


class AstParserArithmetic {
    @Test
    fun testBasic() {
        assertEquals(
            parserFromFile("5+10").parseValueProvider(),
            SingleOperationNode(Operation.Addition, ConstantNode(5), ConstantNode(10))
        )
    }


    @Test
    fun testWithIdentifier() {
        assertEquals(
            parserFromFile("2+test").parseValueProvider(),
            SingleOperationNode(Operation.Addition, ConstantNode(2), MemberAccess("test"))
        )
    }

    @Test
    fun notEqual() {
        assertEquals(
            parserFromFile("2!=0").parseValueProvider(),
            SingleOperationNode(Operation.NotEquals, ConstantNode(2), ConstantNode(0))
        )
    }

    @Test
    fun testParenthesis() {
        assertEquals(
            parserFromLine("(5)").parseValueProvider(),
            ConstantNode(5)
        )
        assertEquals(
            parserFromLine("(5+var)").parseValueProvider(),
            SingleOperationNode(Operation.Addition, ConstantNode(5), MemberAccess("var"))
        )

        assertThat(
            assertThrows<ParserError> { parserFromLine("(5+10").parseValueProvider() })
            .hasMessageContaining("Mismatched parenthesis")

        assertThat(
            assertThrows<ParserError> { parserFromLine("5+10+3").parseValueProvider() })
            .hasMessageContaining("too complex")

        assertDoesNotThrow { parserFromLine("(5+10)+(3+3)").parseValueProvider() }

        assertThat(
            assertThrows<ParserError> { parserFromLine("5+10+3").parseValueProvider() })
            .hasMessageContaining("too complex")
    }

    @Test
    fun testTriple() {
        assertThrows<ParserError> {
            parserFromFile("5+5+10").parseValueProvider()
        }

        assertEquals(
            parserFromFile("(5+5)+10").parseValueProvider(),

            SingleOperationNode(
                Operation.Addition,
                SingleOperationNode(Operation.Addition, ConstantNode(5), ConstantNode(5)),
                ConstantNode(10)
            )
        )
    }

}