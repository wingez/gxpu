package compiler.features

import compiler.features.CompilerBackend
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Structs {

    @ParameterizedTest
    @EnumSource
    @Disabled
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
        runProgramCheckOutput(compiler, program, intMatcher(3, 6))
    }
    @ParameterizedTest
    @EnumSource
    @Disabled
    fun testStructResult(compiler: CompilerBackend) {
        val program = """
          def f():intpair
            result.first=4
            result.second=5
          def main():
            val x = f()
           
            print(x.first)
            print(x.second)
    """
        runProgramCheckOutput(compiler, program, intMatcher(4, 5))
    }
}