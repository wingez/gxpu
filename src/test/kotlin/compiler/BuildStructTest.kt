package se.wingez.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import se.wingez.ast.struct
import se.wingez.ast.variable

class BuildStructTest {

    @Test
    fun testBasicStruct() {

        assertEquals(
            StructType(
                "type1", mapOf(
                    "member1" to StructDataField("member1", 0, byteType),
                    "member2" to StructDataField("member2", 1, byteType),
                )
            ),
            buildStruct(
                struct(
                    "type1", listOf(
                        variable("member1", "byte"),
                        variable("member2", "byte"),
                    )
                ), dummyTypeContainer
            )
        )
    }

}