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
            "void"
        )

        val output = walk(function)

        assertEquals(listOf("5"), output.result)

    }

    @Test
    fun testWalkerAddition() {

        val function = function(
            "main", emptyList(),
            parserFromLine("print(5+5)").parseExpression(),
            "void"
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
        assertEquals(listOf("5"), walk(function).result)

        assertThrows<WalkerException> {
            walk(
                parserFromFile(
                    """
                def main():
                  print(a)
            """.trimIndent()
                ).parseFunctionDefinition()
            )
        }

    }

    @Test
    fun testWalkerIf() {

        for (i in 0..10) {
            val function =
                parserFromFile(
                    """
                def main():
                  i = $i
                  if (i!=5):
                    print(20)
                  else:
                    print($i)
            """.trimIndent()
                ).parseFunctionDefinition()

            val expected = if (i == 5) i.toString() else "20"

            assertEquals(listOf(expected), walk(function).result)
        }
    }

    @Test
    fun testWalkerWhile() {
        val function =
            parserFromFile(
                """
                def main():
                  i = 3
                  while i!=6:
                    print(i)
                    i = i+1
            """.trimIndent()
            ).parseFunctionDefinition()

        val expected = listOf(3, 4, 5).map { it.toString() }

        assertEquals(expected, walk(function).result)
    }

    @Test
    fun testWalkerCallOtherFunctions() {
        val nodes =
            parserFromFile(
                """
                def callMe():
                  print(10)
                    
                def main():
                  callMe()
                  callMe()
            """.trimIndent()
            ).parse()

        val expected = listOf(10, 10).map { it.toString() }

        assertEquals(expected, walk(nodes).result)
    }

    @Test
    fun testWalkerParameters() {
        val nodes =
            parserFromFile(
                """
                def a(t:int):
                  print(t)
                  
                def b(t:int,u:int):
                  print(t+u)
                  
                def main():
                  a(5)
                  b(6,7)
            """.trimIndent()
            ).parse()

        val expected = listOf(5, 13).map { it.toString() }

        assertEquals(expected, walk(nodes).result)
    }

    @Test
    fun testWalkerReturn() {

    }


}