package se.wingez.compiler.backendemulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import se.wingez.byte
import se.wingez.compiler.backendemulator.EmulatorBasicTest.Companion.assembleLoadEmulator

class EmulatorStackTest {


    @Test
    fun testStack() {
        val program = """
         LDSP #25
         LDFP #25
         
         LDA #5
         PUSHA
         exit
         
         LDA #4
         PUSHA
         LDA #3
         POPA
         
         EXIT
        """
        val e = assembleLoadEmulator(program)
        e.run()

        assertEquals(e.getMemoryAt(25), byte(5))
        assertEquals(e.a, byte(5))

        e.run()
        assertEquals(e.getMemoryAt(25), byte(5))
        assertEquals(e.getMemoryAt(25 + 1), byte(4))
        assertEquals(e.sp, byte(25 + 1))
        assertEquals(e.a, byte(4))

    }
}