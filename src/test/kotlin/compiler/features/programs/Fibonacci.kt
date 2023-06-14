package se.wingez.compiler.features.programs

import org.junit.jupiter.api.Test
import se.wingez.compiler.features.runProgramCheckOutput

class Fibonacci {

    @Test
    fun fibonacci() {
        val program = """
          def main():
            val a:byte=1
            val b:byte=0
            val c:byte=0
    
            val counter:byte=0
            while bool(10-counter):
              print(a)
              c=a+b
              b=a
              a=c
              
              counter = counter+1 
                  
    """
        runProgramCheckOutput(program, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55)
    }
}