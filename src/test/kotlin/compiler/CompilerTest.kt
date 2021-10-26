package se.wingez.compiler

import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.FunctionNode
import se.wingez.ast.StatementNode
import se.wingez.ast.parseExpressions
import se.wingez.ast.parserFromFile
import se.wingez.byte
import se.wingez.compiler.actions.ActionBuilder
import se.wingez.emulator.DefaultEmulator
import se.wingez.tokens.parseFile
import java.io.StringReader


fun buildSingleMainFunction(nodes: List<StatementNode>): List<UByte> {
    val node = FunctionNode("main", emptyList(), nodes, "")
    val c = Compiler()
    return c.buildProgram(listOf(node))
}

fun buildBody(body: String): List<UByte> {
    val tokens = parseFile(StringReader(body))
    val nodes = parseExpressions(tokens)


    val node = FunctionNode("main", emptyList(), nodes, "")
    val frame = calculateFrameLayout(node, dummyTypeContainer, 0u)

    val generator = CodeGenerator()
    val function = FunctionBuilder(generator, frame, ActionBuilder(frame, dummyFunctions))
    function.buildNodes(node.body)

    return generator.resultingCode
}

fun buildProgram(body: String): List<UByte> {
    val nodes = parserFromFile(body).parse()

    val c = Compiler()
    c.buildProgram(nodes)

    return c.generator.resultingCode
}

fun shouldMatch(code: List<UByte>, expected: List<UByte>) {
    assertIterableEquals(code, expected) {
        (listOf("SDisassembled: ") + DefaultEmulator().instructionSet.disassemble(code)).joinToString(
            "\n"
        )
    }
}

fun bodyShouldMatchAssembled(body: String, expectedAssembly: String) {

    val code = buildBody(body)
    val expected = DefaultEmulator().instructionSet.assembleMnemonicFile(StringReader(expectedAssembly))

    shouldMatch(code, expected)
}

fun programShouldMatchAssembled(program: String, expectedAssembly: String) {
    val code = buildProgram(program)
    val expected = DefaultEmulator().instructionSet.assembleMnemonicFile(StringReader(expectedAssembly))
    shouldMatch(code, expected)
}

class CompilerTest {
    @Test
    fun testFpOffset() {


        /**
        #Expected
        ldfp #255
        ldsp #255
        call #7
        exit

        ldfp sp
        ret
         */

        val code = buildSingleMainFunction(emptyList())
        assertIterableEquals(
            code, listOf(
                // Init stack and frame
                DefaultEmulator.ldfp.id, byte(255),
                DefaultEmulator.ldsp.id, byte(255),
                // Call
                DefaultEmulator.call_addr.id, byte(7),
                // On return
                DefaultEmulator.exit.id,
                //Start of function
                DefaultEmulator.ldfp_sp.id,
                // Frame size is 0
                DefaultEmulator.ret.id,
            )
        )
    }

    @Test
    fun testIfNoElse() {
        val expected = """
        lda #0
        pusha
        lda #5
        suba sp #0
        addsp #1
        tsta
        
        jmpz #15
        lda #1
        out
        """
        val body = """
          if 5!=0:
            print(1)
        """
        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testIfElse() {
        val expected = """
        lda #0
        pusha
        lda #5
        suba sp #0
        addsp #1
        tsta
        
        jmpz #17
        lda #1
        out
        jmp #20
        lda #2
        out
            
        """
        val body = """
          if 5!=0:
            print(1)
          else:
            print(2)
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testWhile() {
        val expected = """
        lda #0
        pusha
        lda #5
        suba sp #0
        addsp #1
        tsta
        
        jmpz #17
        lda #1
        out
        jmp #0
         
        """
        val body = """
          while 5!=0:
            print(1)
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testIfConditionMustBeComparison() {
        val code = """
            if 2:
              print(6)
        """
        assertThrows<CompileError> {
            buildBody(code)
        }

    }

    @Test
    fun testCall() {
        val expected = """
        LDFP #255
        LDSP #255
        CALL #12
        exit
        # test1 
        LDFP SP
        LDA #10
        out
        RET
        #main
       
        LDFP SP
        CALL #7
        LDA #3
        out
        ret
         
        """
        val body = """
          def test1():
            print(10)
            
          def main():
            test1()
            print(3)
        """

        programShouldMatchAssembled(body, expected)
    }

}