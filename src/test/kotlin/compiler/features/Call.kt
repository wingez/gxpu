package se.wingez.compiler.features

import compiler.features.CompilerBackend
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Call {


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testCallNoParameters(compiler: CompilerBackend) {
        val program = """
          def test1():
            print(5)
            
          def test2():
            print(10)
            
          def main():
            test1()
            test2()
            print(3)
    """

        runProgramCheckOutput(compiler, program, 5, 10, 3)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testCallSingleParameter(compiler: CompilerBackend) {
        val program = """
          def test(var:int):
            print(var)
          
          def main():
            test(5)
            print(1)
          """

        runProgramCheckOutput(compiler, program, 5, 1)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testCallManyParameters(compiler: CompilerBackend) {
        val program = """
            def test(param1:int,param2:int):
              print(param2+1)
              print(param1)
              
            def main():
              print(5)
              test(10,6)
              print(7)
            
    """

        runProgramCheckOutput(compiler, program, 5, 7, 10, 7)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testCallManyVariables(compiler: CompilerBackend) {
        val program = """
            def test(arg1:int,arg2:int,arg3:int):
              val v:int = arg1+arg2
              print(v+arg3)
              
            def main():
              val var:int = 2
              test(1,var,3)
              test(1,var,var) 
            
    """
        runProgramCheckOutput(compiler, program, 6, 5)
    }
}