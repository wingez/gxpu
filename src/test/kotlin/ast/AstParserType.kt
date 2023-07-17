package ast

import ast.syntaxerror.ParserSyntaxError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class AstParserType {


    @Test
    fun testBasicType() {
        assertEquals(
            TypeDefinition.normal("test", emptyList()),
            parserFromLine("test").parseTypeDefinition()
        )
    }

    @Test
    fun testArray() {
        assertEquals(
            TypeDefinition.normal("test", listOf(TypeDefinitionModifier.Array)),
            parserFromLine("test[]").parseTypeDefinition()
        )
    }

    @Test
    fun testPointer() {
        assertEquals(
            TypeDefinition.normal("test", listOf(TypeDefinitionModifier.Pointer)),
            parserFromLine("*test").parseTypeDefinition()
        )
    }

    @Test
    fun testFunction() {
        assertEquals(
            TypeDefinition(FunctionBase(TypeDefinition.normal("Nothing", emptyList()), emptyList()), emptyList()),
            parserFromLine("()->Nothing").parseTypeDefinition()
        )
    }

    @Test
    fun testWithParametersFunction() {
        assertEquals(
            TypeDefinition(
                FunctionBase(
                    TypeDefinition.normal("Nothing"), listOf(
                        TypeDefinition.normal("Int"), TypeDefinition.normal("Bool")
                    )
                ), emptyList()
            ),
            parserFromLine("(Int,Bool)->Nothing").parseTypeDefinition()
        )

        assertThrows<ParserSyntaxError> {
            parserFromLine("(Int,)->Nothing").parseTypeDefinition()
        }
    }

}