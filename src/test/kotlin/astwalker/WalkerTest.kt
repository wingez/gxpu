package se.wingez.astwalker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.function
import se.wingez.ast.parserFromFile
import se.wingez.ast.parserFromLine
import kotlin.test.assertEquals
import kotlin.test.assertTrue


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
                  val a=5
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
                  val i = $i
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
                  val i = 3
                  while i!=6:
                    print(i)
                    i = i+1
            """.trimIndent()
            ).parseFunctionDefinition()

        val expected = listOf(3, 4, 5).map { it.toString() }

        assertEquals(expected, walk(function).result)
    }

    @Test
    fun testWhileMaxIterations() {
        val function = parserFromFile(
            """
            def main():
              val i=0
              while i!=10:
                i=i+1
             
        """.trimIndent()
        ).parse()
        assertDoesNotThrow { walk(function) }

        assertDoesNotThrow { walk(function, WalkConfig(maxLoopIterations = 50)) }
        assertThrows<WalkerException>("Max iterations exceeded") { walk(function, WalkConfig(maxLoopIterations = 5)) }
    }

    @Test
    fun testWhileBreak() {
        var function = parserFromFile(
            """
            def main():
              val i=0
              while i!=10:
                print(i)
                if i!=0:
                  break
                i=i+1
        """.trimIndent()
        ).parse()

        val expected = listOf(0, 1).map { it.toString() }
        assertEquals(expected, walk(function).result)

        function = parserFromFile(
            """
            def main():
              val i=0
              print(i)
              if i==0:
                break
              i=i+1
        """.trimIndent()
        ).parse()

        val msg = assertThrows<WalkerException> { walk(function) }
        assertTrue { msg.message!!.contains("No loop to break from") }

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
    fun testEditParametersDoesNotChangeCaller() {
        val nodes = parserFromFile(
            """
                def a(t:int):
                  t = t+1
                  
                def main():
                  val b = 1
                  a(b)
                  print(b)
                  
            """.trimIndent()
        ).parse()

        val expected = listOf(1).map { it.toString() }

        assertEquals(expected, walk(nodes).result)
    }

    @Test
    fun testWalkerReturn() {
        val nodes = parserFromFile(
            """
                def a(t:int):int
                  result = t+6
                  
                def main():
                  print(a(5))
            """.trimIndent()
        ).parse()

        val expected = listOf(11).map { it.toString() }

        assertEquals(expected, walk(nodes).result)


        val nodes2 = parserFromFile(
            """
                def mul(a:int,b:int):int
                  result = 0
                  while a!=0:
                    result = result + b
                    a= a-1
                  
                  
                def main():
                  print(mul(10,10))
                  print(mul(2,5))
            """.trimIndent()
        ).parse()

        val expected2 = listOf(100, 10).map { it.toString() }

        assertEquals(expected2, walk(nodes2).result)

    }

    @Test
    fun testPrintString() {
        var nodes = parserFromFile(
            """
                def main():
                  print("hello world!")
            """.trimIndent()
        ).parse()

        var expected = listOf("hello world!")
        assertEquals(expected, walk(nodes).result)


        nodes = parserFromFile(
            """
                def main():
                  val a = "hello world!"
                  
                  deref(a)[1] = 67
                  print(a)
                  
            """.trimIndent()
        ).parse()

        expected = listOf("hCllo world!")

        assertEquals(expected, walk(nodes).result)

    }

    @Test
    fun testFunctionEarlyReturn() {
        val nodes =
            parserFromFile(
                """
                def a(t:int):int
                  if t<1:
                    result = 10
                    return
                  if t<2:
                    result = 20
                    return
                  
                  result=30
                  return
                  
                def main():
                  print(a(0))
                  print(a(1))
                  print(a(2))
            """.trimIndent()
            ).parse()

        val expected = listOf(10, 20, 30).map { it.toString() }

        assertEquals(expected, walk(nodes).result)
    }
}