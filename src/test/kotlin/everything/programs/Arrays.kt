package se.wingez.everything.programs

import org.junit.jupiter.api.Test
import se.wingez.everything.runProgramCheckOutput


class ArrayTests {

    @Test
    fun testPrintArray() {
        val program = """
          def printArray(arr:byte[]):
            counter=0
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