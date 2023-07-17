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


}