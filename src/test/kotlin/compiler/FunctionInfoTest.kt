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
        assertThat(layout.fields).hasSize(2)
        assertThat(layout.fields).containsEntry("frame", StructDataField("frame", 0u, stackFrameType))
    }

    @Test
    fun testParam() {
        val layout = getLayout(
            """
            def test1(test:byte):
              print(5)
    """
        )
        assertEquals(layout.size, byte(3))
        assertEquals(layout.sizeOfVars, byte(0))
        assertEquals(layout.sizeOfParameters, byte(1))
        assertEquals(
            layout.fields, mapOf(
                "result" to StructDataField("result", 3u, voidType),
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
              var:byte=5
    """
        )
        assertEquals(layout.size, byte(3))
        assertEquals(layout.sizeOfVars, byte(1))
        assertEquals(layout.sizeOfParameters, byte(0))
        assertThat(layout.fields).containsEntry("var", StructDataField("var", 2u, byteType))
    }

    @Test
    fun testVarParamReturn() {
        val layout = getLayout(
            """
            def test1(param:byte): byte
              var:byte=5
    """
        )
        assertEquals(layout.size, byte(5))
        assertEquals(layout.sizeOfVars, byte(1))
        assertEquals(layout.sizeOfReturn, byte(1))
        assertEquals(layout.sizeOfMeta, byte(2))
        assertEquals(layout.sizeOfParameters, byte(1))

        assertEquals(
            StructBuilder(dummyTypeContainer)
                .addMember("frame", stackFrameType)
                .addMember("param", byteType)
                .addMember("var", byteType)
                .addMember("result", byteType)
                .getFields(), layout.fields
        )

    }

    @Test
    fun testIf() {
        val layout = getLayout(
            """
            def test1():
              if 5:
                var:byte=2
              else:
                var1:byte=3
    """
        )
        assertEquals(layout.size, byte(4))
        assertEquals(layout.sizeOfVars, byte(2))
        assertEquals(layout.sizeOfParameters, byte(0))
        assertEquals(
            layout.fields, mapOf(
                "result" to StructDataField("result", 4u, voidType),
                "frame" to StructDataField("frame", 0u, stackFrameType),
                "var1" to StructDataField("var1", 2u, byteType),
                "var" to StructDataField("var", 3u, byteType),
            )
        )
    }

    @Test
    fun testDescription() {
        val layout = getLayout(
            """
            def test1(var2:byte):
              var:byte=1
            """
        )
        assertEquals(layout.size, byte(4))
        assertEquals(layout.sizeOfVars, byte(1))
        assertEquals(layout.sizeOfParameters, byte(1))
        assertEquals(
            layout.fields, mapOf(
                "result" to StructDataField("result", 4u, voidType),
                "var" to StructDataField("var", 3u, byteType),

                "var2" to StructDataField("var2", 2u, byteType),
                "frame" to StructDataField("frame", 0u, stackFrameType),
            )
        )

        assertIterableEquals(
            listOf(
                "  0: frame: stackFrame",
                "  2: var2: byte",
                "  3: var: byte",
                "  4: result: void",
            ),
            layout.getDescription(),
        )
    }

    @Test
    fun testArrayField() {
        val layout = getLayout(
            """
            def main():
              arr:byte[]
              arr = createArray(8)
        """
        )

        assertThat(layout.fields).containsValue(StructDataField("arr", 2u, Pointer(ArrayType(byteType))))
    }

}