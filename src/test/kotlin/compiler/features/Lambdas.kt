package compiler.features

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class Lambdas {

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testLambda(compilerBackend: CompilerBackend){


        runBodyCheckOutput(compilerBackend, "run({print(5)})", intMatcher(5))
    }

}