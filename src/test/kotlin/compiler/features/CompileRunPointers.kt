package se.wingez.compiler.features

import compiler.features.CompilerBackend
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class CompileRunPointers {

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled
    fun testCallWithPointer(compiler: CompilerBackend) {
        val program = """
          struct type:
            val1:byte
            val2:byte
           
          def toCall(param:type):
            print(param->val1)
            print(param->val2)
            
          
          def main():
            a:new type
            
            a.val1=11
            a.val2=13
            
            toCall(a)
                 
    """
        runProgramCheckOutput(compiler, program, 11, 13)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled
    fun testEditPointerFromCallee(compiler: CompilerBackend) {
        val program = """
          struct type:
            val1:byte
            val2:byte
           
          def toCall(param:type):
            param->val1 = 5
          
          def main():
            a:new type
            
            a.val1=11
            a.val2=13
            
            toCall(a)
            print(a.val1)
            print(a.val2)
            
            
                 
    """
        runProgramCheckOutput(compiler, program, 5, 13)
    }


}