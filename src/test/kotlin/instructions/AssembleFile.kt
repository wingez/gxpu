package se.wingez.instructions

import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class TestAssembleFile {
    @Test
    fun testScopeSyntax() {
        val i = InstructionSet()

        assertThrows<AssembleError> {
            i.assembleMnemonicFile(
                """
                    endscope
        """.trimIndent()
            )
        }
        assertThrows<AssembleError> {
            i.assembleMnemonicFile(
                """
                scope
                scope
                endscope
            """.trimIndent()
            )
        }

        assertDoesNotThrow {
            i.assembleMnemonicFile(
                """
                scope          
                endscope
            """.trimIndent()
            )
        }
    }

    @Test
    fun testAddVariableToScope() {

        val i = InstructionSet()

        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 0u))

        assertIterableEquals(
            listOf<UByte>(0u, 4u, 0u, 6u), i.assembleMnemonicFile(
                """
            scope
            #var1 = 4
            #var4 = 6
            test #var1
            test #var4
            endscope
        """.trimIndent()
            )
        )
    }

    @Test
    fun testOutOfScope() {

        val i = InstructionSet()

        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 1u))

        assertThrows<AssembleError> {
            i.assembleMnemonicFile(
                """
                    scope
                    #var = 4
                    endscope
                    test #var
        """
            )
        }
    }

    @Test
    fun testReassign() {
        val i = InstructionSet()

        assertThrows<AssembleError> {
            i.assembleMnemonicFile(
                """
           #var = 5
           #var = 4
        """
            )
        }
    }

    @Test
    fun testNestedScope() {
        val i = InstructionSet()

        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 0u))

        assertIterableEquals(
            listOf<UByte>(0u, 4u, 0u, 5u, 0u, 4u), i.assembleMnemonicFile(
                """
            scope
            #var = 4
            test #var
            scope
            #var = 5
            test #var
            endscope
            test #var
            endscope
            
        """.trimIndent()
            )
        )

    }

}

