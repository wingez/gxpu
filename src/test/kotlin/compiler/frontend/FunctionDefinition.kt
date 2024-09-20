package compiler.frontend

import compiler.BuiltInSignatures
import compiler.builtInSymbolTable
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionDefinitionTest {


    @Test
    fun testDefinitionDescription(){

        assertEquals("fun print(value: int): void",BuiltInSignatures.print.name )


    }


}