package compiler.features

import SourceProvider
import compiler.BackendCompiler
import compiler.BuiltInSignatures
import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerRunner
import compiler.builtInSymbolTable
import compiler.compileAndRunBody
import compiler.frontend.FileProvider
import compiler.frontend.FrontendCompilerError
import compiler.frontend.ProgramCompiler
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import requireNotReached
import java.io.File
import java.io.Reader
import java.io.StringReader
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


enum class CompilerBackend {
    //Emulator,
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
        CompilerBackend.Walker -> WalkerRunner(WalkConfig(10000))
    }
}

fun interface OutputMatcher {
    fun assertOutputMatch(output: List<String>)
}

private fun intArrayToString(ints: List<Int>): String {
    return String(ints.map { Char(it) }.toCharArray())
}

fun intMatcher(ints: List<Int>): OutputMatcher {


    return OutputMatcher { output ->
        val message = "Expected: ${intArrayToString(ints)}, Actual: $output"

        assertEquals(ints.map { it.toString() }, output, message)
    }
}

fun intMatcher(vararg ints: Int): OutputMatcher {
    return intMatcher(ints.toList())
}

fun matchString(string: String): OutputMatcher {
    return matchLines(listOf(string))
}

fun matchLines(vararg lines: String): OutputMatcher {
    return matchLines(lines.toList())
}

fun matchLines(lines: List<String>): OutputMatcher {

    return OutputMatcher { output ->
        val message = "Expected: ${lines}, Actual: $output"

        assertEquals(lines, output, message)
    }
}

fun runBodyCheckOutput(type: CompilerBackend, body: String, resultMatcher: OutputMatcher) {
    val actual = compileAndRunBody(body, getRunner(type), builtInSymbolTable())

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
        override fun getReader(filename: String): Reader {

            return StringReader(program.getValue(filename))
        }
    }, mainFilename, builtInSymbolTable()).compile()

    val actual = getRunner(type).buildAndRun(intermediate)

    resultMatcher.assertOutputMatch(actual)

}

private data class FeatureTestcase(
    val subject: String,
    val name: String,
    val path: Path,
)

private fun discoverTests(): List<FeatureTestcase> {

    val result = mutableListOf<FeatureTestcase>()

    val p = Path("", "src", "test", "features")

    for (testSubjectFolder in p.listDirectoryEntries()) {

        if (!testSubjectFolder.isDirectory()) {
            continue
        }

        val subjectName = testSubjectFolder.name

        for (testCasePath in testSubjectFolder.listDirectoryEntries()) {
            assert(testCasePath.isRegularFile())
            val testCase = testCasePath.name
//
//            if (testCase!="printVariable"){
//                continue
//            }
            result.add(FeatureTestcase(subjectName, testCase, testCasePath))

        }
    }
    return result
}


fun main() {
    discoverTests().forEach { println(it) }
}


private fun executeTest(testcase: FeatureTestcase, backend: CompilerBackend) {

    val programLines = mutableListOf<String>()
    val expectedLines = mutableListOf<String>()

    var foundDelimiter = false
    for (line in File(testcase.path.toUri()).readLines()) {
        if (line.trimStart(' ').startsWith("-----")) {
            foundDelimiter = true
            continue
        }
        if (!foundDelimiter) {
            programLines.add(line)
        } else {
            if (line.isNotBlank()) {
                expectedLines.add(line)
            }
        }
    }

    val command = expectedLines[0]


    val program = programLines.joinToString("\n")



    if (command == "disabled") {
        Assumptions.assumeTrue(false, "disabled")
        requireNotReached()
    }
    if (command == "fail") {

        assertThrows<FrontendCompilerError> {
            runProgramCheckOutput(backend, program, matchString("dummy value"))
        }
        return

    }

    val expected = matchLines(expectedLines.subList(1, expectedLines.indices.last + 1))

    if (command == "expect") {
        runProgramCheckOutput(backend, program, expected)
        return
    }

    assertTrue(false, "command is $command")

}


class Runner {

    private val testCases = discoverTests()


    private fun getTestCases(compiler: CompilerBackend): List<DynamicTest> {
        return testCases.map {
            val name = "${it.subject}/${it.name}"
            DynamicTest.dynamicTest(name) {
                executeTest(it, compiler)
            }
        }
    }

    @TestFactory
    fun walker(): List<DynamicTest> {
        return getTestCases(CompilerBackend.Walker)
    }
}