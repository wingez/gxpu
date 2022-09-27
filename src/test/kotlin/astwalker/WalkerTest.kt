package se.wingez.astwalker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.function
import se.wingez.ast.parserFromFile
import se.wingez.ast.parserFromLine
import kotlin.test.assertEquals


internal class WalkerTest {


    @Test
    fun testWalker() {

        val function = function(
            "main", emptyList(),
            parserFromLine("print(5)").parseExpression(),
            "returntype"
        )

        val output = walk(function)

        assertEquals(listOf("5"), output.result)

    }

    @Test
    fun testWalkerAddition() {

        val function = function(
            "main", emptyList(),
            parserFromLine("print(5+5)").parseExpression(),
            "returntype"
        )

        val output = walk(function)

        assertEquals(listOf("10"), output.result)

    }

    @Test
    fun testWalkerAssign() {
        val function =
            parserFromFile(
                """
                def main():
                  a=5
                  print(a)
            """.trimIndent()
            ).parseFunctionDefinition()
        val output = walk(function)

        assertEquals(listOf("5"), output.result)
        assertThrows<WalkerException> {
            walk(parserFromFile("""
                def main():
                  print(a)
            """.trimIndent()).parseFunctionDefinition())
        }

    }


}