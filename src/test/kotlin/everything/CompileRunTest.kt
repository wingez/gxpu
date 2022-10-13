package se.wingez.everything

import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.AstParser
import se.wingez.bytes
import se.wingez.compiler.CompileError
import se.wingez.compiler.Compiler
import se.wingez.compiler.DummyBuiltInProvider
import se.wingez.compiler.buildSingleMainFunction
import se.wingez.emulator.DefaultEmulator
import se.wingez.emulator.EmulatorCyclesExceeded
import se.wingez.tokens.TokenEndBlock
import se.wingez.tokens.parseFile
import java.io.StringReader
import kotlin.test.assertEquals


fun runBodyCheckOutput(program: String, vararg result: Int) {
    val tokens = parseFile(StringReader(program))
    val nodes = AstParser(tokens + listOf(TokenEndBlock)).parseStatementsUntilEndblock()

    val code = buildSingleMainFunction(nodes)
    val emulator = DefaultEmulator()
    emulator.setAllMemory(code)
    emulator.run()

    assertIterableEquals(bytes(*result), emulator.outputStream)
}

fun runProgramCheckOutput(program: String, vararg result: Int) {
    val tokens = parseFile(StringReader(program))
    val nodes = AstParser(tokens).parse()

    val c = Compiler(DummyBuiltInProvider(), nodes)
    val generator = c.buildProgram()

    val emulator = DefaultEmulator()
    emulator.setAllMemory(generator.code)
    emulator.run()

    assertEquals(bytes(*result), emulator.outputStream)
}

class CompileRunTest {

    @Test
    fun testPrintConstant() {
        val code = """
        print(5)
    """
        runBodyCheckOutput(code, 5)
    }

    @Test
    fun testPrintVariable() {
        val code = """
            val var:byte=5
            print(var)
        """
        runBodyCheckOutput(code, 5)
    }

    @Test
    fun testPrintManyVariables() {
        val code = """
            val var:byte=5
            val var1:byte=10
            print(var)
            print(var1)
        """
        runBodyCheckOutput(code, 5, 10)
    }

    @Test
    fun testReassignVariable() {
        val code = """
            val var:byte=5
            print(var)
            var=3
            print(var)
        """
        runBodyCheckOutput(code, 5, 3)
    }

    @Test
    fun testVariableIncrement() {
        val code = """
            val var:byte=5
            print(var)
            var= var+1
            print(var)
        """
        runBodyCheckOutput(code, 5, 6)
    }

    @Test
    fun testVariableMove() {
        val code = """
            val var1:byte=2
            val var2:byte = var1
            var1 = 1
            print(var2)
            print(var1)
        """
        runBodyCheckOutput(code, 2, 1)
    }

    @Test
    @Disabled
    fun testInvalidVariableName() {
        val code = """
            var=5
            var1 = var2
            print(var)
        """
        assertThrows<CompileError> {
            runBodyCheckOutput(code, 5)
        }
    }

    @Test
    fun testWhileInfinity() {
        val code = """
            while 1:
              print(5)
            
        """
        assertThrows<EmulatorCyclesExceeded> {
            runBodyCheckOutput(code)
        }
    }

    @Test
    fun testWhileDecrement() {
        val code = """
        val var:byte = 5
        while var:
          print(var)
          var=var-1
        """
        runBodyCheckOutput(code, 5, 4, 3, 2, 1)
    }

    @Test
    fun testNested() {
        var body = """
        if 10:
          print(1)
          if 0:
            print(0)
          print(2)
        """
        runBodyCheckOutput(body, 1, 2)

        body = """
        val a:byte=3
        while a:
          if (a-2):
            print(8)
          print(a)
          a=a-1
        
        """
        runBodyCheckOutput(body, 8, 3, 2, 8, 1)
    }
}

