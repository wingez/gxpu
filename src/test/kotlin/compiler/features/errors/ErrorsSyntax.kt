package compiler.features.errors

import CompilerError
import SourceProvider
import ast.syntaxerror.ParserSyntaxError
import compiler.features.CompilerBackend
import compiler.features.runProgramCheckOutput
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains
import kotlin.test.assertEquals


private inline fun <reified T : CompilerError> assertThrowsProgram(
    program: String,
    message: String,
    expectedLine: String,
    lineNumberOffset: Int = 0
) {

    val provider = SourceProvider { filename, lineNumber ->
        assertEquals(filename, "dummyfile")
        program.lines()[lineNumber + lineNumberOffset]
    }

    assertThrows<T> {
        val compiler = CompilerBackend.Walker //TODO perhaps run on all backends
        runProgramCheckOutput(compiler, program, printError = false)

    }.also {
        assertEquals(message, it.message)
        assertContains(it.getLine(provider), expectedLine)

    }


}

private inline fun <reified T : CompilerError> assertThrowsBody(
    body: String,
    message: String,
    expectedLine: String,
) {
    val program = "def main()\n" + body.trimIndent().indent(2)
    assertThrowsProgram<T>(program, message, expectedLine, lineNumberOffset = 1) // adapt to added function def line
}


class ErrorsSyntax {
    @Test
    fun testErrorsBasic() {
        assertThrowsBody<ParserSyntaxError>("val test=5", "Expected token to be of type Colon, but was EOL", "5")
    }

    @Test
    fun missingAssignmentValue(){
        assertThrowsBody<ParserSyntaxError>("val test", "Expected token to be of type Colon, but was EOL", "val test")
        assertThrowsBody<ParserSyntaxError>("val test", "Expected token to be of type Colon, but was EOL", "val test")
    }

}