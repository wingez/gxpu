package se.wingez.instructions

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import se.wingez.bytes
import se.wingez.emulator.Emulator

internal class InstructionTest {

    private val emptyEmulate = { _: Emulator -> false }

    @Test
    fun testAutoId() {
        val i = InstructionSet(maxSize = 3u)

        val createDummy = { index: UByte -> Instruction("dummy", { _ -> false }, index) }

        val i1 = createDummy(1u)
        val i0 = createDummy(0u)

        i.addInstruction(i1)

        val iauto0 = createDummy(Instruction.AUTO_INDEX_ASSIGMENT)
        val iauto2 = createDummy(Instruction.AUTO_INDEX_ASSIGMENT)
        val iauto3 = createDummy(Instruction.AUTO_INDEX_ASSIGMENT)
        i.addInstruction(iauto0)
        i.addInstruction(iauto2)

        assertEquals(i1.id, (1u).toUByte())
        assertEquals(iauto0.id, (0u).toUByte())
        assertEquals(iauto2.id, (2u).toUByte())

        assertThrows(RegisterInstructionError::class.java) { i.addInstruction(iauto3) }?.let {
            it.message?.let { it1 -> assertTrue(it1.contains("Maximum number of instructions reached")) }
        }


        assertThrows(RegisterInstructionError::class.java) { i.addInstruction(i0) }?.let {
            it.message?.let { it1 -> assertTrue(it1.contains("An instruction with id 0 already exists")) }
        }
    }

    @Test
    fun testMnemonicName() {
        assertEquals(Instruction("mnem", emptyEmulate).name, "mnem")
        assertEquals(Instruction("lda #test", emptyEmulate).name, "lda")
    }

    @Test
    fun testVariables() {
        assertEquals(Instruction("mnem", emptyEmulate).variableOrder, emptyList<String>())
        assertEquals(Instruction("lda hello", emptyEmulate).variableOrder, emptyList<String>())
        assertEquals(Instruction("lda sp,#var", emptyEmulate).variableOrder, listOf("var"))
        assertEquals(Instruction("test #a, #b", emptyEmulate).variableOrder, listOf("a", "b"))

        assertEquals(Instruction("test #a, #b", emptyEmulate, variableOrder = listOf("b", "a")).variableOrder, listOf("b", "a"))

        assertEquals(Instruction("test -#a", emptyEmulate).variableOrder, listOf("a"))

        assertThrows(RegisterInstructionError::class.java) {
            Instruction("test #a, #b", emptyEmulate, variableOrder = listOf("b", "aa"))
        }
        assertThrows(RegisterInstructionError::class.java) {
            Instruction("test #a, #b", emptyEmulate, variableOrder = listOf("b"))
        }
    }

    @Test
    fun testBuild() {
        assertIterableEquals(Instruction("test", emptyEmulate, 0u).build(), listOf<UByte>(0u))
        assertIterableEquals(Instruction("test #test", emptyEmulate, 5u).build(mapOf("test" to 6u)), bytes(5, 6))
        assertIterableEquals(Instruction("test #a #b", emptyEmulate, 7u, variableOrder = listOf("b", "a")).build(
                mapOf("a" to 2u, "b" to 3u)), bytes(7, 3, 2))

        val i = Instruction("test #a, #b", emptyEmulate)
        assertThrows(InstructionBuilderError::class.java) {
            i.build(mapOf("a" to 6u))
        }
        assertThrows(InstructionBuilderError::class.java) {
            i.build(mapOf("a" to 6u, "b" to 4u, "c" to 10u))
        }
    }

    @Test
    fun testAssembleMnemonic() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test", emptyEmulate, 0u))
        i.addInstruction(Instruction("test #ins #tmp", emptyEmulate, 2u))
        i.addInstruction(Instruction("test #ins", emptyEmulate, 1u))

        assertIterableEquals(i.assembleMnemonic("test"), bytes(0))
        assertIterableEquals(i.assembleMnemonic("test #4"), bytes(1, 4))

        assertThrows(InstructionBuilderError::class.java) {
            i.assembleMnemonic("test 4")
        }

        assertIterableEquals(i.assembleMnemonic("test #5 #6"), bytes(2, 5, 6))
        assertIterableEquals(i.assembleMnemonic("test    #5   #6"), bytes(2, 5, 6))

        assertIterableEquals(i.assembleMnemonic("    "), bytes())
    }

    @Test
    fun testAssembleMnemonicComment() {
        assertIterableEquals(InstructionSet().assembleMnemonic("   # comment"), bytes())
    }

    @Test
    fun testAssembleMnemonicCaseInvariance() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test #ins", emptyEmulate, 1u))
        i.addInstruction(Instruction("TEst2 #ins", emptyEmulate, 2u))

        assertIterableEquals(i.assembleMnemonic("test #0"), bytes(1, 0))
        assertIterableEquals(i.assembleMnemonic("TesT #0"), bytes(1, 0))
        assertIterableEquals(i.assembleMnemonic("test2 #0"), bytes(2, 0))
    }

    @Test
    fun testAssembleNegativeSymbol() {
        val i = InstructionSet()
        i.addInstruction(Instruction("sta fp, #offset", emulate = emptyEmulate, 1u))
        i.addInstruction(Instruction("sta fp, -#offset", emulate = emptyEmulate, 2u))

        assertIterableEquals(i.assembleMnemonic("sta fp, #5"), bytes(1, 5))
        assertIterableEquals(i.assembleMnemonic("sta fp, -#10"), bytes(2, 10))
    }

    @Test
    fun testDisassemble() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 1u))
        i.addInstruction(Instruction("TEst2 #ins #asd", emulate = emptyEmulate, id = 2u))
        i.addInstruction(Instruction("second", emulate = emptyEmulate, id = 3u))

        val code = bytes(
                1, 15,
                3,
                3,
                2, 6, 3,
                1, 14
        )

        val expected = listOf(
                "test #15",
                "second",
                "second",
                "TEst2 #6 #3",
                "test #14",
        )

        assertIterableEquals(i.disassemble(code), expected)
    }


    @Test
    fun testPrintInstructions() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test", emulate = emptyEmulate, id = 0u, group = "group1"))
        i.addInstruction(Instruction("test #ins #tmp", emulate = emptyEmulate, id = 1u, group = "group2"))
        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 2u, group = "group1"))

        assertIterableEquals(i.describeInstructions(),
                listOf("Group: group1",
                        "  0: test",
                        "  2: test #ins",
                        "Group: group2",
                        "  1: test #ins #tmp"
                ))
    }

    @Test
    fun testPrintInstructionsNoGroup() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test", emulate = emptyEmulate, id = 5u))

        assertIterableEquals(i.describeInstructions(),
                listOf("Group: not set",
                        "  5: test",
                ))
    }

}
