package se.wingez.compiler.features

import compiler.features.CompilerBackend
import compiler.features.runBodyCheckOutput
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Pointers {
    @ParameterizedTest
    @EnumSource
    fun readPointer(compiler: CompilerBackend) {

        val body = """
            val i=5
            
            val p=&i
            
            print(*p)
            
        """.trimIndent()

        runBodyCheckOutput(compiler, body, 5)
    }

    @ParameterizedTest
    @EnumSource
    fun writeToPointer(compiler: CompilerBackend) {

        val body = """
            val i=5
            
            val p=&i
            
            print(i)
            *p = 10
            print(i)
            
        """.trimIndent()

        runBodyCheckOutput(compiler, body, 5, 10)
    }

}