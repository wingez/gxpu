package compiler.frontend

import ast.parserFromFile
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private val builtInTypeProvider = object : TypeProvider {
    override fun getType(name: String): Datatype? {
        return builtInTypes[name]
    }
}

class DatatypeTest {

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
        assertEquals(
            listOf(
                CompositeDataTypeField("a", Primitives.Integer),
                CompositeDataTypeField("b", Primitives.Integer),
            ), type.compositeFields
        )
    }

    @Test
    fun testToString() {
        val myType = Primitives.Integer

        assertEquals("int", myType.toString())
        assertEquals("int", myType.name)

        assertEquals("array[int]", myType.arrayOf().toString())
        assertEquals("array[int]", myType.arrayOf().name)

        assertEquals("pointer[int]", myType.pointerOf().toString())
        assertEquals("pointer[int]", myType.pointerOf().name)

        val myComposite = CompositeDatatype(
            "pair", listOf(
                CompositeDataTypeField("fst", Primitives.Integer),
                CompositeDataTypeField("snd", Primitives.Integer.pointerOf()),
            )
        )
        assertEquals("pair", myComposite.name)
        assertEquals("pair{fst:int, snd:pointer[int]}", myComposite.toString())
    }

}