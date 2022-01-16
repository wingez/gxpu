package se.wingez.ast

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

fun value(text: String): AstNode {
    return parserFromFile(text).parseValueProvider()
}

fun identifier(name: String): AstNode {
    return AstNode.fromIdentifier(name)
}

class AstParserArithmetic {
    @Test
    fun testBasic() {
        assertEquals(
            parserFromFile("5+10").parseValueProvider(),
            AstNode.fromOperation(NodeTypes.Addition, ConstantNode(5), ConstantNode(10))
        )
    }


    @Test
    fun testWithIdentifier() {
        assertEquals(
            parserFromFile("2+test").parseValueProvider(),
            AstNode.fromOperation(NodeTypes.Addition, ConstantNode(2), identifier("test"))
        )
    }

    @Test
    fun notEqual() {
        assertEquals(
            parserFromFile("2!=0").parseValueProvider(),
            AstNode.fromOperation(NodeTypes.NotEquals, ConstantNode(2), ConstantNode(0))
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
            AstNode.fromOperation(NodeTypes.Addition, ConstantNode(5), identifier("var"))
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
                AstNode.fromOperation(NodeTypes.Addition, ConstantNode(5), ConstantNode(5)),
                ConstantNode(10)
            )
        )
    }

    @Test
    fun testAssociation() {
        assertEquals(
            AstNode.fromOperation(
                NodeTypes.Addition,
                AstNode.fromOperation(NodeTypes.Addition, ConstantNode(5), ConstantNode(6)),
                ConstantNode(7)
            ),
            value("5+6+7")
        )
        assertEquals(
            AstNode.fromOperation(
                NodeTypes.NotEquals,
                AstNode.fromOperation(NodeTypes.Addition, ConstantNode(5), ConstantNode(6)),
                AstNode.fromOperation(NodeTypes.Addition, ConstantNode(7), ConstantNode(8)),
            ),
            value("5+6!=7+8")
        )
    }


}