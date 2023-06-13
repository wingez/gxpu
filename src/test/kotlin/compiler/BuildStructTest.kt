package se.wingez.compiler

import compiler.backends.emulator.StructDataField
import compiler.backends.emulator.StructType
import compiler.backends.emulator.byteType
import compiler.frontend.Datatype
import compiler.frontend.buildStruct
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
                    "member1" to StructDataField("member1", Datatype.Integer, 0, 1),
                    "member2" to StructDataField("member2", Datatype.Integer, 1, 1),
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