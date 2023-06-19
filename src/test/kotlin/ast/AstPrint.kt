package ast

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class AstPrint {

    @Test
    fun testBasic() {
        assertEquals(
            "5",
            formatAst(
                parseExpression("5")
            )
        )
        assertEquals(
            """
            call add
              5
              5
        """.trimIndent(),
            formatAst(
                parseExpression("5+5")
            )
        )
        assertEquals(
            """
            call add
              call add
                5
                6
              7
        """.trimIndent(),
            formatAst(
                parseExpression("5+6+7")
            )
        )
    }

}