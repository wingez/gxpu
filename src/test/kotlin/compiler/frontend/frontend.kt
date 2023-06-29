package compiler.frontend

import org.junit.jupiter.api.Test
import ast.FunctionType
import ast.parserFromFile
import compiler.backendemulator.dummyTypeContainer
import kotlin.test.assertEquals

val emptyFunctions = object : FunctionSignatureResolver {
    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionSignature {
        if (name == "print") {
            return FunctionSignature("print", listOf(Datatype.Integer), Datatype.Void, FunctionType.Normal)
        }

        TODO("Not yet implemented")
    }

}

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


        assertEquals(
            1,
            compileFunctionBody(
                shouldHave.asFunction().body,
                definitionFromFunctionNode(shouldHave, dummyTypeContainer),
                emptyList(),
                emptyFunctions,
                dummyTypeContainer
            ).fields.compositeFields.size
        )
        assertEquals(
            0,
            compileFunctionBody(
                shouldNotHave.asFunction().body,
                definitionFromFunctionNode(shouldNotHave, dummyTypeContainer),
                emptyList(),
                emptyFunctions,
                dummyTypeContainer
            ).fields.compositeFields.size
        )
    }
}