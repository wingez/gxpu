package se.wingez.instructions

import compiler.backends.emulator.SignatureBuilder
import compiler.backends.emulator.instructions.Instruction
import compiler.backends.emulator.instructions.InstructionSet
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import se.wingez.compiler.backends.emulator.EmulatorInstruction
import se.wingez.compiler.backends.emulator.Reference
import se.wingez.compiler.backends.emulator.Value
import se.wingez.compiler.backends.emulator.instructions.AssembleError
import se.wingez.compiler.frontend.Label

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
    fun testLabel() {
        val i = InstructionSet()
        val testInstr = Instruction("test #ins", emulate = emptyEmulate, id = 0u)
        i.addInstruction(testInstr)

        assertIterableEquals(
            emptyList<UByte>(),
            i.assembleMnemonicFile(
                """
            :here
        """.trimIndent()
            )
        )

        assertIterableEquals(
            listOf(
                EmulatorInstruction(
                    testInstr, mapOf(
                        "ins" to Value(
                            reference = Reference(
                                SignatureBuilder("main").getSignature(),
                                Label("here")
                            )
                        )
                    )
                )
            ),
            i.assembleMnemonicFile(
                """
            :here
            test #here
        """.trimIndent()
            )
        )

        assertIterableEquals(
            listOf(
                EmulatorInstruction(
                    testInstr, mapOf(
                        "ins" to Value(
                            reference = Reference(
                                SignatureBuilder("main").getSignature(),
                                Label("here")
                            )
                        )
                    )
                ), EmulatorInstruction(
                    testInstr, mapOf(
                        "ins" to Value(
                            reference = Reference(
                                SignatureBuilder("main").getSignature(),
                                Label("here2")
                            )
                        )
                    )
                )
            ),
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
    fun testLabelLookahead() {
        val i = InstructionSet()
        val testInstr = Instruction("test #ins", emulate = emptyEmulate, id = 0u)
        i.addInstruction(testInstr)


        assertIterableEquals(
            listOf(
                EmulatorInstruction(
                    testInstr, mapOf(
                        "ins" to Value(
                            reference = Reference(
                                SignatureBuilder("main").getSignature(),
                                Label("here")
                            )
                        )
                    )
                ), EmulatorInstruction(
                    testInstr, mapOf(
                        "ins" to Value(constant = 4)
                    )
                )
            ), i.assembleMnemonicFile(
                """
            test #here
            :here
            test #4
        """.trimIndent()
            )
        )
    }

}

