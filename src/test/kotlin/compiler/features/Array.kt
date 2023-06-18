package compiler.features

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import se.wingez.ast.parserFromFile
import se.wingez.compiler.backends.astwalker.WalkerException
import se.wingez.compiler.backends.astwalker.walk
import se.wingez.compiler.frontend.FrontendCompilerError


class Array {

    @ParameterizedTest
    @EnumSource
    fun testCreateArrayAndSize(compiler: CompilerBackend) {
        val program = """
          val arr:*int[] = createArray(3)
          
          print(arr.size())
    """
        runBodyCheckOutput(compiler, program, 3)
    }


    @ParameterizedTest
    @EnumSource
    fun testArrayRead(compiler: CompilerBackend) {
        val program = """
          val arr:*int[] = createArray(3)
          
          print(arr[0])
    """
        runBodyCheckOutput(compiler, program, 0)
    }

    @ParameterizedTest
    @EnumSource
    fun testArrayWrite(compiler: CompilerBackend) {
        val program = """
          val arr:*int[] = createArray(3)
          
          print(arr[0])
          print(arr[1])
          arr[0] = 10
          print(arr[0])
          print(arr[1])
          
          
    """
        runBodyCheckOutput(compiler, program, 0, 0, 10, 0)
    }


    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testPrintArray(compiler: CompilerBackend) {
        val program = """
          def printArray(arr:*int[]):
            val counter=0
            while (counter) != arr.size():
              print(arr[counter])
              counter = counter+1
            
            
            
          def main():
            val arr:*byte[] = createArray(3)
            arr[0]=5
            arr[1]=10
            arr[2]=15
            
            printArray(arr)
    """
        runProgramCheckOutput(compiler, program, 5, 10, 15)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testEditArrayInFunction(compiler: CompilerBackend) {
        val program = """
          def editArray(arr:*int[]):
            arr[0]=100
            
          def main():
            val arr:*int[]
            arr = createArray(3)
            arr[0]=5
            
            editArray(arr)
            print(arr[0])
    """
        runProgramCheckOutput(compiler, program, 100)
    }


    @ParameterizedTest
    @EnumSource
    fun testArraySizeReadonly(compiler: CompilerBackend) {
        val program = """
          def main():
            val a:*int[] = createArray(5)
            a.size() = 10
    """
        assertThrows<FrontendCompilerError> { runProgramCheckOutput(compiler, program) }
    }

    @ParameterizedTest
    @EnumSource
    fun testArrayReadOutOfBounds(compiler: CompilerBackend) {

        Assumptions.assumeTrue(compiler != CompilerBackend.Emulator)

        val program = """
          def main():
            val a:int[] = createArray(10)
            print(a.size())
            print(a[10])
            
        """
        assertThrows<WalkerException> { runProgramCheckOutput(compiler, program) }
    }


}