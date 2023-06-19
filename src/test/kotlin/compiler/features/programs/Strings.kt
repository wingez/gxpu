package compiler.features.programs

import compiler.features.CompilerBackend
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Strings {

    @ParameterizedTest
    @EnumSource
    fun testStringCopy(compiler: CompilerBackend) {

        Assumptions.assumeTrue(compiler != CompilerBackend.Emulator)

        val program = """
          def main():
            val a = "abcd"
            val b:int[] = createArray(a.size())
            
            val index = 0
            while index != a.size():
              val letter = a[index]
              letter = letter+1
              b[index] = letter
              index = index+1
            
            print(a)
            print(b)
    """
        runProgramCheckOutput(compiler, program, "abcd", "bcde")
    }


    @ParameterizedTest
    @EnumSource
    fun formatInt(compiler: CompilerBackend) {

        Assumptions.assumeTrue(compiler!=CompilerBackend.Emulator)

        val program = """
          def array_slice(arr:*int[], from:int, to:int):*int[]
            
            val size = to-from
            result=createArray(size)
            
            val counter =0
            while counter<size:
              result[counter] = arr[from + counter]
              counter = counter+1
          
          def format_int(value:int):*int[]
            
            if value ==0:
              result = "0"
              return
            
            
            val max_size = 10
            val result_size = 0
            
            result = createArray(max_size)
            
            while bool(value):
              result[max_size-1-result_size] = 48+ mod(value,10)
              value = idiv(value,10)
              result_size = result_size +1
            
            
            result = array_slice(result,max_size - result_size, max_size)
            
            
          def main():
            print(format_int(0))
            print(format_int(1))
            print(format_int(10))
            print(format_int(18))
            print(format_int(19))
            print(format_int(207))
            
            
            
    """.trimIndent()

        runProgramCheckOutput(compiler, program, 0, 1, 10, 18, 19, 207)
    }
}