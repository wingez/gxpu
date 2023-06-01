package se.wingez.emulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.byte
import se.wingez.bytes
import se.wingez.compiler.backends.emulator.emulator.*
import se.wingez.compiler.backends.emulator.instructions.InstructionSet
import java.io.StringReader

internal class EmulatorBasicTest {

    companion object {
        fun assembleLoadEmulator(program: String): Emulator {
            val e = DefaultEmulator()
            val memory = e.instructionSet.assembleMnemonicFile(StringReader(program))
            e.setAllMemory(memory)
            return e
        }
    }

    val dummyInstructions: InstructionSet

    init {
        val i = InstructionSet()

        i.createInstruction("invalid", 0u) {
            throw EmulatorInvalidInstructionError("Invalid instruction 0")
        }
        i.createInstruction("exit", 1u) {
            it.halt()
        }
        i.createInstruction("print", 2u) {
            it.print(it.a)
        }
        i.createInstruction("LDA #val", 3u) {
            it.a = it.getIncPC()
        }
        dummyInstructions = i
    }

    @Test
    fun testMemoryAccess() {
        val e = Emulator(dummyInstructions, 4)
        assertEquals(e.memory.size, 4)

        //check memory to large
        assertThrows<EmulatorRuntimeError> { e.setAllMemory(Array<UByte>(5) { 0u }.toList()) }

        assertThrows<EmulatorRuntimeError> { e.getMemoryAt(4) }
    }

    @Test
    fun testPc() {
        val e = Emulator(dummyInstructions, 4)
        e.setAllMemory(bytes(0, 1, 2, 3))
        assertEquals(e.pc, byte(0))

        for (i in 0..3) {
            assertEquals(e.getIncPC(), byte(i))
        }
        assertThrows<EmulatorRuntimeError> {
            e.getIncPC()
        }
    }

    @Test
    fun testPcLoopAround() {
        val e = Emulator(dummyInstructions)

        for (i in 0..300) {
            e.getIncPC()
        }
    }

    @Test
    fun testALoopAround() {
        val e = Emulator(dummyInstructions)

        e.a = byte(255)
        e.a = e.a.inc()
        assertEquals(e.a, byte(0))

        e.a = e.a.inc()
        assertEquals(e.a, byte(1))


    }

    @Test
    fun testExecutionCyclesExceeded() {
        val e = Emulator(dummyInstructions)

        // Exit
        e.setAllMemory(bytes(
                // 4x Load A, #0
                3, 0, 3, 0, 3, 0, 3, 0,
                // Exit
                1,
        ))
        e.run()
        e.reset()

        e.run(maxClockCycles = 5)
        e.reset()

        assertThrows<EmulatorCyclesExceeded> { e.run(maxClockCycles = 4) }
    }

    @Test
    fun testExecutionGeneral() {
        val e = DefaultEmulator()

        fun buildAndRun(vararg instructions: Iterable<UByte>): List<UByte> {

            val code = mutableListOf<UByte>()
            instructions.forEach { code.addAll(it) }

            e.reset()
            e.outputStream.clear()
            e.setAllMemory(code)
            e.run()
            return e.outputStream.toList()
        }

        assertIterableEquals(buildAndRun(
                DefaultEmulator.print.build(), DefaultEmulator.exit.build()
        ), bytes(0))
        assertIterableEquals(buildAndRun(
            DefaultEmulator.print.build(),
            DefaultEmulator.lda_constant.build(mapOf("val" to 10)),
            DefaultEmulator.print.build(),
            DefaultEmulator.exit.build()
        ), bytes(0, 10))
    }


    @Test
    fun testCall() {
        val program = """
         LDSP #25
         LDFP #25
         
         CALL #7
         invalid
         EXIT
        """
        val e = assembleLoadEmulator(program)
        e.run()

        assertEquals(e.getMemoryAt(25 - 1), byte(25))
        assertEquals(e.getMemoryAt(25 - 2), byte(6))
        assertEquals(e.fp, byte(23))
        assertEquals(e.sp, byte(23))
    }

    @Test
    fun testCallAndRet() {
        val program = """
    LDSP #25
    LDFP #25
    
    LDA #1
    OUT
    
    CALL #13
    LDA #3
    OUT
    EXIT
    
    ldfp sp
    LDA #2
    OUT
    RET
    
    """
        val e = assembleLoadEmulator(program)
        e.run()
        assertIterableEquals(e.outputStream, bytes(1, 2, 3))
        assertEquals(e.fp, byte(25))
        assertEquals(e.sp, byte(25))

    }


    @Test
    fun testJump() {
        val program = """
    
    lda #5
    out
    jmp #7
    lda #7
    
    out
    exit
    """
        val e = assembleLoadEmulator(program)
        e.run()
        assertIterableEquals(e.outputStream, bytes(5, 5))
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
    tsta
    exit
    """
        var e = assembleLoadEmulator(program)
        e.run()
        assertEquals(e.zeroFlag, false)

        e = assembleLoadEmulator("""
    lda #0
    tsta
    exit
        """)
        e.run()
        assertEquals(e.zeroFlag, true)
    }

    @Test
    fun testJumpIfZero() {
        val program = """
    lda #1
    tsta
    jmpz #6
    out
    lda #0
    tsta
    jmpz #12
    out
    
    exit
    """
        val e = assembleLoadEmulator(program)
        e.run()
        assertIterableEquals(e.outputStream, bytes(1))
    }


}