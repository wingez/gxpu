package se.wingez.everything

import org.junit.jupiter.api.Test

class CompileRunPointers {

    @Test
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

}