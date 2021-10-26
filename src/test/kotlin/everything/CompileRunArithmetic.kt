package se.wingez.everything

import org.junit.jupiter.api.Test

class CompileRunArithmetic {


    @Test
    fun testAddition() {
        val code = """
          print(5+10) 
        """

        runBodyCheckOutput(code, 15)
    }

    @Test
    fun testSubtraction() {
        val code = """
          var =5
          print(8-var)
           
        """
        runBodyCheckOutput(code, 3)
    }

}