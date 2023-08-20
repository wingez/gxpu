package compiler.features

import compiler.frontend.FrontendCompilerError
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Modules {


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testModules(compilerBackend: CompilerBackend) {
        val files = mapOf(
            "main" to """
            import other
            
            def main():
              callme()
        """.trimIndent(),
            "other" to """
            def callme():
              print(4)
        """.trimIndent()
        )

        runProgramCheckOutput(compilerBackend, files, "main", intMatcher(4))

    }


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testModulesGlobals(compilerBackend: CompilerBackend) {
        val files = mapOf(
            "main" to """
            import other
            val i=6
            
            def main():
              callme()
              print(i)
        """.trimIndent(),
            "other" to """
            val j=7
            def callme():
              print(j)
        """.trimIndent()
        )

        runProgramCheckOutput(compilerBackend, files, "main", intMatcher(7, 6))
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testNeverImportGlobal(compilerBackend: CompilerBackend) {
        val files = mapOf(
            "main" to """
            import other
            
            def main():
              print(j)
        """.trimIndent(),
            "other" to """
            val j=7
        """.trimIndent()
        )

        assertThrows<FrontendCompilerError> {
            runProgramCheckOutput(compilerBackend, files, "main", intMatcher(7, 7))
        }
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testGlobalsInitializeInModules(compilerBackend: CompilerBackend) {
        val files = mapOf(
            "main" to """
            import other1
            import other2
            
            def main():
              print1()
              print2()
        """.trimIndent(),
            "other1" to """
            val j=7
            def print1():
              print(j)
        """.trimIndent(), "other2" to """
            val k=5
            def print2():
              print(k)
        """.trimIndent()
        )

        runProgramCheckOutput(compilerBackend, files, "main", intMatcher(7, 5))
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testGlobalSameName(compilerBackend: CompilerBackend) {
        val files = mapOf(
            "main" to """
            import other1
            import other2
            
            def main():
              print1()
              print2()
        """.trimIndent(),
            "other1" to """
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