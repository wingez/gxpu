package se.wingez.compiler

import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import se.wingez.ast.FunctionNode
import se.wingez.ast.StatementNode
import se.wingez.byte
import se.wingez.emulator.DefaultEmulator

class CompilerTest {

    fun buildSingleMainFunction(nodes: List<StatementNode>): List<UByte> {
        val node = FunctionNode("main", emptyList(), nodes, "")
        val c = Compiler()
        return c.buildProgram(listOf(node))
    }


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
}