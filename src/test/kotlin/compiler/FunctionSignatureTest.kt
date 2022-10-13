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


    fun getSignature(program: String): BuiltFunction {
        val node = AstParser(parseFile(StringReader(program))).parseFunctionDefinition()


        val functionProvider = object : FunctionProvider {
            override fun findSignature(name: String, parameterSignature: List<DataType>): FunctionSignature {
                return FunctionSignature.fromNode(node, dummyTypeContainer)
            }

        }


        val builder = FunctionBuilder(
            FunctionSignature.fromNode(node, dummyTypeContainer),
            functionProvider, dummyTypeContainer
        )

        return builder.buildBody(node.childNodes)
    }

    @Test
    fun testEmpty() {
        val build = getSignature(
            """
            def test1():
              print(5)
    """
        )
        val layout = build.layout

        assertEquals(layout.size, 2)
        assertEquals(build.sizeOfVars, 0)
        assertThat(layout.fields).hasSize(2)
        assertThat(layout.fields).containsEntry("frame", StructDataField("frame", 0, stackFrameType))
    }

    @Test
    fun testParam() {
        val built = getSignature(
            """
            def test1(test:byte):
              print(5)
    """
        )
        val layout = built.layout
        assertEquals(layout.size, 3)
        assertEquals(built.sizeOfVars, 0)
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
        val built = getSignature(
            """
            def test1():
              val var:byte=5
    """
        )
        val layout = built.layout
        assertEquals(layout.size, 3)
        assertEquals(built.sizeOfVars, 1)
        assertThat(layout.fields).containsEntry("var", StructDataField("var", 2, byteType))
    }

    @Test
    fun testVarParamReturn() {
        val built = getSignature(
            """
            def test1(param:byte): byte
              val var:byte=5
    """
        )
        assertEquals(built.layout.size, 5)
        assertEquals(built.sizeOfVars, 1)

        assertEquals(
            StructBuilder()
                .addMember("frame", stackFrameType)
                .addMember("param", byteType)
                .addMember("var", byteType)
                .addMember("result", byteType)
                .getFields(), built.layout.fields
        )

    }

    @Test
    fun testIf() {
        val built = getSignature(
            """
            def test1():
              if 5:
                val var:byte=2
              else:
                val var1:byte=3
    """
        )
        assertEquals(built.layout.size, 4)
        assertEquals(built.sizeOfVars, 2)
        assertEquals(
            mapOf(
                "result" to StructDataField("result", 4, voidType),
                "frame" to StructDataField("frame", 0, stackFrameType),
                "var1" to StructDataField("var1", 3, byteType),
                "var" to StructDataField("var", 2, byteType),
            ), built.layout.fields
        )
    }

    @Test
    fun testDescription() {
        val built = getSignature(
            """
            def test1(var2:byte):
              val var:byte=1
            """
        )
        assertEquals(built.layout.size, 4)
        assertEquals(built.sizeOfVars, 1)
        assertEquals(
            built.layout.fields, mapOf(
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
            built.layout.getDescription(),
        )
    }
}