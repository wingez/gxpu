package se.wingez.compiler.backendwalker

import compiler.frontend.Datatype
import compiler.frontend.TypeProvider
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.FunctionType
import ast.expression.OperatorBuiltIns
import se.wingez.ast.parserFromFile
import se.wingez.compiler.backends.astwalker.*
import se.wingez.compiler.frontend.FunctionDefinition
import se.wingez.compiler.frontend.FunctionDefinitionResolver
import java.util.NoSuchElementException
import kotlin.test.assertEquals


internal class WalkerDatatypeTest {

    @Test
    fun testBasic() {

        val compositeType = Datatype.Composite(
            "test", mapOf(
                "member1" to Datatype.Integer
            )
        )

        assertEquals(true, Datatype.Integer.isPrimitive())
        assertEquals(false, Datatype.Void.isPrimitive())
        assertEquals(false, compositeType.isPrimitive())

        assertThrows<AssertionError> { Datatype.Integer.compositeMembers }
        assertThrows<AssertionError> { Datatype.Void.compositeMembers }
    }

    private fun getDummyType(): Datatype {
        val node = parserFromFile(
            """
            struct test:
              a:int
              b:int
        """.trimIndent()
        ).parseStruct()
        val types = mapOf(
            "int" to Datatype.Integer
        )
        val typeProvider = object : TypeProvider {
            override fun getType(name: String): Datatype {
                return types.getValue(name)
            }
        }
        val functionProvider = object : FunctionDefinitionResolver {
            override fun getFunctionDefinitionMatching(
                name: String,
                functionType: FunctionType,
                parameterTypes: List<Datatype>
            ): FunctionDefinition {
                TODO("Not yet implemented")
            }
        }
        val variableProvider = object : VariableProvider {
            override fun getTypeOfVariable(variableName: String): Datatype {
                TODO("Not yet implemented")
            }
        }

        return createTypeFromNode(node, variableProvider, functionProvider, typeProvider)
    }

    @Test
    fun fromNode() {
        val type = getDummyType()
        assertEquals(true, type.isComposite())
        assertEquals(mapOf("a" to Datatype.Integer, "b" to Datatype.Integer), type.compositeMembers)
    }



    @Test
    @Disabled
    fun testMemberAssign() {
        val code = """
            struct test:
              a:int
              b:int
            
            def main():
              val t:new test
              
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
    fun testReadArraySize() {
        val program = """
          def main():
            val a:int[] = createArray(5)
            print(a.size())
                  
    """
        val nodes = parserFromFile(program).parse()
        assertEquals(listOf(5).map { it.toString() }, walk(nodes).result)
    }

    @Test
    @Disabled
    fun testArraySizeReadonly() {
        val program = """
          def main():
            val a:int[] = createArray(5)
            a.size() = 10
    """
        val nodes = parserFromFile(program).parse()
        assertThrows<WalkerException> { walk(nodes) }
    }

    @Test
    fun testArrayRead() {
        val program = """
          def main():
            val a:int[] = createArray(10)
            print(a.size())
            print(a[0])
            
    """
        val nodes = parserFromFile(program).parse()
        assertEquals(listOf(10, 0).map { it.toString() }, walk(nodes).result)
    }

    @Test
    fun testArrayReadOutOfBounds() {
        val program = """
          def main():
            val a:int[] = createArray(10)
            print(a.size())
            print(a[10])
            
    """
        val nodes = parserFromFile(program).parse()
        assertThrows<WalkerException> { walk(nodes) }
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