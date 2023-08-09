package compiler.features

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test

class Modules {


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testModules(compilerBackend: CompilerBackend){
        val files = mapOf("main" to """
            import other
            
            def main():
              callme()
        """.trimIndent(),
        "other" to """
            def callme():
              print(4)
        """.trimIndent())

        runProgramCheckOutput(compilerBackend, files,"main", intMatcher(4))

    }


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testModulesGlobals(compilerBackend: CompilerBackend){
        val files = mapOf("main" to """
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
        """.trimIndent())

        runProgramCheckOutput(compilerBackend, files,"main", intMatcher(7,6))
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled
    fun testImportGlobal(compilerBackend: CompilerBackend){
        val files = mapOf("main" to """
            import other
            
            def main():
              callme()
              print(j)
        """.trimIndent(),
            "other" to """
            val j=7
            def callme():
              print(j)
        """.trimIndent())

        runProgramCheckOutput(compilerBackend, files,"main", intMatcher(7,7))
    }



}