package se.wingez.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import se.wingez.ast.PrimitiveMemberDeclaration
import se.wingez.ast.StructNode

class BuildStructTest {

    @Test
    fun testBasicStruct() {

        assertEquals(
            StructType(
                "type1", mapOf(
                    "member1" to StructDataField("member1", 0u, byteType),
                    "member2" to StructDataField("member2", 1u, byteType),
                )
            ),
            buildStruct(
                StructNode(
                    "type1", listOf(
                        PrimitiveMemberDeclaration("member1", "byte"),
                        PrimitiveMemberDeclaration("member2", "byte"),
                    )
                ), dummyTypeContainer
            )
        )
    }

}