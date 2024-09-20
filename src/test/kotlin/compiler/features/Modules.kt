package compiler.features

import compiler.frontend.FrontendCompilerError
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Modules {


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled
    fun testGlobalSameName(compilerBackend: CompilerBackend) {
        val files = mapOf(
            "main" to """
            import other1
            import other2
            
            def main():
              print1()
              print2()
              
        """.trimIndent(), "other1" to """
                
            val j=7
            def print1():
              print(j)
              
              
        """.trimIndent(), "other2" to """
            val j=5
            def print2():
              print(j)
        """.trimIndent()
        )
        runProgramCheckOutput(compilerBackend, files, "main", intMatcher(7, 5))
    }
}