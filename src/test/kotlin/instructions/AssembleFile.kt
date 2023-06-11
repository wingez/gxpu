package se.wingez.instructions

import compiler.backends.emulator.instructions.AssembleError
import compiler.backends.emulator.instructions.Instruction
import compiler.backends.emulator.instructions.InstructionSet
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

    @Test
    fun testLabel() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 0u))

        assertIterableEquals(
            emptyList<UByte>(),
            i.assembleMnemonicFile(
                """
            :here
        """.trimIndent()
            )
        )

        assertIterableEquals(
            listOf<UByte>(0u, 0u),
            i.assembleMnemonicFile(
                """
            :here
            test #here
        """.trimIndent()
            )
        )

        assertIterableEquals(
            listOf<UByte>(0u, 0u, 0u, 2u),
            i.assembleMnemonicFile(
                """
            :here
            test #here
            :here2
            test #here2
        """.trimIndent()
            )
        )
    }

    @Test
    fun testLabelOverwrite() {
        val i = InstructionSet()

        assertThrows<AssembleError> {
            i.assembleMnemonicFile(
                """
                :here
                :here
            """.trimIndent()
            )
        }
    }


    @Test
    fun testLabelScope() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 0u))

        assertThrows<AssembleError> {
            i.assembleMnemonicFile(
                """
            scope
            :here
            endscope
            test #here
            
        """.trimIndent()
            )
        }

        assertIterableEquals(
            i.assembleMnemonicFile(
                """
                test #0
                test #2
                test #0
            """.trimIndent()
            ),
            i.assembleMnemonicFile(
                """
                :here
                test #here
                scope
                :here
                test #here
                endscope
                test #here
            """.trimIndent()
            )
        )

    }

    @Test
    fun testLabelLookahead() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 0u))


        assertIterableEquals(
            listOf<UByte>(0u, 2u), i.assembleMnemonicFile(
                """
            test #here
            :here
        """.trimIndent()
            )
        )
    }

    @Test
    fun testLabelLookaheadScope() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 0u))

        assertThrows<AssembleError> {
            i.assembleMnemonicFile(
                """
                
                test #here
                scope
                :here
                endscope
                
            """.trimIndent()
            )
        }

    }
}

