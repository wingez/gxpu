package se.wingez.compiler.features.programs

import compiler.features.CompilerBackend
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource


class ArrayTests {

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled
    fun testPrintArray(compiler: CompilerBackend) {
        val program = """
          def printArray(arr:byte[]):
            counter:byte=0
            while (counter) != (arr -> size):
              print(arr[counter])
              counter = counter+1
            
            
            
          def main():
            arr:byte[]
            arr = createArray(3)
            arr[0]=5
            arr[1]=10
            arr[2]=15
            
            printArray(arr)
    """
        runProgramCheckOutput(compiler, program, 5, 10, 15)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    @Disabled
    fun testEditArrayInFunction(compiler: CompilerBackend) {
        val program = """
          def editArray(arr:byte[]):
            arr[0]=100
            
          def main():
            arr:byte[]
            arr = createArray(3)
            arr[0]=5
            
            editArray(arr)
            print(arr[0])
    """
        runProgramCheckOutput(compiler, program, 100)
    }
}