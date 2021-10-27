package se.wingez.everything

import org.junit.jupiter.api.Test

class CompileRunCall {


    @Test
    fun testCallNoParameters() {
        val program = """
          def test1():
            print(5)
            
          def test2():
            print(10)
            
          def main():
            test1()
            test2()
            print(3)
    """

        runProgramCheckOutput(program, 5, 10, 3)
    }

    @Test
    fun testCallSingleParameter() {
        val program = """
          def test(val):
            print(val)
          
          def main():
            test(5)
            print(1)
          """

        runProgramCheckOutput(program, 5, 1)
    }

    @Test
    fun testCallManyParameters() {
        val program = """
            def test(param1,param2):
              print(param2+1)
              print(param1)
              
            def main():
              print(5)
              test(10,6)
              print(7)
            
    """

        runProgramCheckOutput(program, 5, 7, 10, 7)
    }

    @Test
    fun testCallManyVariables() {
        val program = """
            def test(arg1,arg2,arg3):
              v = arg1+arg2
              print(v+arg3)
              
            def main():
              var = 2
              test(1,var,3)
              test(1,var,var) 
            
    """
        runProgramCheckOutput(program, 6, 5)
    }
}