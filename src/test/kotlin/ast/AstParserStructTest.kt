package se.wingez.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

fun struct(name: String, members: List<AstNode>): AstNode {
    return AstNode.fromStruct(name, members)
}

fun memberAccess(v: AstNode, member: String): AstNode {
    return AstNode(NodeTypes.MemberAccess, member, listOf(v))
}

fun memberDeref(v: AstNode, member: String): AstNode {
    return AstNode(NodeTypes.MemberDeref, member, listOf(v))
}

class AstParserStructTest {

    @Test
    fun testStructBasic() {

        assertEquals(
            parserFromFile(
                """
        struct tmp:
          member1
          member2
            
        """
            ).parseStruct(), struct(
                "tmp", listOf(
                    variable("member1", ""),
                    variable("member2", "")
                )
            )
        )
        assertEquals(
            parserFromFile(
                """
        struct tmp:
          member1:byte
          member2:int
            
        """
            ).parseStruct(), struct(
                "tmp", listOf(
                    variable("member1", "byte"),
                    variable("member2", "int"),
                )
            )
        )

        assertEquals(
            parserFromFile(
                """
        struct tmp:
          member1:new int
            
        """
            ).parseStruct(), struct(
                "tmp", listOf(
                    variable("member1", "int", explicitNew = true),
                )
            )
        )
    }


    @Test
    fun testStructAssign() {
        assertEquals(
            parserFromFile(
                """
        member.i=5
            
        """
            ).parseAssignment(), AstNode.fromAssign(
                memberAccess(identifier("member"), "i"),
                constant(5)
            )
        )
    }

    @Test
    fun testStructAssignInFunction() {
        assertEquals(
            parserFromFile(
                """
        def main():
          a: type1
      
          a.member1=2
          a.member2=1
        """
            ).parseFunctionDefinition(),
            function(
                "main", emptyList(),
                listOf(
                    variable("a", "type1"),
                    AstNode.fromAssign(
                        memberAccess(identifier("a"), "member1"),
                        constant(2)
                    ),
                    AstNode.fromAssign(
                        memberAccess(identifier("a"), "member2"),
                        constant(1)
                    )
                ), "void"
            )
        )
    }

    @Test
    fun testStructMemberRead() {
        assertEquals(
            parserFromFile(
                """
        a=s.member
        """
            ).parseAssignment(),
            AstNode.fromAssign(
                identifier("a"),
                memberAccess(identifier("s"), "member")
            ),
        )
    }

    @Test
    fun testStructMemberDeref() {
        assertEquals(
            AstNode.fromAssign(
                memberDeref(identifier("a"), "test"),
                identifier("s")
            ),
            parserFromLine("a->test = s").parseAssignment()
        )
    }

    @Test
    fun testStructArray() {

        assertEquals(
            parserFromFile(
                """
        struct tmp:
          member1: byte[]
          member2: str[]
            
        """
            ).parseStruct(), struct(
                "tmp", listOf(
                    variable("member1", "byte", isArray = true),
                    variable("member2", "str", isArray = true),
                )
            )
        )
    }


    @Test
    fun testArrayAccess() {
        assertEquals(
            AstNode.fromArrayAccess(identifier("test"), constant(5)),
            parserFromLine("test[5]").parseValueProvider()
        )

        assertEquals(
            AstNode.fromArrayAccess(
                AstNode.fromIdentifier("test"),
                AstNode.fromOperation(NodeTypes.Addition, constant(5), constant(5))
            ),
            parserFromLine("test[5+5]").parseValueProvider()
        )
    }
}