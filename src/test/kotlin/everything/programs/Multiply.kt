package se.wingez.everything.programs

import org.junit.jupiter.api.Test
import se.wingez.everything.runProgramCheckOutput

class Multiply {

    @Test
    fun multiply() {
        val program = """
          def mul(a:byte,b:byte):byte
            result:byte=0
            while b!=0:
              result=result+a
              b=b-1
          
          def main():
            print(mul(0,10))
            print(mul(4,5))
            print(mul(10,0))
          """
        runProgramCheckOutput(program, 0, 20, 0)
    }

}