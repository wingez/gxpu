package se.wingez.astwalker

import org.junit.jupiter.api.Test
import se.wingez.ast.parserFromFile
import kotlin.test.assertEquals


internal class WalkPrograms {

    @Test
    fun testFibonacci() {
        val program = """
          def main():
            a=1
            b=0
            c=0
    
            counter=0
            while (10-counter)!=0:
              print(a)
              c=a+b
              b=a
              a=c
              
              counter = counter+1 
                  
    """
        val nodes = parserFromFile(program).parse()

        assertEquals(listOf(1, 1, 2, 3, 5, 8, 13, 21, 34, 55).map { it.toString() }, walk(nodes).result)
    }

    @Test
    fun testStringCopy() {
        val program = """
          def main():
            a = "abcd"
            b:int[] = createArray(a.size)
            
            index = 0
            while index != a.size:
              letter = a[index]
              letter = letter+1
              b[index] = letter
              index = index+1
            
            print(a)
            print(b)
    """
        val nodes = parserFromFile(program).parse()

        assertEquals(listOf("abcd", "bcde"), walk(nodes).result)
    }

    @Test
    fun testPrimes() {

        val program = """
          def main():
            
            max_number = 30
            number = 2
            while number < max_number:
              factor = 2
              is_prime = 1
              while factor < number:
                if mod(number, factor) == 0:
                  is_prime = 0
                  break
                factor = factor+1
              
              if is_prime>0:
                print(number)
               
              number = number+1
    """.trimIndent()
        val nodes = parserFromFile(program).parse()

        assertEquals(listOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29).map { it.toString() }, walk(nodes).result)
    }

    @Test
    fun formatInt() {
        val program = """
          def array_slice(arr:int[], from:int, to:int):int[]
            
            size = to-from
            result=createArray(size)
            
            counter =0
            while counter<size:
              result[counter] = arr[from + counter]
              counter = counter+1
          
          def format_int(value:int):int[]
            
            if value ==0:
              result = "0"
              return
            
            
            max_size = 10
            result_size = 0
            
            result = createArray(max_size)
            
            while value>0:
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
        val nodes = parserFromFile(program).parse()

        assertEquals(listOf(0,1,10,18,19,207).map { it.toString() }, walk(nodes).result)
    }

}