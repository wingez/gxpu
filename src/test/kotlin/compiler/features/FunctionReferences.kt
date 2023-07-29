package compiler.features

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource


class FunctionReferences {
    @ParameterizedTest
    @EnumSource
    fun testInvokeFunctionReference(backend: CompilerBackend) {

        val body = """
            print.invoke(5)
        """.trimIndent()

        runBodyCheckOutput(backend, body, intMatcher(5))

    }

}