package se.wingez.compiler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import se.wingez.ast.AstParser
import se.wingez.byte
import se.wingez.tokens.parseFile
import java.io.StringReader

class TypeContainer(
    private val types: List<DataType>
) : TypeProvider {
    override fun getType(name: String): DataType {
        if (name.isEmpty())
            return byteType

        return types.find { it.name == name } ?: throw AssertionError("Did not find $name")

    }
}

private val defaultTypes = listOf(voidType, byteType)

val dummyTypeContainer = TypeContainer(
    defaultTypes
)

internal class FunctionInfoTest {


    fun getLayout(program: String, types: List<DataType> = defaultTypes): FunctionInfo {
        val node = AstParser(parseFile(StringReader(program))).parseFunctionDefinition()

        return calculateFrameLayout(node, TypeContainer(types), 0u)

    }

    @Test
    fun testEmpty() {
        val layout = getLayout(
            """
            def test1():
              print(5)
    """
        )
        assertEquals(layout.size, byte(2))
        assertEquals(layout.sizeOfVars, byte(0))
        assertEquals(layout.sizeOfParameters, byte(0))
        assertThat(layout.fields).hasSize(1)
        assertThat(layout.fields).containsEntry("frame", StructDataField("frame", 0u, stackFrameType))
    }

    @Test
    fun testParam() {
        val layout = getLayout(
            """
            def test1(test):
              print(5)
    """
        )
        assertEquals(layout.size, byte(3))
        assertEquals(layout.sizeOfVars, byte(0))
        assertEquals(layout.sizeOfParameters, byte(1))
        assertEquals(
            layout.fields, mapOf(
                "frame" to StructDataField("frame", 0u, stackFrameType),
                "test" to StructDataField("test", 2u, byteType)
            )
        )
    }

    @Test
    fun testVar() {
        val layout = getLayout(
            """
            def test1():
              var=5
    """
        )
        assertEquals(layout.size, byte(3))
        assertEquals(layout.sizeOfVars, byte(1))
        assertEquals(layout.sizeOfParameters, byte(0))
        assertThat(layout.fields).containsEntry("var", StructDataField("var", 0u, byteType))
    }

    @Test
    fun testIf() {
        val layout = getLayout(
            """
            def test1():
              if 5:
                var=2
              else:
                var1=3
    """
        )
        assertEquals(layout.size, byte(4))
        assertEquals(layout.sizeOfVars, byte(2))
        assertEquals(layout.sizeOfParameters, byte(0))
        assertEquals(
            layout.fields, mapOf(
                "frame" to StructDataField("frame", 2u, stackFrameType),
                "var1" to StructDataField("var1", 0u, byteType),
                "var" to StructDataField("var", 1u, byteType),
            )
        )
    }

    @Test
    fun testDescription() {
        val layout = getLayout(
            """
            def test1(var2):
              var=1
            """
        )
        assertEquals(layout.size, byte(4))
        assertEquals(layout.sizeOfVars, byte(1))
        assertEquals(layout.sizeOfParameters, byte(1))
        assertEquals(
            layout.fields, mapOf(
                "frame" to StructDataField("frame", 1u, stackFrameType),
                "var2" to StructDataField("var2", 3u, byteType),
                "var" to StructDataField("var", 0u, byteType),
            )
        )

        assertIterableEquals(
            listOf(
                "  0: var: byte",
                "  1: frame: stackFrame",
                "  3: var2: byte",
            ),
            layout.getDescription(),
        )
    }

}