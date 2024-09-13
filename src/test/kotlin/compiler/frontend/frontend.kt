package compiler.frontend

import org.junit.jupiter.api.Test
import ast.FunctionType
import ast.parserFromFile
import compiler.BuiltInSignatures
import compiler.builtInSymbolTable
import kotlin.test.assertEquals

internal class FrontendTest {

    @Test
    fun functionShouldOnlyHaveReturnVariableIfNotReturnVoid() {

        val shouldHave = """
         def main():int
           return 5
     """.trimIndent().let { parserFromFile(it).parseFunctionDefinition() }


        val shouldNotHave = """
         def main():
           print(5)
     """.trimIndent().let { parserFromFile(it).parseFunctionDefinition() }


        val symbolTable = builtInSymbolTable()


        assertEquals(
            1,
            compileFunctionBody(
                shouldHave.asFunction().body,
                definitionFromFunctionNode(shouldHave, "dummyfile", symbolTable),
                symbolTable, "", VariableType.Local,
            ).first().fields.compositeFields.size
        )


        assertEquals(
            0,
            compileFunctionBody(
                shouldNotHave.asFunction().body,
                definitionFromFunctionNode(shouldNotHave, "dummyfile", symbolTable),
                symbolTable, "", VariableType.Local,
            ).first().fields.compositeFields.size
        )
    }
}