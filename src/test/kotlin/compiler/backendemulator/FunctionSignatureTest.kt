package compiler.backendemulator

import compiler.backends.emulator.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ast.AstParser
import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.frontend.*
import org.junit.jupiter.api.assertThrows
import tokenizeLines

class TypeContainer(
    types: List<Datatype>,
    aliases: Map<String, Datatype>
) : TypeProvider {

    private val allTypes = aliases + types.associateBy { it.name }
    override fun getType(name: String): Datatype {
        if (name.isEmpty())
            return Datatype.Integer

        return allTypes[name] ?: throw AssertionError("Did not find $name")

    }
}

private val defaultTypes = listOf(Datatype.Void, Datatype.Integer)

val dummyTypeContainer = TypeContainer(
    defaultTypes,
    mapOf(
        "byte" to Datatype.Integer,
        "intpair" to Datatype.Composite(
            "intpair",
            listOf(
                CompositeDataTypeField("first", Datatype.Integer),
                CompositeDataTypeField("second", Datatype.Integer)
            )
        )
    )
)

internal class FunctionSignatureTest {


    fun getSignature(program: String): BuiltFunction {
        val node = AstParser(tokenizeLines(program)).parseFunctionDefinition()


        val functionProvider = object : FunctionSignatureResolver {
            override fun getFunctionDefinitionMatching(
                name: String,
                functionType: FunctionType,
                parameterTypes: List<Datatype>
            ): FunctionSignature {

                return when (name) {
                    OperatorBuiltIns.Equal -> FunctionSignature(
                        OperatorBuiltIns.Equal, listOf(
                            Datatype.Integer,
                            Datatype.Integer
                        ), Datatype.Boolean, FunctionType.Operator
                    )

                    "print" -> FunctionSignature(
                        "print", listOf(
                            Datatype.Integer,
                            Datatype.Integer
                        ), Datatype.Void, FunctionType.Normal
                    )

                    "main", "test1" -> definitionFromFunctionNode(node, dummyTypeContainer).signature
                    else -> throw NotImplementedError()
                }
            }

        }

        TODO()
//        val builder = FunctionBuilder(
//            definitionFromFunctionNode(node, dummyTypeContainer),
//            functionProvider, dummyTypeContainer
//        )
//
//        return builder.buildBody(node)
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

        assertEquals(layout.size, 0)
        assertEquals(layout.sizeOfType(FieldAnnotation.LocalVariable), 0)
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
        assertEquals(layout.size, 1)
        assertEquals(layout.sizeOfType(FieldAnnotation.LocalVariable), 0)
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
        assertEquals(1, layout.size)
        assertEquals(1, layout.sizeOfType(FieldAnnotation.LocalVariable))
        assertEquals(StructDataField("var", Datatype.Integer, 0), layout.getField("var"))
    }

    @Test
    fun testVarParamReturn() {
        val layout = getSignature(
            """
            def test1(param:byte): byte
              val var:byte=5
    """
        ).layout
        assertEquals(layout.size, 3)
        assertEquals(layout.sizeOfType(FieldAnnotation.LocalVariable), 1)

        assertEquals(StructDataField("result", Datatype.Integer, -2), layout.getField("result"))
        assertEquals(StructDataField("param", Datatype.Integer, -1), layout.getField("param"))
        assertEquals(StructDataField("var", Datatype.Integer, 0), layout.getField("var"))
    }

    @Test
    fun testIf() {
        val layout = getSignature(
            """
            def test1():
              if 5==0:
                val var:byte=2
              else:
                val var1:byte=3
    """
        ).layout
        assertEquals(layout.size, 2)
        assertEquals(layout.sizeOfType(FieldAnnotation.LocalVariable), 2)

        assertEquals(StructDataField("var1", Datatype.Integer, 1), layout.getField("var1"))
        assertEquals(StructDataField("var", Datatype.Integer, 0), layout.getField("var"))
    }

    @Test
    fun testDescription() {
        val layout = getSignature(
            """
            def test1(var2:byte):byte
              val var:byte=1
            """
        ).layout
        assertEquals(layout.size, 3)
        assertEquals(layout.sizeOfType(FieldAnnotation.LocalVariable), 1)

        assertEquals(StructDataField("result", Datatype.Integer, -2), layout.getField("result"))
        assertEquals(StructDataField("var2", Datatype.Integer, -1), layout.getField("var2"))
        assertEquals(StructDataField("var", Datatype.Integer, 0), layout.getField("var"))

        assertEquals(
            listOf(
                "-2: result: integer",
                "-1: var2: integer",
                "0: var: integer",
            ),
            layout.getDescription(),
        )
    }


    @Test
    fun testSizeOf() {
        kotlin.test.assertEquals(1, sizeOf(Datatype.Integer))
        kotlin.test.assertEquals(1, sizeOf(Datatype.Boolean))
        kotlin.test.assertEquals(1, sizeOf(Datatype.Pointer(Datatype.Integer)))

        assertThrows<EmulatorBackendCompilerError> { sizeOf(Datatype.Array(Datatype.Integer)) }
        kotlin.test.assertEquals(1, sizeOf(Datatype.ArrayPointer(Datatype.Integer)))

        kotlin.test.assertEquals(
            1,
            sizeOf(Datatype.Composite("test", listOf(CompositeDataTypeField("a", Datatype.Integer))))
        )
        kotlin.test.assertEquals(
            2,
            sizeOf(
                Datatype.Composite(
                    "test",
                    listOf(CompositeDataTypeField("a", Datatype.Integer), CompositeDataTypeField("b", Datatype.Integer))
                )
            )
        )
    }

    @Test
    fun testComposite() {
        val built = getSignature(
            """
            def test1():
              val var:intpair
    """
        )
        val layout = built.layout
        assertEquals(layout.size, 2)
        assertEquals(layout.sizeOfType(FieldAnnotation.LocalVariable), 2)
        assertEquals(StructDataField("var", dummyTypeContainer.getType("intpair"), 0), layout.getField("var"))
    }

}