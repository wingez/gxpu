package compiler.features.programs

import compiler.features.CompilerBackend
import compiler.features.intMatcher
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Primes {
    @ParameterizedTest
    @EnumSource
    fun testPrimes(compiler: CompilerBackend) {

        Assumptions.assumeTrue(compiler!=CompilerBackend.Emulator)

        val program = """
          def main():
            
            val max_number = 30
            val number = 2
            while number < max_number:
              val factor = 2
              val is_prime = 1
              while factor < number:
                if mod(number, factor) == 0:
                  is_prime = 0
                  break
                factor = factor+1
              
              if is_prime>0:
                print(number)
               
              number = number+1
    """.trimIndent()
        runProgramCheckOutput(compiler,program, intMatcher(2, 3, 5, 7, 11, 13, 17, 19, 23, 29 ))
    }
}