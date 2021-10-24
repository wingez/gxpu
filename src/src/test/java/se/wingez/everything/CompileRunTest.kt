package se.wingez.everything

import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import se.wingez.TokenEndBlock
import se.wingez.ast.AstParser
import se.wingez.bytes
import se.wingez.compiler.buildSingleMainFunction
import se.wingez.emulator.DefaultEmulator
import se.wingez.parseFile
import java.io.StringReader


fun runBodyCheckOutput(program: String, vararg result: Int) {
    val tokens = parseFile(StringReader(program))
    val nodes = AstParser(tokens + listOf(TokenEndBlock)).parseStatementsUntilEndblock()

    val code = buildSingleMainFunction(nodes)
    val emulator = DefaultEmulator()
    emulator.setAllMemory(code)
    emulator.run()

    assertIterableEquals(bytes(*result), emulator.outputStream)
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
            var=5
            print(var)
        """
        runBodyCheckOutput(code, 5)
    }
}

