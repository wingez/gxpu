package compiler.features.programs

import compiler.features.CompilerBackend
import compiler.features.intMatcher
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Fibonacci {

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun fibonacci(compiler: CompilerBackend) {
        val program = """
          def main():
            val a:byte=1
            val b:byte=0
            val c:byte=0
    
            val counter:byte=0
            while bool(10-counter):
              print(a)
              c=a+b
              b=a
              a=c
              
              counter = counter+1 
                  
    """
        runProgramCheckOutput(compiler, program, intMatcher(1, 1, 2, 3, 5, 8, 13, 21, 34, 55))
    }
}