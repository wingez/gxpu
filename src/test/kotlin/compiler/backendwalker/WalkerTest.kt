package compiler.backendwalker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import compiler.backends.astwalker.WalkerException
import compiler.features.matchString
import compiler.frontend.FrontendCompilerError
import org.junit.jupiter.api.Disabled
import kotlin.test.assertEquals
import kotlin.test.assertTrue


internal class WalkerTest {








    @Test
    fun testWhileMaxIterations() {
        val function = """
            def main():
              val i=0
              while i!=10:
                i=i+1
             
        """.trimIndent()
        assertDoesNotThrow { run(function) }

        assertDoesNotThrow { run(function, maxLoopIterations = 100) }
        assertThrows<WalkerException>("Max iterations exceeded") { run(function, maxLoopIterations = 20) }
    }
}