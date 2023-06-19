package compiler.frontend

import org.junit.jupiter.api.Test
import ast.FunctionType
import ast.parserFromFile
import compiler.dummyTypeContainer
import kotlin.test.assertEquals

val emptyFunctions = object : FunctionDefinitionResolver {
    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition {
        if (name=="print"){
            return FunctionDefinition("print", listOf(Datatype.Integer), Datatype.Void, FunctionType.Normal)
        }

        TODO("Not yet implemented")
    }

}

internal class FrontendTest {

    @Test
    fun functionShouldOnlyHaveReturnVariableIfNotReturnVoid() {

        val shouldHave = """
         def main():integer
           return 5
     """.trimIndent()


        val shouldNotHave = """
         def main():
           print(5)
     """.trimIndent()

        assertEquals(
            1,
            compileFunction(
                parserFromFile(shouldHave).parse().first(),
                emptyFunctions,
                dummyTypeContainer
            ).localVariables.size
        )
        assertEquals(
            0,
            compileFunction(
                parserFromFile(shouldNotHave).parse().first(),
                emptyFunctions,
                dummyTypeContainer
            ).localVariables.size
        )
    }
}