package compiler.backendemulator

import compiler.backends.emulator.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ast.AstParser
import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.BuiltInSignatures
import compiler.FunctionCollection
import compiler.TypeCollection
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
            return Primitives.Integer

        return allTypes[name] ?: throw AssertionError("Did not find $name")

    }
}

private val defaultTypes = listOf(Primitives.Nothing, Primitives.Integer)

val dummyTypeContainer = TypeCollection(
    emptyList(), BuiltInSignatures()
)

internal class FunctionSignatureTest {


    fun getSignature(program: String): BuiltFunction {
        val node = AstParser(tokenizeLines(program)).parseFunctionDefinition()

        val typeProvider = TypeCollection(emptyList(), BuiltInSignatures())
        val functionProvider = FunctionCollection(BuiltInSignatures().functions)


        val function = compileFunctionBody(
            node.asFunction().body,
            definitionFromFunctionNode(node,"dummyfile", typeProvider),
            emptyMap(),
            functionProvider,
            typeProvider,
            "",
            VariableType.Local,
        ).let {
            require(it.size == 1)
            it.first()
        }

        val builder = FunctionBuilder(
            function,
            LayedOutStruct(CompositeDatatype("dummy", emptyList()))
        )

        return builder.buildBody()
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
        assertEquals(layout.sizeOfLocalVariables, 0)
    }

    @Test
    fun testParam() {
        val built = getSignature(
            """
            def test1(test:int):
              print(5)
    """
        )
        val layout = built.layout
        assertEquals(layout.size, 1)
        assertEquals(layout.sizeOfLocalVariables, 0)
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
        assertEquals(1, layout.sizeOfLocalVariables)
        assertEquals(StructDataField("var", Primitives.Integer, 0), layout.getField("var"))
    }

    @Test
    fun testVarParamReturn() {
        val layout = getSignature(
            """
            def test1(param:int): int
              val var:int=5
    """
        ).layout
        assertEquals(layout.size, 3)
        assertEquals(layout.sizeOfLocalVariables, 1)

        assertEquals(StructDataField("result", Primitives.Integer, -2), layout.getField("result"))
        assertEquals(StructDataField("param", Primitives.Integer, -1), layout.getField("param"))
        assertEquals(StructDataField("var", Primitives.Integer, 0), layout.getField("var"))
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
        assertEquals(layout.sizeOfLocalVariables, 2)

        assertEquals(StructDataField("var1", Primitives.Integer, 1), layout.getField("var1"))
        assertEquals(StructDataField("var", Primitives.Integer, 0), layout.getField("var"))
    }

    @Test
    fun testDescription() {
        val layout = getSignature(
            """
            def test1(var2:int):int
              val var:int=1
            """
        ).layout
        assertEquals(layout.size, 3)
        assertEquals(layout.sizeOfLocalVariables, 1)

        assertEquals(StructDataField("result", Primitives.Integer, -2), layout.getField("result"))
        assertEquals(StructDataField("var2", Primitives.Integer, -1), layout.getField("var2"))
        assertEquals(StructDataField("var", Primitives.Integer, 0), layout.getField("var"))

        assertEquals(
            listOf(
                "-2: result: int",
                "-1: var2: int",
                "0: var: int",
            ),
            layout.getDescription(),
        )
    }


    @Test
    fun testSizeOf() {
        kotlin.test.assertEquals(1, sizeOf(Primitives.Integer))
        kotlin.test.assertEquals(1, sizeOf(Primitives.Boolean))
        kotlin.test.assertEquals(1, sizeOf(Primitives.Integer.pointerOf()))

        assertThrows<EmulatorBackendCompilerError> { sizeOf(Primitives.Integer.arrayOf()) }
        kotlin.test.assertEquals(1, sizeOf(Primitives.Integer.arrayPointerOf()))

        kotlin.test.assertEquals(
            1,
            sizeOf(CompositeDatatype("test", listOf(CompositeDataTypeField("a", Primitives.Integer))))
        )
        kotlin.test.assertEquals(
            2,
            sizeOf(
                CompositeDatatype(
                    "test",
                    listOf(
                        CompositeDataTypeField("a", Primitives.Integer),
                        CompositeDataTypeField("b", Primitives.Integer)
                    )
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
        assertEquals(layout.sizeOfLocalVariables, 2)
        assertEquals(StructDataField("var", dummyTypeContainer.requireType("intpair"), 0), layout.getField("var"))
    }

}