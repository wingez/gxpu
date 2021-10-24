package se.wingez.everything

import org.junit.jupiter.api.Test

class CompileRunArithmetic {


    @Test
    fun testAddition() {
        var code = """
          print(5+10) 
        """

        runBodyCheckOutput(code, 15)
    }
}