package compiler.features

import compiler.BackendCompiler
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource


class Globals {

    @ParameterizedTest
    @EnumSource
    fun testReadGlobal(compiler: CompilerBackend) {

        val code = """
           val i:int
            
           def main():
             print(i)
        """.trimIndent()
        runProgramCheckOutput(compiler, code, 5)

    }

}