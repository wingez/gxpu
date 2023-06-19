package compiler.backendwalker

import ast.expression.OperatorBuiltIns
import ast.parserFromFile
import compiler.backends.astwalker.walk
import compiler.frontend.Datatype
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals


internal class WalkerDatatypeTest {



    @Test
    fun testMemberAssign() {
        val code = """
            struct test:
              a:int
              b:int
            
            def main():
              val t:test
              
              t.a = 5
              t.b = 0
              print(t.a)
              print(t.b)
              
              t.b=t.a
              print(t.a)
              print(t.b)
              
              t.a = 10
              print(t.a)
              print(t.b)
        """.trimIndent()

        val nodes = parserFromFile(code).parse()

        assertEquals(
            listOf(
                5, 0,
                5, 5,
                10, 5
            ).map { it.toString() }, walk(nodes).result
        )
    }

    @Test
    fun testCreateArray() {
        val program = """
          def main():
            val a:int[] = createArray(5)
                  
    """
        val nodes = parserFromFile(program).parse()

        assertDoesNotThrow { walk(nodes) }
    }

    @Test
    fun testArrayAssign() {
        val program = """
          def main():
            val a:int[] = createArray(5)
            print(a[0])
            a[1] = 5
            print(a[1])
            print(a[0])
            a[0]= 3
            print(a[0])
            print(a[1])
            
            
    """
        val nodes = parserFromFile(program).parse()
        assertEquals(listOf(0, 5, 0, 3, 5).map { it.toString() }, walk(nodes).result)
    }

    @Test
    fun testFunctionTypes() {
        assertEquals("add", OperatorBuiltIns.Addition)
        val program = """
          def add(a:int,b:int):int
            result = a-b
          def main():
            print(6+5)
            print(add(6,5))
    """
        val nodes = parserFromFile(program).parse()
        assertEquals(listOf(11, 1).map { it.toString() }, walk(nodes).result)

    }
}