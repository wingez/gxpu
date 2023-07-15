package compiler.features

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Pointers {
    @ParameterizedTest
    @EnumSource
    fun readPointer(compiler: CompilerBackend) {

        val body = """
            val i=5
            
            
            val p=&i
            print(6)
            print(*p)
            
        """.trimIndent()

        runBodyCheckOutput(compiler, body,intMatcher(6, 5))
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

        runBodyCheckOutput(compiler, body, intMatcher(5, 10))
    }

    @ParameterizedTest
    @EnumSource
    fun pointerAsParameter(compiler: CompilerBackend) {

        val body = """
            def modify(ptr:*int, v:int):
              *ptr=v
            
            def main():
              val i=5  
              print(i)
              modify(&i,6)
              print(i)
            
        """.trimIndent()

        runProgramCheckOutput(compiler, body, intMatcher(5, 6))
    }


}