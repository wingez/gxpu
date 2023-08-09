package compiler.features

import SourceProvider
import compiler.BackendCompiler
import compiler.BuiltInSignatures
import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerRunner
import compiler.backends.emulator.BuiltInFunctions
import compiler.backends.emulator.EmulatorRunner
import compiler.compileAndRunBody
import compiler.frontend.FileProvider
import compiler.frontend.ProgramCompiler
import java.io.Reader
import java.io.StringReader
import kotlin.streams.toList
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


private fun getRunner(type: CompilerBackend): BackendCompiler {
    return when (type) {
        CompilerBackend.Emulator -> EmulatorRunner(BuiltInFunctions())
        CompilerBackend.Walker -> WalkerRunner(WalkConfig(1000))
    }
}

fun interface OutputMatcher {
    fun assertOutputMatch(output: List<Int>)
}

private fun intArrayToString(ints: List<Int>): String {
    return String(ints.map { Char(it) }.toCharArray())
}

fun intMatcher(ints: List<Int>): OutputMatcher {


    return OutputMatcher { output ->
        val message = "Expected: ${intArrayToString(ints)}, Actual: ${intArrayToString(output)}"

        assertEquals(ints, output, message)
    }
}

fun intMatcher(vararg ints: Int): OutputMatcher {
    return intMatcher(ints.toList())
}

fun matchString(string: String): OutputMatcher {
    return intMatcher(string.chars().toList())
}

fun matchLines(vararg lines: String): OutputMatcher {
    return matchLines(lines.toList())
}

fun matchLines(lines: List<String>): OutputMatcher {
    return matchString(lines.joinToString(""))
}

fun runBodyCheckOutput(type: CompilerBackend, body: String, resultMatcher: OutputMatcher) {
    val actual = compileAndRunBody(body, getRunner(type), BuiltInSignatures())

    resultMatcher.assertOutputMatch(actual)
}

fun runProgramCheckOutput(type: CompilerBackend, program: String, resultMatcher: OutputMatcher) {

    runProgramCheckOutput(type, mapOf("dummyfile" to program), "dummyfile", resultMatcher)


}


fun runProgramCheckOutput(
    type: CompilerBackend,
    program: Map<String, String>,
    mainFilename: String,
    resultMatcher: OutputMatcher
) {

    val intermediate = ProgramCompiler(object : FileProvider {
        override fun getReader(filename: String): Reader? {

            return program[filename]?.let { StringReader(it) }
        }
    }, mainFilename, BuiltInSignatures()).compile()

    val actual = getRunner(type).buildAndRun(intermediate)

    resultMatcher.assertOutputMatch(actual)

}