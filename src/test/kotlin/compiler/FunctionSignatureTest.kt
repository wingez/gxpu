package se.wingez.compiler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import se.wingez.ast.AstParser
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

internal class FunctionSignatureTest {


    fun getSignature(program: String, types: List<DataType> = defaultTypes): FrameLayout {
        val node = AstParser(parseFile(StringReader(program))).parseFunctionDefinition()

        return calculateSignature(node, TypeContainer(types))

    }

    @Test
    fun testEmpty() {
        val layout = getSignature(
            """
            def test1():
              print(5)
    """
        )
        assertEquals(layout.size, 2)
        assertEquals(layout.sizeOfVars, 0)
        assertEquals(layout.sizeOfParameters, 0)
        assertThat(layout.fields).hasSize(2)
        assertThat(layout.fields).containsEntry("frame", StructDataField("frame", 0, stackFrameType))
    }

    @Test
    fun testParam() {
        val layout = getSignature(
            """
            def test1(test:byte):
              print(5)
    """
        )
        assertEquals(layout.size, 3)
        assertEquals(layout.sizeOfVars, 0)
        assertEquals(layout.sizeOfParameters, 1)
        assertEquals(
            layout.fields, mapOf(
                "result" to StructDataField("result", 3, voidType),
                "frame" to StructDataField("frame", 0, stackFrameType),
                "test" to StructDataField("test", 2, byteType)
            )
        )
    }

    @Test
    fun testVar() {
        val layout = getSignature(
            """
            def test1():
              var:byte=5
    """
        )
        assertEquals(layout.size, 3)
        assertEquals(layout.sizeOfVars, 1)
        assertEquals(layout.sizeOfParameters, 0)
        assertThat(layout.fields).containsEntry("var", StructDataField("var", 2, byteType))
    }

    @Test
    fun testVarParamReturn() {
        val layout = getSignature(
            """
            def test1(param:byte): byte
              var:byte=5
    """
        )
        assertEquals(layout.size, 5)
        assertEquals(layout.sizeOfVars, 1)
        assertEquals(layout.sizeOfReturn, 1)
        assertEquals(layout.sizeOfMeta, 2)
        assertEquals(layout.sizeOfParameters, 1)

        assertEquals(
            StructBuilder()
                .addMember("frame", stackFrameType)
                .addMember("param", byteType)
                .addMember("var", byteType)
                .addMember("result", byteType)
                .getFields(), layout.fields
        )

    }

    @Test
    fun testIf() {
        val layout = getSignature(
            """
            def test1():
              if 5:
                var:byte=2
              else:
                var1:byte=3
    """
        )
        assertEquals(layout.size, 4)
        assertEquals(layout.sizeOfVars, 2)
        assertEquals(layout.sizeOfParameters, 0)
        assertEquals(
            layout.fields, mapOf(
                "result" to StructDataField("result", 4, voidType),
                "frame" to StructDataField("frame", 0, stackFrameType),
                "var1" to StructDataField("var1", 2, byteType),
                "var" to StructDataField("var", 3, byteType),
            )
        )
    }

    @Test
    fun testDescription() {
        val layout = getSignature(
            """
            def test1(var2:byte):
              var:byte=1
            """
        )
        assertEquals(layout.size, 4)
        assertEquals(layout.sizeOfVars, 1)
        assertEquals(layout.sizeOfParameters, 1)
        assertEquals(
            layout.fields, mapOf(
                "result" to StructDataField("result", 4, voidType),
                "var" to StructDataField("var", 3, byteType),

                "var2" to StructDataField("var2", 2, byteType),
                "frame" to StructDataField("frame", 0, stackFrameType),
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
}