package se.wingez.compiler.features

import compiler.features.CompilerBackend
import compiler.features.runBodyCheckOutput
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class CompileRunArithmetic {


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testAddition(compiler: CompilerBackend) {
        val code = """
          print(5+10) 
        """
        runBodyCheckOutput(compiler, code, 15)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testSubtraction(compiler: CompilerBackend) {
        val code = """
          val var:byte =5
          print(8-var)
           
        """
        runBodyCheckOutput(compiler, code, 3)

    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testLessThan(compiler: CompilerBackend) {
        val code = """
          def printBool(b:bool):
            if b:
              print(1)
            else:
              print(0)
          
          def main():
            printBool(1<2)
            printBool(2<1)
            printBool(1<1)
        """
        runProgramCheckOutput(compiler, code, 1, 0, 0)

    }

}