package se.wingez.compiler.features.programs

import compiler.features.CompilerBackend
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Multiply {

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun multiply(compiler: CompilerBackend) {
        val program = """
          def mul(a:int,b:int):int
            result=0
            while bool(b):
              result=result+a
              b=b-1
          
          def main():
            print(mul(0,10))
            print(mul(4,5))
            print(mul(10,0))
          """
        runProgramCheckOutput(compiler, program, 0, 20, 0)
    }

}