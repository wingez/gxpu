package compiler.backendwalker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import compiler.backends.astwalker.WalkerException
import compiler.features.matchString
import compiler.frontend.FrontendCompilerError
import kotlin.test.assertEquals
import kotlin.test.assertTrue


internal class WalkerTest {


    @Test
    fun testWalker() {
        val program = """
            def main():
              print(5)
        """.trimIndent()

        assertEquals(listOf(5), run(program))

    }

    @Test
    fun testWalkerAddition() {

        val function = """
            def main():
              print(5+5)
        """.trimIndent()

        assertEquals(listOf(10), run(function))

    }

    @Test
    fun testWalkerAssign() {
        val function = """
                def main():
                  val a:int=5
                  print(a)
            """
        assertEquals(listOf(5), run(function))

        assertThrows<FrontendCompilerError> {
            run(
                """
                def main():
                  print(a)
            """
            )
        }

    }

    @Test
    fun testWalkerIf() {

        for (i in 0..10) {
            val function =
                """
                def main():
                  val i = $i
                  if (i!=5):
                    print(20)
                  else:
                    print($i)
            """
            val expected = if (i == 5) i else 20

            assertEquals(listOf(expected), run(function))
        }
    }

    @Test
    fun testWalkerWhile() {
        val function = """
                def main():
                  val i = 3
                  while i!=6:
                    print(i)
                    i = i+1
            """

        val expected = listOf(3, 4, 5)

        assertEquals(expected, run(function))
    }

    @Test
    fun testWhileMaxIterations() {
        val function = """
            def main():
              val i=0
              while i!=10:
                i=i+1
             
        """.trimIndent()
        assertDoesNotThrow { run(function) }

        assertDoesNotThrow { run(function, maxLoopIterations = 50) }
        assertThrows<WalkerException>("Max iterations exceeded") { run(function, maxLoopIterations = 20) }
    }

    @Test
    fun testWhileBreak() {
        var function = """
            def main():
              val i=0
              while i!=10:
                print(i)
                if i!=0:
                  break
                i=i+1
        """.trimIndent()

        val expected = listOf(0, 1)
        assertEquals(expected, run(function))

        function = """
            def main():
              val i=0
              print(i)
              if i==0:
                break
              i=i+1
        """.trimIndent()

        val msg = assertThrows<FrontendCompilerError> { run(function) }
        assertTrue { msg.message!!.contains("No loop to break from") }
    }

    @Test
    fun testWalkerCallOtherFunctions() {
        val program =
            """
                def callMe():
                  print(10)
                    
                def main():
                  callMe()
                  callMe()
            """.trimIndent()

        val expected = listOf(10, 10)

        assertEquals(expected, run(program))
    }

    @Test
    fun testWalkerParameters() {
        val program =
            """
                def a(t:int):
                  print(t)
                  
                def b(t:int,u:int):
                  print(t+u)
                  
                def main():
                  a(5)
                  b(6,7)
            """.trimIndent()

        val expected = listOf(5, 13)

        assertEquals(expected, run(program))
    }

    @Test
    fun testEditParametersDoesNotChangeCaller() {
        val program =
            """
                def a(t:int):
                  t = t+1
                  
                def main():
                  val b = 1
                  a(b)
                  print(b)
                  
            """.trimIndent()

        val expected = listOf(1)

        assertEquals(expected, run(program))
    }

    @Test
    fun testWalkerReturn() {
        var program =
            """
                def a(t:int):int
                  result = t+6
                  
                def main():
                  print(a(5))
            """.trimIndent()

        val expected = listOf(11)

        assertEquals(expected, run(program))


        program =
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

        val expected2 = listOf(100, 10)

        assertEquals(expected2, run(program))

    }

    @Test
    fun testPrintString() {
        var program =
            """
                def main():
                  print("hello world!")
            """.trimIndent()

        matchString("hello world!").assertOutputMatch(run(program))


        program =
            """
                def main():
                  val a = "hello world!"
                  
                  a[1] = 67
                  print(a)
                  
            """.trimIndent()

        matchString("hCllo world!").assertOutputMatch(run(program))
    }

    @Test
    fun testFunctionEarlyReturn() {
        val program =
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

        val expected = listOf(10, 20, 30)

        assertEquals(expected, run(program))
    }


}