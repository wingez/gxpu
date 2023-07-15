package compiler.features

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class CompileRunArithmetic {


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testAddition(compiler: CompilerBackend) {
        val code = """
          print(5+10) 
        """
        runBodyCheckOutput(compiler, code, intMatcher(15))
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testSubtraction(compiler: CompilerBackend) {
        val code = """
          val var:byte =5
          print(8-var)
           
        """
        runBodyCheckOutput(compiler, code, intMatcher(3))

    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testLessThan(compiler: CompilerBackend) {
        val code = """
            if 1<2:
              print(1)
            else:
              print(0)
           
            if 2<1:
              print(1)
            else:
              print(0)
           
            if 1<1:
              print(1)
            else:
              print(0)
        """
        runBodyCheckOutput(compiler, code, intMatcher(1, 0, 0))

    }

}