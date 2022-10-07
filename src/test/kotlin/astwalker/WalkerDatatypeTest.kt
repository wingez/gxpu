package se.wingez.astwalker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.parserFromFile
import java.util.NoSuchElementException
import kotlin.test.assertEquals


internal class WalkerDatatypeTest {

    @Test
    fun testBasic() {

        val compositeType = Datatype(
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

        return createTypeFromNode(node, typeProvider)
    }

    @Test
    fun fromNode() {
        val type = getDummyType()
        assertEquals(true, type.isComposite())
        assertEquals(mapOf("a" to Datatype.Integer, "b" to Datatype.Integer), type.compositeMembers)
    }

    @Test
    fun testVariables() {
        val type = getDummyType()

        assertThrows<AssertionError> { Variable(type, 0) }
        assertThrows<NoSuchElementException> { Variable(type, mapOf()) }
        // Missing member
        assertThrows<NoSuchElementException> { Variable(type, mapOf("a" to Variable(Datatype.Integer, 0))) }
        // Wrong member type
        assertThrows<AssertionError> {
            Variable(
                type, mapOf(
                    "a" to Variable(Datatype.Integer, 0),
                    "b" to Variable(Datatype.Void),
                )
            )
        }

        val okMember = Variable(
            type, mapOf(
                "a" to Variable(Datatype.Integer, 0),
                "b" to Variable(Datatype.Integer, 1),
            )
        )

        assertEquals(0, okMember.getField("a").getPrimitiveValue())
        assertEquals(1, okMember.getField("b").getPrimitiveValue())

        okMember.setField("b", Variable(Datatype.Integer, 5))

        assertEquals(0, okMember.getField("a").getPrimitiveValue())
        assertEquals(5, okMember.getField("b").getPrimitiveValue())
    }

    @Test
    fun testMemberAssign() {
        val code = """
            struct test:
              a:int
              b:int
            
            def main():
              t:new test
              
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
    fun testFibonacci() {
        val program = """
          def main():
            a=1
            b=0
            c=0
    
            counter=0
            while (10-counter)!=0:
              print(a)
              c=a+b
              b=a
              a=c
              
              counter = counter+1 
                  
    """
        val nodes = parserFromFile(program).parse()

        assertEquals(listOf(1, 1, 2, 3, 5, 8, 13, 21, 34, 55).map { it.toString() }, walk(nodes).result)

    }
}