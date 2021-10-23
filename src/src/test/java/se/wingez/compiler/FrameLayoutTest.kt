package se.wingez.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import se.wingez.Tokenizer
import se.wingez.ast.AstParser
import java.io.StringReader

internal class FrameLayoutTest {

    class TypeContainer(
            private val types: List<DataType>
    ) : TypeProvider {
        override fun getType(name: String): DataType {
            if (name.isEmpty())
                return byteType

            return types.find { it.name == name } ?: throw AssertionError("Did not find $name")

        }
    }


    val t = Tokenizer()
    val defaultTypes = listOf(voidType, byteType)

    fun getLayout(program: String, types: List<DataType> = defaultTypes): FrameLayout {
        val node = AstParser(t.parseFile(StringReader(program))).parseFunctionDefinition()

        return calculateFrameLayout(node, TypeContainer(types))

    }

    @Test
    fun testEmpty() {
        val layout = getLayout("""
            def test1():
              print(5)
    """)
        assertEquals(layout.size, 2)
        assertEquals(layout.sizeOfVars, 0)
        assertEquals(layout.sizeOfParameters, 0)
        assertEquals(layout.fields, emptyMap<String, StructDataField>())
    }

    @Test
    fun testParam() {
        val layout = getLayout("""
            def test1(test):
              print(5)
    """)
        assertEquals(layout.size, 3)
        assertEquals(layout.sizeOfVars, 0)
        assertEquals(layout.sizeOfParameters, 1)
        assertEquals(layout.fields, mapOf("test" to StructDataField("test", 2, byteType)))
    }

    @Test
    fun testVar() {
        val layout = getLayout("""
            def test1():
              var=5
    """)
        assertEquals(layout.size, 3)
        assertEquals(layout.sizeOfVars, 1)
        assertEquals(layout.sizeOfParameters, 0)
        assertEquals(layout.fields, mapOf("var" to StructDataField("var", 0, byteType)))
    }

    @Test
    fun testIf() {
        val layout = getLayout("""
            def test1():
              if 5:
                var=2
              else:
                var1=3
    """)
        assertEquals(layout.size, 4)
        assertEquals(layout.sizeOfVars, 2)
        assertEquals(layout.sizeOfParameters, 0)
        assertEquals(layout.fields, mapOf(
                "var1" to StructDataField("var1", 0, byteType),
                "var" to StructDataField("var", 1, byteType),
        ))
    }

    @Test
    fun testDescription() {
        val layout = getLayout("""
            def test1(var2):
              var=1
    """)
        assertEquals(layout.size, 4)
        assertEquals(layout.sizeOfVars, 1)
        assertEquals(layout.sizeOfParameters, 1)
        assertEquals(layout.fields, mapOf(
                "var2" to StructDataField("var2", 3, byteType),
                "var" to StructDataField("var", 0, byteType),
        ))

        assertIterableEquals(layout.getDescription(), listOf(
                "  0: var: byte",
                "  3: var2: byte"
        ))
    }

}