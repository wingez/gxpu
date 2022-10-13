package se.wingez.everything

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class CompileRunStructs {

    @Test
    fun testSingleStructVariable() {
        val program = """
    
          struct type1:
            member1:byte
            member2:byte
          
          def main():
            val a:new type1
            
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
            val a:new type1
            
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
            child: new children
          
          def main():
            val a:new parent
            
            a.parent1=1
            (a.child).child1=2
            (a.child).child2=3
            
            
            print(a.parent1)
            print((a.child).child1)
            print((a.child).child2)
     
    """
        runProgramCheckOutput(program, 1, 2, 3)
    }

    @Test
    @Disabled
    fun testCreateArray() {
        val program = """
            
          def main():
            arr:byte[]
            arr2:byte[]
            arr = createArray(8)
            arr2 = createArray(2)
            
            print(arr->size)
            print(arr2->size)
            
        """
        runProgramCheckOutput(program, 8, 2)
    }

    @Test
    @Disabled
    fun testWriteArray() {
        val program = """
          def main():
            arr:byte[]
            arr = createArray(8)
            
            arr[0] = 8
            arr[1] = 2
            
            print(arr->size)
            print(arr[1])
            print(arr[0])
            
        """
        runProgramCheckOutput(program, 8, 2, 8)
    }

}