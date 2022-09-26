package se.wingez.astwalker
import org.junit.jupiter.api.Test
import se.wingez.ast.function
import se.wingez.ast.parserFromLine
import kotlin.test.assertEquals


internal class WalkerTest{


    @Test
    fun testWalker(){

        val function = function("main", emptyList(),
            parserFromLine("print(5)").parseExpression(),
            "returntype"
        )

        val output = WalkerOutput()

        walk(function,output)
        
        assertEquals(listOf("5"),output.result)

    }
    @Test
    fun testWalkerAddition(){

        val function = function("main", emptyList(),
            parserFromLine("print(5+5)").parseExpression(),
            "returntype"
        )

        val output = WalkerOutput()

        walk(function,output)

        assertEquals(listOf("10"),output.result)

    }


}