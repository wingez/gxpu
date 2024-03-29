package compiler.frontend

import org.junit.jupiter.api.Test
import ast.FunctionType
import ast.parserFromFile
import compiler.BuiltInSignatures
import compiler.backendemulator.dummyTypeContainer
import kotlin.test.assertEquals

val emptyFunctions = object : FunctionSignatureResolver {
    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition {
        if (name == "print") {
            return BuiltInSignatures.print
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
                definitionFromFunctionNode(shouldHave, "dummyfile", dummyTypeContainer),
                emptyMap(),
                emptyFunctions,
                dummyTypeContainer, "", VariableType.Local,
            ).first().fields.compositeFields.size
        )
        assertEquals(
            0,
            compileFunctionBody(
                shouldNotHave.asFunction().body,
                definitionFromFunctionNode(shouldNotHave, "dummyfile", dummyTypeContainer),
                emptyMap(),
                emptyFunctions,
                dummyTypeContainer, "", VariableType.Local,
            ).first().fields.compositeFields.size
        )
    }
}