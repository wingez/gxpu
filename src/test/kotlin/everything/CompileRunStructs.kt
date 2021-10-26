package se.wingez.everything

import org.junit.jupiter.api.Test

class CompileRunStructs {

    @Test
    fun testSingleStructVariable() {
        val program = """
    
          struct type1:
            member1:byte
            member2:byte
          
          def main():
            a:type1
            
            a.member2=5
            a.member1=2
            
            print(a.member1)
            print(a.member2)
     
    """
        runProgramCheckOutput(program, 2, 5)
    }

    @Test
    fun testSingleStructVariableArithmetic() {
        val program = """
    
          struct type1:
            member1:byte
            member2:byte
          
          def main():
            a:type1
            
            a.member2=5
            a.member1=2
            
            print((a.member1)+2)
            print((a.member2)+(a.member1))
     
    """
        runProgramCheckOutput(program, 4, 7)
    }

    @Test
    fun testStructNested() {
        val program = """
          struct children:
            child1:byte
            child2:byte
            
          struct parent:
            parent1:byte
            child:children
          
          def main():
            a:parent
            
            a.parent1=1
            (a.child).child1=2
            (a.child).child2=3
            
            
            print(a.parent1)
            print((a.child).child1)
            print((a.child).child2)
     
    """
        runProgramCheckOutput(program, 1, 2, 3)
    }

}