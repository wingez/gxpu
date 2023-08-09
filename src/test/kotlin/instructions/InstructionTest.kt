package instructions

import compiler.backends.emulator.emulator.Emulator
import compiler.backends.emulator.instructions.Instruction
import compiler.backends.emulator.instructions.InstructionBuilderError
import compiler.backends.emulator.instructions.InstructionSet
import compiler.backends.emulator.instructions.RegisterInstructionError
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import compiler.backends.emulator.EmulatorInstruction
import compiler.backends.emulator.emulate
import compiler.frontend.DefinitionBuilder

val emptyEmulate = { _: Emulator, _: Map<String, Int> -> }

internal class InstructionTest {


    @Test
    fun testAutoId() {
        val i = InstructionSet(maxSize = 3)

        val createDummy = { index: Int -> Instruction("dummy", emptyEmulate, index) }

        val i1 = createDummy(1)
        val i0 = createDummy(0)

        i.addInstruction(i1)

        val iauto0 = createDummy(Instruction.AUTO_INDEX_ASSIGMENT)
        val iauto2 = createDummy(Instruction.AUTO_INDEX_ASSIGMENT)
        val iauto3 = createDummy(Instruction.AUTO_INDEX_ASSIGMENT)
        i.addInstruction(iauto0)
        i.addInstruction(iauto2)

        assertEquals(i1.id, 1)
        assertEquals(iauto0.id, 0)
        assertEquals(iauto2.id, 2)

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
            i.createInstruction("LDA [FP #offset]", 1, "", emptyEmulate)
        val lda_fp_offset =
            i.createInstruction("LDA FP #offset", 2, "", emptyEmulate)

        val mnem1 = "LDA [FP #5]"
        val mnem2 = "LDA FP #4"

        assertEquals(i.assembleMnemonic(definition, mnem1), listOf(emulate(lda_at_fp_offset, "offset" to 5)))
        assertEquals(i.assembleMnemonic(definition,mnem2), listOf(emulate(lda_fp_offset, "offset" to 4)))
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
        assertIterableEquals(Instruction("test", emptyEmulate, 0).build(), listOf(0))
        assertIterableEquals(Instruction("test #test", emptyEmulate, 5).build(mapOf("test" to 6)), listOf(5, 6))
        assertIterableEquals(
            Instruction("test #a #b", emptyEmulate, 7, variableOrder = listOf("b", "a")).build(
                mapOf("a" to 2, "b" to 3)
            ), listOf(7, 3, 2)
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
        val test1 = Instruction("test", emptyEmulate, 0)
        val test2 = Instruction("test #ins #tmp", emptyEmulate, 2)
        val test3 = Instruction("test #ins", emptyEmulate, 1)
        i.addInstruction(test1)
        i.addInstruction(test2)
        i.addInstruction(test3)

        assertIterableEquals(i.assembleMnemonic(definition,"test"), listOf(emulate(test1)))
        assertIterableEquals(i.assembleMnemonic(definition,"test #4"), listOf(emulate(test3, "ins" to 4)))

        assertThrows(InstructionBuilderError::class.java) {
            i.assembleMnemonic(definition,"test 4")
        }

        assertIterableEquals(
            i.assembleMnemonic(definition,"test #5 #6"),
            listOf(emulate(test2, values = listOf("ins" to 5, "tmp" to 6), references = emptyList()))
        )
        assertIterableEquals(
            i.assembleMnemonic(definition,"test    #5   #6"),
            listOf(emulate(test2, values = listOf("ins" to 5, "tmp" to 6), references = emptyList()))
        )

        assertIterableEquals(i.assembleMnemonic(definition,"    "), emptyList<EmulatorInstruction>())
    }

    @Test
    fun testAssembleMnemonicSizes() {
        val i = InstructionSet()
        val instr = Instruction("test ##var, #val", emptyEmulate, 0)
        i.addInstruction(instr)

        assertIterableEquals(
            listOf(
                emulate(
                    instr,
                    values = listOf("var" to 258, "val" to 8),
                    references = emptyList()
                )
            ), i.assembleMnemonic(definition,"test ##258 #8")
        )
    }

    @Test
    fun testAssembleMnemonicComment() {
        assertIterableEquals(InstructionSet().assembleMnemonic(definition,"   // comment"), emptyList<Int>())
    }

    @Test
    fun testAssembleMnemonicCaseInvariance() {
        val i = InstructionSet()
        val test = Instruction("test #ins", emptyEmulate, 1)
        val test2 = Instruction("test2 #ins", emptyEmulate, 2)
        i.addInstruction(test)
        i.addInstruction(test2)

        assertIterableEquals(i.assembleMnemonic(definition,"test #0"), listOf(emulate(test, "ins" to 0)))
        assertIterableEquals(i.assembleMnemonic(definition,"TesT #0"), listOf(emulate(test, "ins" to 0)))
        assertIterableEquals(i.assembleMnemonic(definition,"test2 #0"), listOf(emulate(test2, "ins" to 0)))
    }

    @Test
    fun testAssembleNegativeSymbol() {
        val i = InstructionSet()
        val instr = Instruction("sta fp, #offset", emulate = emptyEmulate, 1)
        i.addInstruction(instr)

        assertIterableEquals(i.assembleMnemonic(definition,"sta fp, #5"), listOf(emulate(instr, "offset" to 5)))
        assertIterableEquals(i.assembleMnemonic(definition,"sta fp, #-10"), listOf(emulate(instr, "offset" to -10)))
    }

    @Test
    fun testDisassemble() {
        val i = InstructionSet()
        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 1))
        i.addInstruction(Instruction("TEst2 #ins #asd", emulate = emptyEmulate, id = 2))
        i.addInstruction(Instruction("second", emulate = emptyEmulate, id = 3))

        val code = listOf(
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
        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 1))
        i.addInstruction(Instruction("TEst2 #ins #asd", emulate = emptyEmulate, id = 2))
        i.addInstruction(Instruction("second", emulate = emptyEmulate, id = 3))

        val code = listOf(
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
        i.addInstruction(Instruction("test", emulate = emptyEmulate, id = 0, group = "group1"))
        i.addInstruction(Instruction("test #ins #tmp", emulate = emptyEmulate, id = 1, group = "group2"))
        i.addInstruction(Instruction("test #ins", emulate = emptyEmulate, id = 2, group = "group1"))

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
        i.addInstruction(Instruction("test", emulate = emptyEmulate, id = 5))

        assertIterableEquals(
            i.describeInstructions(),
            listOf(
                "Group: not set",
                "  5: test",
            )
        )
    }

}
