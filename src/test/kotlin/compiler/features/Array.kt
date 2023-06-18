package compiler.features

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource


class Array {

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
}