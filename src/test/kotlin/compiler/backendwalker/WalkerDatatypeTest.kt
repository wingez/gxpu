package compiler.backendwalker

import ast.expression.OperatorBuiltIns
import ast.parserFromFile
import compiler.BuiltInSignatures
import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerRunner
import compiler.compileAndRunProgram
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.StringReader
import kotlin.test.assertEquals


internal fun run(program:String,maxLoopIterations: Int =1000):List<String>{
    val runner = WalkerRunner(WalkConfig(maxLoopIterations = maxLoopIterations))
    return compileAndRunProgram(StringReader(program),"dummyfile", runner, BuiltInSignatures())
}

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
        assertEquals(
            listOf(
                5, 0,
                5, 5,
                10, 5
            ).map { it.toString() }, run(code)
        )
    }

    @Test
    fun testCreateArray() {
        val program = """
          def main():
            val a:int[] = createArray(5)
                  
    """
        assertDoesNotThrow { run(program) }
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
        assertEquals(listOf(0, 5, 0, 3, 5).map { it.toString() }, run(program))
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
        assertEquals(listOf(11, 1).map { it.toString() }, run(program))

    }
}