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
            AstNode.fromOperation(NodeTypes.Addition, constant(5), constant(10))
        )
    }


    @Test
    fun testWithIdentifier() {
        assertEquals(
            parserFromFile("2+test").parseValueProvider(),
            AstNode.fromOperation(NodeTypes.Addition, constant(2), identifier("test"))
        )
    }

    @Test
    fun notEqual() {
        assertEquals(
            parserFromFile("2!=0").parseValueProvider(),
            AstNode.fromOperation(NodeTypes.NotEquals, constant(2), constant(0))
        )
    }

    @Test
    fun testParenthesis() {
        assertEquals(
            parserFromLine("(5)").parseValueProvider(),
            constant(5)
        )
        assertEquals(
            parserFromLine("(5+var)").parseValueProvider(),
            AstNode.fromOperation(NodeTypes.Addition, constant(5), identifier("var"))
        )

        assertThat(
            assertThrows<ParserError> { parserFromLine("(5+10").parseValueProvider() })
            .hasMessageContaining("Mismatched parenthesis")

        assertDoesNotThrow { parserFromLine("(5+10)+(3+3)").parseValueProvider() }

    }

    @Test
    fun testTriple() {
        assertEquals(
            parserFromFile("(5+5)+10").parseValueProvider(),

            AstNode.fromOperation(
                NodeTypes.Addition,
                AstNode.fromOperation(NodeTypes.Addition, constant(5), constant(5)),
                constant(10)
            )
        )
    }

    @Test
    fun testAssociation() {
        assertEquals(
            AstNode.fromOperation(
                NodeTypes.Addition,
                AstNode.fromOperation(NodeTypes.Addition, constant(5), constant(6)),
                constant(7)
            ),
            value("5+6+7")
        )
        assertEquals(
            AstNode.fromOperation(
                NodeTypes.NotEquals,
                AstNode.fromOperation(NodeTypes.Addition, constant(5), constant(6)),
                AstNode.fromOperation(NodeTypes.Addition, constant(7), constant(8)),
            ),
            value("5+6!=7+8")
        )
    }


}