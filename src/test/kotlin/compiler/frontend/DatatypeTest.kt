package compiler.frontend

import ast.parserFromFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

private val builtInTypeProvider = object : TypeProvider {
    override fun getType(name: String): Datatype? {
        return builtInTypes[name]
    }
}

class DatatypeTest {

    @Test
    fun testBasic() {

        val compositeType = Datatype.Composite(
            "test", listOf(
                CompositeDataTypeField("member1", Datatype.Integer)
            )
        )

        assertEquals(true, Datatype.Integer.isPrimitive)
        assertEquals(false, Datatype.Void.isPrimitive)
        assertEquals(false, compositeType.isPrimitive)

        assertThrows<IllegalArgumentException> { Datatype.Integer.compositeFields }
        assertThrows<IllegalArgumentException> { Datatype.Void.compositeFields }
    }

    @Test
    fun fromNode() {
        val node = parserFromFile(
            """
           struct test:
             a:int
             b:int
        """.trimIndent()
        ).parseStruct()
        val type = buildStruct(node, builtInTypeProvider)
        assertEquals(true, type.isComposite)
        assertEquals(false, type.isPointer)
        assertEquals(false, type.isPointer)
        assertEquals(false, type.isArray)
        assertEquals(
            listOf(
                CompositeDataTypeField("a", Datatype.Integer),
                CompositeDataTypeField("b", Datatype.Integer),
            ), type.compositeFields
        )
    }


}