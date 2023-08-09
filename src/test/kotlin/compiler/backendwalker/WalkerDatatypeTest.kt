package compiler.backendwalker

import ast.expression.OperatorBuiltIns
import compiler.BuiltInSignatures
import compiler.backends.astwalker.*
import compiler.compileAndRunProgram
import compiler.features.intMatcher
import compiler.frontend.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.Reader
import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


internal fun run(program: String, maxLoopIterations: Int = 1000): List<Int> {
    val runner = WalkerRunner(WalkConfig(maxLoopIterations = maxLoopIterations))
    val intermediate = ProgramCompiler(object : FileProvider {
        override fun getReader(filename: String): Reader {
            return StringReader(program)
        }
    }, "dummyfile", BuiltInSignatures()).compile()
    return runner.buildAndRun(intermediate.allTypes, intermediate.functions, intermediate.globals)
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

        intMatcher(
            5, 0,
            5, 5,
            10, 5
        ).assertOutputMatch(run(code))
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
        intMatcher(0, 5, 0, 3, 5).assertOutputMatch(run(program))
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
        intMatcher(11, 1).assertOutputMatch(run(program))
    }
}

class NewTest {

    @Test
    fun test() {
        val myDatatype = CompositeDatatype(
            "test", listOf(
                CompositeDataTypeField("field1", Primitives.Integer),
                CompositeDataTypeField("field2", Primitives.Integer),
                CompositeDataTypeField("field3", Primitives.Integer),
            )
        )
        val holder = ValueHolder(myDatatype)

        assertNotEquals(holder, ValueHolder(myDatatype))


        val entireView = holder.viewEntire()

        assertEquals(
            ValueHolder.View(
                holder, myDatatype, 0 until 3
            ), entireView
        )

        val viewField1 = entireView.viewField("field1")
        val viewField2 = entireView.viewField("field2")
        val viewField3 = entireView.viewField("field3")
        assertEquals(
            ValueHolder.View(
                holder, Primitives.Integer, 0 until 1
            ), viewField1
        )
        assertEquals(
            ValueHolder.View(
                holder, Primitives.Integer, 2 until 3
            ), viewField3
        )

        assertEquals(PrimitiveValue.integer(0), viewField1.getPrimitiveValue())
        assertEquals(PrimitiveValue.integer(0), viewField3.getPrimitiveValue())

        // Set field3 to 5
        viewField3.setPrimitiveValue(PrimitiveValue.integer(5))
        assertEquals(PrimitiveValue.integer(0), viewField1.getPrimitiveValue())
        assertEquals(PrimitiveValue.integer(5), viewField3.getPrimitiveValue())

        //Set field3 to 2 and field2 to 8
        val fields = entireView.getValue().primitives.toMutableList().apply {
            this[2] = PrimitiveValue.integer(2)
            this[1] = PrimitiveValue.integer(8)
        }

        entireView.applyValue(Value(entireView.datatype, fields))
        assertEquals(PrimitiveValue.integer(0), viewField1.getPrimitiveValue())
        assertEquals(PrimitiveValue.integer(8), viewField2.getPrimitiveValue())
        assertEquals(PrimitiveValue.integer(2), viewField3.getPrimitiveValue())


    }

}