package se.wingez.compiler.features

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class CompileRunPointers {

    @Test
    @Disabled
    fun testCallWithPointer() {
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
        runProgramCheckOutput(program, 11, 13)
    }

    @Test
    @Disabled
    fun testEditPointerFromCallee() {
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
        runProgramCheckOutput(program, 5, 13)
    }


}