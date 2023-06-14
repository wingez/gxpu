package se.wingez.compiler.features.programs

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import se.wingez.compiler.features.runProgramCheckOutput


class ArrayTests {

    @Test
    @Disabled
    fun testPrintArray() {
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
        runProgramCheckOutput(program, 5, 10, 15)
    }

    @Test
    @Disabled
    fun testEditArrayInFunction() {
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
        runProgramCheckOutput(program, 100)
    }
}