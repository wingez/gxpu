package se.wingez.ast

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class AstPrint {

    @Test
    fun testBasic() {
        assertEquals(
            "5",
            formatAst(
                parserFromLine("5").parseExpression().first()
            )
        )
        assertEquals(
            """
            call builtin_addition
              5
              5
        """.trimIndent(),
            formatAst(
                parserFromLine("5+5").parseExpression().first()
            )
        )
        assertEquals(
            """
            call builtin_addition
              call builtin_addition
                5
                6
              7
        """.trimIndent(),
            formatAst(
                parserFromLine("5+6+7").parseExpression().first()
            )
        )
    }

}