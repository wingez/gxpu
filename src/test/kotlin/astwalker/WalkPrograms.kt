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

}