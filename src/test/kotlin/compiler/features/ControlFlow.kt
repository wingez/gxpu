package compiler.features

import org.junit.jupiter.api.assertThrows
import compiler.features.CompilerBackend
import compiler.features.runBodyCheckOutput
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import compiler.backends.astwalker.WalkerException
import compiler.frontend.FrontendCompilerError
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Disabled


class ControlFlow {


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled
    fun testInvalidVariableName(compiler: CompilerBackend) {
        val code = """
            val var=5
            var1 = var2
            print(var)
        """
        assertThrows<FrontendCompilerError> {
            runBodyCheckOutput(compiler, code, intMatcher(5))
        }
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testWhileInfinity(compiler: CompilerBackend) {

        Assumptions.assumeTrue(compiler == CompilerBackend.Walker)

        val code = """
            while bool(1):
              print(5)
            
        """

        assertThrows<WalkerException> {
            runBodyCheckOutput(compiler, code, intMatcher())
        }
    }

}

