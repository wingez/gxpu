package compiler.features

import compiler.features.CompilerBackend
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Structs {

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled

    fun testSingleStructVariable(compiler: CompilerBackend) {
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
        runProgramCheckOutput(compiler, program, 2, 5)
    }


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled

    fun testSingleStructVariableArithmetic(compiler: CompilerBackend) {
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
        runProgramCheckOutput(compiler, program, 4, 7)
    }


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled

    fun testStructNested(compiler: CompilerBackend) {
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
        runProgramCheckOutput(compiler, program, 1, 2, 3)
    }


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled
    fun testCreateArray(compiler: CompilerBackend) {
        val program = """
            
          def main():
            arr:byte[]
            arr2:byte[]
            arr = createArray(8)
            arr2 = createArray(2)
            
            print(arr->size)
            print(arr2->size)
            
        """
        runProgramCheckOutput(compiler, program, 8, 2)
    }


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled
    fun testWriteArray(compiler: CompilerBackend) {
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
        runProgramCheckOutput(compiler, program, 8, 2, 8)
    }

}