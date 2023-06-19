package se.wingez.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

fun struct(name: String, members: List<AstNode>): AstNode {
    return AstNode.fromStruct(name, members)
}

fun memberAccess(v: AstNode, member: String): AstNode {
    return AstNode(NodeTypes.MemberAccess, member, listOf(v))
}

class AstParserStructTest {

    @Test
    fun testStructBasic() {

        assertEquals(
            struct(
                "tmp", listOf(
                    variable("member1"),
                    variable("member2")
                )
            ),
            parserFromFile(
                """
        struct tmp:
          member1:byte
          member2:byte
        """
            ).parseStruct(),
        )

        assertEquals(
            struct(
                "tmp", listOf(
                    variable("member1", "byte"),
                    variable("member2", "int"),
                )
            ),
            parserFromFile(
                """
        struct tmp:
          member1:byte
          member2:int
        """
            ).parseStruct(),
        )
    }


    @Test
    fun testStructAssign() {
        assertEquals(
            listOf(
                AstNode.fromAssign(
                    memberAccess(identifier("member"), "i"),
                    constant(5)
                )
            ),
            parserFromFile(
                """
        member.i=5
        """
            ).parseExpression(),
        )
    }

    @Test
    fun testStructAssignInFunction() {
        assertEquals(
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
                ), null
            ),

            parserFromFile(
                """
        def main():
          val a: type1
      
          a.member1=2
          a.member2=1
        """
            ).parseFunctionDefinition(),
        )
    }

    @Test
    fun testStructMemberRead() {
        assertEquals(
            listOf(
                AstNode.fromAssign(
                    identifier("a"),
                    memberAccess(identifier("s"), "member")
                )
            ),
            parserFromFile(
                """
        a=s.member
        """
            ).parseExpression(),
        )
    }

    @Test
    fun testStructMemberDeref() {
        assertEquals(
            listOf(
                AstNode.fromAssign(
                    AstNode.fromDeref(
                        AstNode.fromMemberAccess(identifier("a"), "test")
                    ),
                    identifier("s")
                )
            ),
            parserFromLine("*a.test = s").parseExpression()
        )
    }

    @Test
    fun testArrowDeref() {
        assertEquals(
            AstNode.fromDeref(
                AstNode.fromMemberAccess(
                    identifier("a"),
                    "b"
                )
            ),
            parseExpression("*a.b")
        )
        assertEquals(
            parseExpression("a->b"),
            parseExpression("(*a).b")
        )
    }


    @Test
    fun testStructArray() {

        assertEquals(
            struct(
                "tmp", listOf(
                    variable("member1", "byte", isArray = true),
                    variable("member2", "str", isArray = true),
                )
            ),
            parserFromFile(
                """
        struct tmp:
          member1: byte[]
          member2: str[]
            
        """
            ).parseStruct(),
        )
    }
}