package compiler.features

import SourceProvider
import compiler.BackendCompiler
import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerRunner
import compiler.backends.machineCode.MachineCodeRunner
import compiler.builtInSymbolTable
import compiler.compileAndRunBody
import compiler.frontend.CompiledIntermediateProgram
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
import kotlin.test.fail


enum class CompilerBackend {
    MachineCode,
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
        CompilerBackend.MachineCode -> MachineCodeRunner()
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


fun runCompiledProgramCheckOutput(
    type: CompilerBackend,
    program: CompiledIntermediateProgram,
    resultMatcher: OutputMatcher
) {
    val actual = getRunner(type).buildAndRun(program)

    resultMatcher.assertOutputMatch(actual)
}


fun runProgramCheckOutput(
    type: CompilerBackend,
    program: Map<String, String>,
    mainFilename: String,
    resultMatcher: OutputMatcher
) {

    val intermediate = ProgramCompiler(
        { filename -> StringReader(program.getValue(filename)) },
        mainFilename,
        builtInSymbolTable()
    ).compile()

    runCompiledProgramCheckOutput(type, intermediate, resultMatcher)
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

    val expectedLines = mutableListOf<String>()

    var foundDelimiter = false

    val mainFile: Path
    val fileProvider: FileProvider


    if (testcase.path.isRegularFile()) {
        mainFile = testcase.path
        fileProvider = FileProvider { filename ->
            if (filename != mainFile.name) {
                requireNotReached()
            }
            mainFile.reader()
        }
    } else {
        mainFile = testcase.path.resolve("main")
        require(mainFile.exists())
        fileProvider = FileProvider { filename ->
            testcase.path.resolve(filename).reader()
        }
    }


    val symbolTable = builtInSymbolTable()
    val frontendCompiler = ProgramCompiler(fileProvider, mainFile.name, symbolTable)




    for (line in mainFile.readLines()) {
        if (line.trimStart(' ').startsWith("#####")) {
            foundDelimiter = true
            continue
        }
        if (foundDelimiter && line.isNotBlank()) {
            expectedLines.add(line.trimStart('#', ' '))
        }
    }

    if (expectedLines.size < 1) {
        fail("missing test template")
    }

    val command = expectedLines[0]

    if (command == "disabled") {
        Assumptions.assumeTrue(false, "disabled")
        requireNotReached()
    }
    if (command == "fail") {
        assertThrows<FrontendCompilerError> {
            frontendCompiler.compile()
        }
        return
    }

    val expected = matchLines(expectedLines.subList(1, expectedLines.indices.last + 1))

    if (command == "expect") {
        val intermediate = frontendCompiler.compile()
        runCompiledProgramCheckOutput(backend, intermediate, expected)
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

    @TestFactory
    fun machineCode(): List<DynamicTest> {
        return getTestCases(CompilerBackend.MachineCode)
    }

}