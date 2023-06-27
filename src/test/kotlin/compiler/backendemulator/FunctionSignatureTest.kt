package compiler.backendemulator

import compiler.backends.emulator.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ast.AstParser
import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.frontend.*
import tokenizeLines
import tokens.parseFile
import java.io.StringReader

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
    mapOf("byte" to Datatype.Integer)
)

internal class FunctionSignatureTest {


    fun getSignature(program: String): BuiltFunction {
        val node = AstParser(tokenizeLines(program)).parseFunctionDefinition()


        val functionProvider = object : FunctionDefinitionResolver {
            override fun getFunctionDefinitionMatching(
                name: String,
                functionType: FunctionType,
                parameterTypes: List<Datatype>
            ): FunctionDefinition {

                return when (name) {
                    OperatorBuiltIns.Equal -> FunctionDefinition(
                        OperatorBuiltIns.Equal, listOf(
                            Datatype.Integer,
                            Datatype.Integer
                        ), Datatype.Boolean, FunctionType.Operator
                    )

                    "print" -> FunctionDefinition(
                        "print", listOf(
                            Datatype.Integer,
                            Datatype.Integer
                        ), Datatype.Void, FunctionType.Normal
                    )

                    "main", "test1" -> FunctionDefinition.fromFunctionNode(node, dummyTypeContainer)
                    else -> throw NotImplementedError()
                }
            }

        }


        val builder = FunctionBuilder(
            FunctionDefinition.fromFunctionNode(node, dummyTypeContainer),
            functionProvider, dummyTypeContainer, dummyDatatypeSizeProvider
        )

        return builder.buildBody(node)
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
        assertThat(layout.layout).hasSize(0)
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
        assertEquals(
            layout.layout, mapOf(
                CompositeDataTypeField("test", Datatype.Integer, FieldAnnotation.Parameter) to StructDataField(
                    "test",
                    Datatype.Integer,
                    -1, 1
                )
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
        assertEquals(layout.size, 1)
        assertEquals(layout.sizeOfType(FieldAnnotation.LocalVariable), 1)
        assertThat(layout.layout).containsEntry(
            CompositeDataTypeField("var", Datatype.Integer, FieldAnnotation.LocalVariable),
            StructDataField("var", Datatype.Integer, 0, 1)
        )
    }

    @Test
    fun testVarParamReturn() {
        val built = getSignature(
            """
            def test1(param:byte): byte
              val var:byte=5
    """
        )
        assertEquals(built.layout.size, 3)
        assertEquals(built.layout.sizeOfType(FieldAnnotation.LocalVariable), 1)

        assertEquals(
            mapOf(
                CompositeDataTypeField("result", Datatype.Integer, FieldAnnotation.Result) to
                        StructDataField("result", Datatype.Integer, -2, 1),
                CompositeDataTypeField("param", Datatype.Integer, FieldAnnotation.Parameter) to
                        StructDataField("param", Datatype.Integer, -1, 1),
                CompositeDataTypeField("var", Datatype.Integer, FieldAnnotation.LocalVariable) to
                        StructDataField("var", Datatype.Integer, 0, 1)
            ), built.layout.layout
        )

    }

    @Test
    fun testIf() {
        val built = getSignature(
            """
            def test1():
              if 5==0:
                val var:byte=2
              else:
                val var1:byte=3
    """
        )
        assertEquals(built.layout.size, 2)
        assertEquals(built.layout.sizeOfType(FieldAnnotation.LocalVariable), 2)
        assertEquals(
            mapOf(
                CompositeDataTypeField("var1", Datatype.Integer, FieldAnnotation.LocalVariable) to
                        StructDataField("var1", Datatype.Integer, 1, 1),
                CompositeDataTypeField("var", Datatype.Integer, FieldAnnotation.LocalVariable) to
                        StructDataField("var", Datatype.Integer, 0, 1),
            ), built.layout.layout
        )
    }

    @Test
    fun testDescription() {
        val built = getSignature(
            """
            def test1(var2:byte):byte
              val var:byte=1
            """
        )
        assertEquals(built.layout.size, 3)
        assertEquals(built.layout.sizeOfType(FieldAnnotation.LocalVariable), 1)
        assertEquals(
            built.layout.layout, mapOf(
                CompositeDataTypeField("result", Datatype.Integer, FieldAnnotation.Result) to StructDataField("result", Datatype.Integer, -2, 1),

                CompositeDataTypeField("var2", Datatype.Integer, FieldAnnotation.Parameter) to StructDataField("var2", Datatype.Integer, -1, 1),
                CompositeDataTypeField("var", Datatype.Integer, FieldAnnotation.LocalVariable) to StructDataField("var", Datatype.Integer, 0, 1),
            )
        )

        assertEquals(
            listOf(
                "-2: result: integer",
                "-1: var2: integer",
                "0: var: integer",
            ),
            built.layout.getDescription(),
        )
    }
}