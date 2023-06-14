package compiler.features

import compiler.backendemulator.DummyBuiltInProvider
import compiler.backendemulator.buildSingleMainFunction
import compiler.backends.emulator.Compiler
import compiler.backends.emulator.emulator.DefaultEmulator
import org.junit.jupiter.api.Assertions
import se.wingez.TokenEndBlock
import se.wingez.ast.AstParser
import se.wingez.ast.parserFromFile
import se.wingez.compiler.backends.astwalker.walk
import se.wingez.compiler.backends.emulator.EmulatorInstruction
import se.wingez.tokens.parseFile
import java.io.StringReader


enum class CompilerBackend {
    Emulator,
    Walker,
}

private interface Runner {

    fun runBody(body: String): List<String>
    fun runProgram(program: String): List<String>

}

private class EmulatorRunner : Runner {
    override fun runBody(body: String): List<String> {
        val tokens = parseFile(StringReader(body))
        val nodes = AstParser(tokens + listOf(TokenEndBlock)).parseStatementsUntilEndblock()

        val code = buildSingleMainFunction(nodes)
        return run(code.instructions)
    }

    override fun runProgram(program: String): List<String> {
        val tokens = parseFile(StringReader(program))
        val nodes = AstParser(tokens).parse()

        val c = Compiler(DummyBuiltInProvider(), nodes)
        val generator = c.buildProgram()

        return run(generator.instructions)
    }

    private fun run(instructions: List<EmulatorInstruction>): List<String> {
        val emulator = DefaultEmulator()
        emulator.setProgram(instructions)
        emulator.run()

        return emulator.outputStream.map { it.toString() }
    }
}

private class WalkerRunner : Runner {
    override fun runBody(body: String): List<String> {
        val bodyTrimmed = body.trimIndent()

        val bodyIndented = bodyTrimmed.lines().map { "  $it" }

        val lines = listOf("def main():") + bodyIndented

        val program = lines.joinToString("\n")

        return runProgram(program)
    }

    override fun runProgram(program: String): List<String> {
        val nodes = parserFromFile(program).parse()
        return walk(nodes).result
    }
}

private class LlvmRunner : Runner {
    override fun runBody(body: String): List<String> {
        TODO("Not yet implemented")
    }

    override fun runProgram(program: String): List<String> {
        TODO("Not yet implemented")
    }

}

private fun getRunner(type: CompilerBackend): Runner {
    return when (type) {
        CompilerBackend.Emulator -> EmulatorRunner()
        CompilerBackend.Walker -> WalkerRunner()
    }
}


fun runBodyCheckOutput(type: CompilerBackend, program: String, vararg result: Any) {

    val expected = result.map(Any::toString)

    val actual = getRunner(type).runBody(program)

    Assertions.assertIterableEquals(expected, actual)
}

fun runProgramCheckOutput(type: CompilerBackend, program: String, vararg result: Any) {

    val expected = result.map { it.toString() }

    val actual = getRunner(type).runProgram(program)

    Assertions.assertIterableEquals(expected, actual)
}

