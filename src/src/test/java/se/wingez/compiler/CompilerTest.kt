package se.wingez.compiler

import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import se.wingez.ast.FunctionNode
import se.wingez.ast.StatementNode
import se.wingez.ast.parseExpressions
import se.wingez.byte
import se.wingez.emulator.DefaultEmulator
import se.wingez.parseFile
import java.io.StringReader


fun buildSingleMainFunction(nodes: List<StatementNode>): List<UByte> {
    val node = FunctionNode("main", emptyList(), nodes, "")
    val c = Compiler()
    return c.buildProgram(listOf(node))
}


fun bodyShouldMatchAssembled(body: String, expectedAssembly: String) {
    val tokens = parseFile(StringReader(body))
    val nodes = parseExpressions(tokens)


    val node = FunctionNode("main", emptyList(), nodes, "")
    val frame = calculateFrameLayout(node, dummyTypeContainer)

    val generator = CodeGenerator()
    val function = AssemblyFunction(generator, frame, 0u)
    function.buildNodes(node.body)

    val expected = DefaultEmulator().instructionSet.assembleMnemonicFile(StringReader(expectedAssembly))

    assertIterableEquals(generator.resultingCode, expected) {
        (listOf("Dissasembled: ") + DefaultEmulator().instructionSet.disassemble(generator.resultingCode)).joinToString(
            "\n"
        )
    }
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
            code, listOf<UByte>(
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
}