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

    fun getDummyType(): Datatype {
        val node = parserFromFile(
            """
            struct test:
              a:int
              b:int
        """.trimIndent()
        ).parseStruct()

        val typeProvider = mapOf(
            "int" to Datatype.Integer
        )

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
}