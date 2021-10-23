package se.wingez.instructions

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
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
        assertIterableEquals(Instruction("test #test", emptyEmulate, 5u).build(mapOf("test" to 6u)), listOf<UByte>(5u, 6u))
        assertIterableEquals(Instruction("test #a #b", emptyEmulate, 7u, listOf("b", "a")).build(
                mapOf("a" to 2u, "b" to 3u)), listOf<UByte>(7u, 3u, 2u))

        val i = Instruction("test #a, #b", emptyEmulate)
        assertThrows(InstructionBuilderError::class.java) {
            i.build(mapOf("a" to 6u))
        }
        assertThrows(InstructionBuilderError::class.java) {
            i.build(mapOf("a" to 6u, "b" to 4u, "c" to 10u))
        }
    }
}