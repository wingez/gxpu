package se.wingez.everything.programs

import org.junit.jupiter.api.Test
import se.wingez.everything.runProgramCheckOutput

class Fibonacci {

    @Test
    fun fibonacci() {
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
        runProgramCheckOutput(program, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55)
    }
}