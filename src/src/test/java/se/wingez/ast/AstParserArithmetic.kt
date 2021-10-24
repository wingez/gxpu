package se.wingez.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.AstParserTest.Companion.parserFromFile

class AstParserArithmetic {
    @Test
    fun testBasic() {
        assertEquals(
            parserFromFile("5+10").parseValueProvider(),
            SingleOperationNode(Operation.Plus, ConstantNode(5), ConstantNode(10))
        )
    }

    @Test
    fun testTooComplex() {
        assertThrows<ParserError> {
            parserFromFile("5+5+10").parseValueProvider()
        }
    }

    @Test
    fun testWithIdentifier() {
        assertEquals(
            parserFromFile("2+test").parseValueProvider(),
            SingleOperationNode(Operation.Plus, ConstantNode(2), MemberAccess("test"))
        )
    }

}