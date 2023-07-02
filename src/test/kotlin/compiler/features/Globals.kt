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
        runProgramCheckOutput(compiler, code, 0)
    }

    @ParameterizedTest
    @EnumSource
    fun testInitializeGlobal(compiler: CompilerBackend) {

        val code = """
           val i=5
            
           def main():
             print(i)
        """.trimIndent()
        runProgramCheckOutput(compiler, code, 5)
    }

    @ParameterizedTest
    @EnumSource
    fun testGlobalInitializeCall(compiler: CompilerBackend) {

        val code = """
           val i=callme()
           
           def callme():int
             result= 6
           
           def main():
             print(i)
        """.trimIndent()
        runProgramCheckOutput(compiler, code, 6)
    }
}