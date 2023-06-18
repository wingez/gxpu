package se.wingez.compiler.features

import compiler.features.CompilerBackend
import compiler.features.runBodyCheckOutput
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
}