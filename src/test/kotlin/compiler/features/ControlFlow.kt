package compiler.features

import org.junit.jupiter.api.assertThrows
import compiler.backends.emulator.emulator.EmulatorCyclesExceeded
import compiler.features.CompilerBackend
import compiler.features.runBodyCheckOutput
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import compiler.backends.astwalker.WalkerException
import compiler.frontend.FrontendCompilerError


class ControlFlow {
    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testPrintConstant(compiler: CompilerBackend) {
        val code = """
        print(5)
    """
        runBodyCheckOutput(compiler, code, 5)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testPrintVariable(compiler: CompilerBackend) {
        val code = """
            val var:byte=5
            print(var)
        """
        runBodyCheckOutput(compiler, code, 5)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testPrintManyVariables(compiler: CompilerBackend) {
        val code = """
            val var:byte=5
            val var1:byte=10
            print(var)
            print(var1)
        """
        runBodyCheckOutput(compiler, code, 5, 10)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testReassignVariable(compiler: CompilerBackend) {
        val code = """
            val var:byte=5
            print(var)
            var=3
            print(var)
        """
        runBodyCheckOutput(compiler, code, 5, 3)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testVariableIncrement(compiler: CompilerBackend) {
        val code = """
            val var:byte=5
            print(var)
            var= var+1
            print(var)
        """
        runBodyCheckOutput(compiler, code, 5, 6)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testVariableMove(compiler: CompilerBackend) {
        val code = """
            val var1:byte=2
            val var2:byte = var1
            var1 = 1
            print(var2)
            print(var1)
        """
        runBodyCheckOutput(compiler, code, 2, 1)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testInvalidVariableName(compiler: CompilerBackend) {
        val code = """
            val var=5
            var1 = var2
            print(var)
        """
        assertThrows<FrontendCompilerError> {
            runBodyCheckOutput(compiler, code, 5)
        }
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testWhileInfinity(compiler: CompilerBackend) {
        val code = """
            while bool(1):
              print(5)
            
        """
        when (compiler) {
            CompilerBackend.Emulator -> assertThrows<EmulatorCyclesExceeded> {
                runBodyCheckOutput(compiler, code)
            }

            CompilerBackend.Walker -> assertThrows<WalkerException> {
                runBodyCheckOutput(compiler, code)
            }

            else -> TODO()
        }
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testWhileDecrement(compiler: CompilerBackend) {
        val code = """
        val var:byte = 5
        while bool(var):
          print(var)
          var=var-1
        """
        runBodyCheckOutput(compiler, code, 5, 4, 3, 2, 1)
    }

    @ParameterizedTest
    @EnumSource(CompilerBackend::class)
    fun testNested(compiler: CompilerBackend) {
        var body = """
        if bool(10):
          print(1)
          if bool(0):
            print(0)
          print(2)
        """
        runBodyCheckOutput(compiler, body, 1, 2)

        body = """
        val a:byte=3
        while bool(a):
          if bool(a-2):
            print(8)
          print(a)
          a=a-1
        
        """
        runBodyCheckOutput(compiler, body, 8, 3, 2, 8, 1)
    }
}

