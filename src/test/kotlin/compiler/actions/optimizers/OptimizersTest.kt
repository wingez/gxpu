package compiler.actions.optimizers

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import se.wingez.compiler.actions.ConstantRegister
import se.wingez.compiler.actions.PopRegister
import se.wingez.compiler.actions.PrintAction
import se.wingez.compiler.actions.PushRegister
import se.wingez.compiler.actions.optimizers.removePushPop

internal class OptimizersTest {

    @Test
    fun testRemovePushPop() {
        val list = mutableListOf(
            PrintAction(), ConstantRegister(5), PushRegister(), PopRegister(), ConstantRegister(5)
        )
        val expected = listOf(
            PrintAction(), ConstantRegister(5), ConstantRegister(5)
        )
        assertTrue(removePushPop(list))
        assertEquals(
            expected, list
        )
        assertFalse(removePushPop(list))
        assertEquals(
            expected, list
        )

    }
}