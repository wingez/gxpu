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


        var symbolTable = builtInSymbolTable()

        var result = compileFunctionBody(
            shouldHave.asFunction().body,
            definitionFromFunctionNode(shouldHave, "dummyfile", symbolTable),
            symbolTable, VariableType.Local, emptyList(),
        ).first()


        assertEquals(
            1,
            symbolTable.getVariablesForFunction(result.definition).size
        )


        symbolTable = builtInSymbolTable()

        result = compileFunctionBody(
            shouldNotHave.asFunction().body,
            definitionFromFunctionNode(shouldNotHave, "dummyfile", symbolTable),
            symbolTable, VariableType.Local, emptyList(),
        ).first()

        assertEquals(
            0,
            symbolTable.getVariablesForFunction(result.definition).size
        )
    }
}