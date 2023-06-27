package compiler.features

import compiler.features.CompilerBackend
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Structs {

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testSingleStructVariable(compiler: CompilerBackend) {

        val program = """
    
          struct type1:
            member1:int
            member2:int
          
          def main():
            val a:type1
            
            a.member2=5
            a.member1=2
            
            print(a.member1)
            print(a.member2)
     
    """
        runProgramCheckOutput(compiler, program, 2, 5)
    }


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testSingleStructVariableArithmetic(compiler: CompilerBackend) {

        val program = """
    
          struct type1:
            member1:int
            member2:int
          
          def main():
            val a:type1
            
            a.member2=5
            a.member1=2
            
            print((a.member1)+2)
            print((a.member2)+(a.member1))
     
    """
        runProgramCheckOutput(compiler, program, 4, 7)
    }


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testStructNested(compiler: CompilerBackend) {
        val program = """
          struct children:
            child1:int
            child2:int
            
          struct parent:
            parent1:int
            child:children
          
          def main():
            val a:parent
            
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
    @EnumSource()
    fun testStructPointer(compiler: CompilerBackend) {
        val program = """
          struct s:
            a:int
            b:int
            
          
          def main():
            val x:s
            x.a=5
            x.b=6
            
            val p = &x
            
            p->a=3
            
            print(p->a)
            print(p->b)
    """
        runProgramCheckOutput(compiler, program, 3, 6)
    }
    @ParameterizedTest
    @EnumSource
    @Disabled
    fun testStructResult(compiler: CompilerBackend) {
        Assumptions.assumeTrue(compiler != CompilerBackend.Emulator)

        val program = """
          def f():intpair
            result.first=4
            result.second=5
          def main():
            val x = f()
           
            print(x.first)
            print(x.second)
    """
        runProgramCheckOutput(compiler, program, 4, 5)
    }
}