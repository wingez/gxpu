package compiler.features

import CompilerError
import SourceProvider
import compiler.backendemulator.DummyBuiltInProvider
import compiler.backendemulator.buildSingleMainFunction
import compiler.backends.emulator.Compiler
import compiler.backends.emulator.emulator.DefaultEmulator
import org.junit.jupiter.api.Assertions
import ast.AstParser
import ast.parserFromFile
import compiler.backends.emulator.EmulatorInstruction
import TokenEndBlock
import compiler.BackendCompiler
import compiler.BuiltInSignatures
import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerRunner
import compiler.backends.emulator.BuiltInFunctions
import compiler.backends.emulator.EmulatorRunner
import compiler.compileAndRunBody
import compiler.compileAndRunProgram
import compiler.frontend.compileFunctionBody
import org.junit.jupiter.api.fail
import tokenizeLines
import java.io.StringReader
import kotlin.test.assertEquals


enum class CompilerBackend {
    Emulator,
    Walker,
}


private class Source(val program: String) : SourceProvider {
    override fun getLine(filename: String, lineNumber: Int): String {
        assertEquals("dummyfile", filename)
        return program.lines()[lineNumber]
    }
}

private class EmulatorRunnerd {
    fun runBody(body: String, printError: Boolean): List<String> {
        TODO()
        val code = try {
            val tokens = tokenizeLines(body)
            val nodes = AstParser(tokens + listOf(TokenEndBlock)).parseStatementsUntilEndblock()

            buildSingleMainFunction(nodes)
        } catch (e: CompilerError) {
            if (printError) {
                fail("Error compiling: ${e.getLine(Source(body))}")
            }
            throw e
        }
        return run(code.instructions)
    }

    fun runProgram(program: String, printError: Boolean): List<String> {

        val runner =

            compileAndRunProgram(
                StringReader(program),
                "dummyfile",
                compiler.backends.emulator.EmulatorRunner(BuiltInFunctions()),
                BuiltInSignatures()
            )

        val generator = try {
            val tokens = tokenizeLines(program)
            val nodes = AstParser(tokens).parse()

            //val c = Compiler(DummyBuiltInProvider(), nodes)
            //c.buildProgram()
        } catch (e: CompilerError) {
            if (printError) {
                fail("Error compiling: ${e.getLine(Source(program))}")
            }
            throw e
        }
        //return run(generator.instructions)
        TODO()
    }

    private fun run(instructions: List<EmulatorInstruction>): List<String> {
        val emulator = DefaultEmulator()
        emulator.setProgram(instructions)
        emulator.run()

        return emulator.outputStream.map { it.toString() }
    }
}

private class WalkerRunnerasd {
    fun runBody(body: String, printError: Boolean): List<String> {
        val bodyTrimmed = body.trimIndent()

        val bodyIndented = bodyTrimmed.lines().map { "  $it" }

        val lines = listOf("def main():") + bodyIndented

        val program = lines.joinToString("\n")

        return runProgram(program, printError)
    }

    fun runProgram(program: String, printError: Boolean): List<String> {
        val nodes = try {
            parserFromFile(program).parse()
        } catch (e: CompilerError) {
            if (printError) {
                fail("Error compiling line: \"${e.getLine(Source(program))}\", ${e.message}")
            }
            throw e
        }
        TODO()
    }
}

private class LlvmRunner {
    fun runBody(body: String, printError: Boolean): List<String> {
        TODO("Not yet implemented")
    }

    fun runProgram(program: String, printError: Boolean): List<String> {
        TODO("Not yet implemented")
    }

}

private fun getRunner(type: CompilerBackend): BackendCompiler {
    return when (type) {
        CompilerBackend.Emulator -> EmulatorRunner(BuiltInFunctions())
        CompilerBackend.Walker -> WalkerRunner(WalkConfig(1000))
    }
}


fun runBodyCheckOutput(type: CompilerBackend, body: String, vararg result: Any, printError: Boolean = true) {
    val expected = result.map(Any::toString)

    val actual = compileAndRunBody(body, getRunner(type), BuiltInSignatures())
    Assertions.assertIterableEquals(expected, actual)
}

fun runProgramCheckOutput(type: CompilerBackend, program: String, vararg result: Any, printError: Boolean = true) {


    val expected = result.map { it.toString() }

    val actual = compileAndRunProgram(StringReader(program), "dummyfile", getRunner(type), BuiltInSignatures())

    Assertions.assertIterableEquals(expected, actual)
}
