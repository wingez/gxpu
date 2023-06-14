package se.wingez.instructions

import compiler.backends.emulator.emulator.Emulator
import compiler.backends.emulator.instructions.Instruction
import compiler.backends.emulator.instructions.InstructionBuilderError
import compiler.backends.emulator.instructions.InstructionSet
import compiler.backends.emulator.instructions.RegisterInstructionError
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import se.wingez.bytes
import se.wingez.compiler.backends.emulator.EmulatorInstruction
import se.wingez.compiler.backends.emulator.Value
import se.wingez.compiler.backends.emulator.emulate

val emptyEmulate = { _: Emulator, _: Map<String, UByte> -> }

internal class InstructionTest {


    @Test
    fun testAutoId() {
        val i = InstructionSet(maxSize = 3u)

        val createDummy = { index: UByte -> Instruction("dummy", emptyEmulate, index) }

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

        assertEquals(
            Instruction("test #a, #b", emptyEmulate, variableOrder = listOf("b", "a")).variableOrder,
            listOf("b", "a")
        )

        assertEquals(Instruction("test -#a", emptyEmulate).variableOrder, listOf("a"))

        assertThrows(RegisterInstructionError::class.java) {
            Instruction("test #a, #b", emptyEmulate, variableOrder = listOf("b", "aa"))
        }
        assertThrows(RegisterInstructionError::class.java) {
            Instruction("test #a, #b", emptyEmulate, variableOrder = listOf("b"))
        }
    }

    @Test
    fun testAddressBrackets() {
        assertEquals(Instruction("test [#a, #b]", emptyEmulate).variableOrder, listOf("a", "b"))
        val i = InstructionSet()

        val lda_at_fp_offset =
            i.createInstruction("LDA [FP #offset]", 1u, "", emptyEmulate)
        val lda_fp_offset =
            i.createInstruction("LDA FP #offset", 2u, "", emptyEmulate)

        val mnem1 = "LDA [FP #5]"
        val mnem2 = "LDA FP #4"

        assertEquals(i.assembleMnemonic(mnem1), listOf(emulate(lda_at_fp_offset, "offset" to 5)))
        assertEquals(i.assembleMnemonic(mnem2), listOf(emulate(lda_fp_offset, "offset" to 4)))
    }

    @Test
    fun testVariableSizes() {
        val i = Instruction("test #a, ##b", emptyEmulate)
        assertEquals(listOf("a", "b"), i.variableOrder)
        assertEquals(mapOf("a" to 1, "b" to 2), i.variableSizes)
        assertEquals(i.size, 4)
    }

    @Test
    fun testBuild() {
        assertIterableEquals(Instruction("test", emptyEmulate, 0u).build(), listOf<UByte>(0u))
        assertIterableEquals(Instruction("test #test", emptyEmulate, 5u).build(mapOf("test" to 6)), bytes(5, 6))
        assertIterableEquals(
            Instruction("test #a #b", emptyEmulate, 7u, variableOrder = listOf("b", "a")).build(
                mapOf("a" to 2, "b" to 3)
            ), bytes(7, 3, 2)
        )

        val i = Instruction("test #a, #b", emptyEmulate)
        assertThrows(InstructionBuilderError::class.java) {
            i.build(mapOf("a" to 6))
        }
        assertThrows(InstructionBuilderError::class.java) {
            i.build(mapOf("a" to 6, "b" to 4, "c" to 10))
        }
    }

    @Test
    fun testAssembleMnemonic() {
        val i = InstructionSet()
        val test1 = Instruction("test", emptyEmulate, 0u)
        val test2 = Instruction("test #ins #tmp", emptyEmulate, 2u)
        val test3 = Instruction("test #ins", emptyEmulate, 1u)
        i.addInstruction(test1)
        i.addInstruction(test2)
        i.addInstruction(test3)

        assertIterableEquals(i.assembleMnemonic("test"), listOf(emulate(test1)))
        assertIterableEquals(i.assembleMnemonic("test #4"), listOf(emulate(test3, "ins" to 4)))

        assertThrows(InstructionBuilderError::class.java) {
            i.assembleMnemonic("test 4")
        }

        assertIterableEquals(
            i.assembleMnemonic("test #5 #6"),
            listOf(emulate(test2, values = listOf("ins" to 5, "tmp" to 6), references = emptyList()))
        )
        assertIterableEquals(
            i.assembleMnemonic("test    #5   #6"),
            listOf(emulate(test2, values = listOf("ins" to 5, "tmp" to 6), references = emptyList()))
        )

        assertIterableEquals(i.assembleMnemonic("    "), emptyList<EmulatorInstruction>())
    }

    @Test
    fun testAssembleMnemonicSizes() {
        val i = InstructionSet()
        val instr = Instruction("test ##var, #val", emptyEmulate, 0u)
        i.addInstruction(instr)

        assertIterableEquals(
            listOf(
                emulate(
                    instr,
                    values = listOf("var" to 258, "val" to 8),
                    references = emptyList()
                )
            ), i.assembleMnemonic("test ##258 #8")
        )
    }

    @Test
    fun testAssembleMnemonicComment() {
        assertIterableEquals(InstructionSet().assembleMnemonic("   // comment"), bytes())
    }

    @Test
    fun testAssembleMnemonicCaseInvariance() {
        val i = InstructionSet()
        val test = Instruction("test #ins", emptyEmulate, 1u)
        val test2 = Instruction("test2 #ins", emptyEmulate, 2u)
        i.addInstruction(test)
        i.addInstruction(test2)

        assertIterableEquals(i.assembleMnemonic("test #0"), listOf(emulate(test, "ins" to 0)))
        assertIterableEquals(i.assembleMnemonic("TesT #0"), listOf(emulate(test, "ins" to 0)))
        assertIterableEquals(i.assembleMnemonic("test2 #0"), listOf(emulate(test2, "ins" to 0)))
    }

    @Test
    fun testAssembleNegativeSymbol() {
        val i = InstructionSet()
        val instr = Instruction("sta fp, #offset", emulate = emptyEmulate, 1u)
        i.addInstruction(instr)

        assertIterableEquals(i.assembleMnemonic("sta fp, #5"), listOf(emulate(instr, "offset" to 5)))
        assertIterableEquals(i.assembleMnemonic("sta fp, #-10"), listOf(emulate(instr, "offset" to -10)))
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
    fun testDisassembleWithIndices() {
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

        val expected = mapOf(
            0 to "test #15",
            2 to "second",
            3 to "second",
            4 to "TEst2 #6 #3",
            7 to "test #14",
        )

        assertEquals(expected, i.disassembleWithIndex(code))
    }


    @Test
    fun testPrintInstructions() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test", emulate = emptyEmulate, id = 0u, group = "group1"))
        i.addInstruction(Instruction("test #ins #tmp", emulate = emptyEmulate, id = 1u, group = "group2"))
        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 2u, group = "group1"))

        assertIterableEquals(
            i.describeInstructions(),
            listOf(
                "Group: group1",
                "  0: test",
                "  2: test #ins",
                "Group: group2",
                "  1: test #ins #tmp"
            )
        )
    }

    @Test
    fun testPrintInstructionsNoGroup() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test", emulate = emptyEmulate, id = 5u))

        assertIterableEquals(
            i.describeInstructions(),
            listOf(
                "Group: not set",
                "  5: test",
            )
        )
    }

}
