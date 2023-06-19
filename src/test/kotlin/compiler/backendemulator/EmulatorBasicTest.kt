package compiler.backendemulator

import compiler.backends.emulator.emulator.*
import compiler.backends.emulator.instructions.InstructionSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import compiler.backends.emulator.EmulatorInstruction
import compiler.backends.emulator.emulate
import java.io.StringReader

internal class EmulatorBasicTest {

    companion object {
        fun assembleLoadEmulator(program: String): Emulator {
            val e = DefaultEmulator()
            val instructions = e.instructionSet.assembleMnemonicFile(StringReader(program))
            e.setProgram(instructions)
            return e
        }
    }

    val dummyInstructions: InstructionSet

    init {
        val i = InstructionSet()

        i.createInstruction("invalid", 0) { em, params ->
            throw EmulatorInvalidInstructionError("Invalid instruction 0")
        }
        i.createInstruction("exit", 1) { em, params ->
            em.halt()
        }
        i.createInstruction("print", 2) { em, params ->
            em.print(em.a)
        }
        i.createInstruction("LDA #val", 3) { em, params ->
            em.a = params.getValue("val")
        }
        dummyInstructions = i
    }

    @Test
    fun testMemoryAccess() {
        val e = Emulator(dummyInstructions, 4)
        assertEquals(e.memory.size, 4)

        //check memory to large
        assertThrows<EmulatorRuntimeError> { e.setAllMemory(Array(5) { 0 }.toList()) }

        assertThrows<EmulatorRuntimeError> { e.getMemoryAt(4) }
    }

    @Test
    fun testExecutionCyclesExceeded() {

        val program = """
         LDA #0
         LDA #0
         LDA #0
         LDA #0
         exit
        """
        val e = assembleLoadEmulator(program)
        e.run()
        e.reset()

        e.run(maxClockCycles = 5)
        e.reset()

        assertThrows<EmulatorCyclesExceeded> { e.run(maxClockCycles = 4) }
    }

    @Test
    fun testExecutionGeneral() {
        val e = DefaultEmulator()

        fun buildAndRun(vararg instructions: EmulatorInstruction): List<Int> {

            e.reset()
            e.clearMemory()
            e.outputStream.clear()
            e.setProgram(instructions.toList())
            e.run()
            return e.outputStream.toList()
        }

        assertIterableEquals(
            buildAndRun(
                emulate(DefaultEmulator.print),
                emulate(DefaultEmulator.exit)
            ), listOf(0)
        )
        assertIterableEquals(
            buildAndRun(
                emulate(DefaultEmulator.print),
                emulate(DefaultEmulator.lda_constant, "val" to 10),
                emulate(DefaultEmulator.print),
                emulate(DefaultEmulator.exit)
            ), listOf(0, 10)
        )
    }


    @Test
    fun testCall() {
        val program = """
         LDSP #25
         LDFP #25
         
         CALL #exit
         invalid
         :exit
         EXIT
        """
        val e = assembleLoadEmulator(program)
        e.run()

        assertEquals(e.fp, 25)
        assertEquals(e.sp, 25)
    }

    @Test
    fun testCallAndRet() {
        val program = """
    LDSP #25
    LDFP #25
    
    LDA #1
    OUT
    
    CALL #func
    LDA #3
    OUT
    EXIT
    :func
    ldfp sp
    LDA #2
    OUT
    RET
    
    """
        val e = assembleLoadEmulator(program)
        e.run()
        assertIterableEquals(e.outputStream, listOf(1, 2, 3))
        assertEquals(e.fp, 25)
        assertEquals(e.sp, 25)

    }


    @Test
    fun testJump() {
        val program = """
    
    lda #5
    out
    jmp #jumphere
    lda #7
    
    :jumphere
    out
    exit
    """
        val e = assembleLoadEmulator(program)
        e.run()
        assertIterableEquals(e.outputStream, listOf(5, 5))
    }

    @Test
    fun testLoopInfinite() {
        val program = """
    lda #5
    
    jmp #0
    exit
    """
        val e = assembleLoadEmulator(program)
        assertThrows<EmulatorCyclesExceeded> { e.run() }
    }

    @Test
    fun testZeroFlag() {
        val program = """
    lda #1
    tstz a
    exit
    """
        var e = assembleLoadEmulator(program)
        e.run()
        assertEquals(e.flag, false)

        e = assembleLoadEmulator(
            """
    lda #0
    tstz a
    exit
        """
        )
        e.run()
        assertEquals(e.flag, true)
    }

    @Test
    fun testJumpIfZero() {
        val program = """
    lda #1
    tstz a
    jmpf #jumphere
    out
    :jumphere
    lda #0
    tstz a
    jmpf #exit
    out
    :exit
    exit
    """
        val e = assembleLoadEmulator(program)
        e.run()
        assertIterableEquals(e.outputStream, listOf(1))
    }


}