package ast

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AstParserType{


    @Test
    fun testBasicType(){
        assertEquals(
            TypeDefinition("test"),
        parserFromLine("test").parseTypeDefinition()
        )
    }

    @Test
    fun testArray(){
        assertEquals(
            TypeDefinition("test",isArray = true),
            parserFromLine("test[]").parseTypeDefinition()
        )
    }

    @Test
    fun testPointer(){
        assertEquals(
            TypeDefinition("test", isPointer = true),
            parserFromLine("*test").parseTypeDefinition()
        )
    }

}