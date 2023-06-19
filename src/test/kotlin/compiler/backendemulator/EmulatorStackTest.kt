package compiler.backendemulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import compiler.backendemulator.EmulatorBasicTest.Companion.assembleLoadEmulator

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

        assertEquals(e.getMemoryAt(25), 5)
        assertEquals(e.a, 5)

        e.run()
        assertEquals(e.getMemoryAt(25), 5)
        assertEquals(e.getMemoryAt(25 + 1), 4)
        assertEquals(e.sp, 25 + 1)
        assertEquals(e.a, 4)

    }
}