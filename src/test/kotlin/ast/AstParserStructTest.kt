package ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

fun struct(name: String, members: List<AstNode>): AstNode {
    return AstNode.fromStruct(name, members, na)
}

fun memberAccess(v: AstNode, member: String): AstNode {
    return AstNode(NodeTypes.MemberAccess, member, listOf(v), na)
}

class AstParserStructTest {

    @Test
    fun testStructBasic() {

        assertEqualsIgnoreSource(
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

        assertEqualsIgnoreSource(
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
                    constant(5),
                    na
                )
            ),
            parserFromFile(
                """
        member.i=5
        """
            ).parseExpression().ignoreSource(),
        )
    }

    @Test
    fun testStructAssignInFunction() {
        assertEqualsIgnoreSource(
            function(
                "main", emptyList(),
                listOf(
                    variable("a", "type1"),
                    AstNode.fromAssign(
                        memberAccess(identifier("a"), "member1"),
                        constant(2),
                        na,
                    ),
                    AstNode.fromAssign(
                        memberAccess(identifier("a"), "member2"),
                        constant(1),
                        na,
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
                    memberAccess(identifier("s"), "member"),
                    na,
                )
            ),
            parserFromFile(
                """
        a=s.member
        """
            ).parseExpression().ignoreSource(),
        )
    }

    @Test
    fun testStructMemberDeref() {
        assertEquals(
            listOf(
                AstNode.fromAssign(
                    AstNode.fromDeref(
                        AstNode.fromMemberAccess(identifier("a"), "test", na), na
                    ),
                    identifier("s"),
                    na,
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
                    "b", na
                ), na
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

        assertEqualsIgnoreSource(
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