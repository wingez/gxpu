package se.wingez.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
            ).parseStruct(), StructNode(
                "tmp", listOf(
                    PrimitiveMemberDeclaration("member1", ""),
                    PrimitiveMemberDeclaration("member2", "")
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
            ).parseStruct(), StructNode(
                "tmp", listOf(
                    PrimitiveMemberDeclaration("member1", "byte"),
                    PrimitiveMemberDeclaration("member2", "int"),
                )
            )
        )

        assertEquals(
            parserFromFile(
                """
        struct tmp:
          member1:new int
            
        """
            ).parseStruct(), StructNode(
                "tmp", listOf(
                    PrimitiveMemberDeclaration("member1", "int", true),
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
            ).parseAssignment(), AssignNode(
                MemberAccess(identifier("member"), "i"),
                ConstantNode(5)
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
            FunctionNode(
                "main", emptyList(),
                listOf(
                    PrimitiveMemberDeclaration("a", "type1"),
                    AssignNode(
                        MemberAccess(identifier("a"), "member1"),
                        ConstantNode(2)
                    ),
                    AssignNode(
                        MemberAccess(identifier("a"), "member2"),
                        ConstantNode(1)
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
            AssignNode(
                identifier("a"),
                MemberAccess(identifier("s"), "member")
            ),
        )
    }

    @Test
    fun testStructMemberDeref() {
        assertEquals(
            AssignNode(
                MemberDeref(identifier("a"), "test"),
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
            ).parseStruct(), StructNode(
                "tmp", listOf(
                    PrimitiveMemberDeclaration("member1", "byte", isArray = true),
                    PrimitiveMemberDeclaration("member2", "str", isArray = true),
                )
            )
        )
    }


    @Test
    fun testArrayAccess() {
        assertEquals(
            ArrayAccess(identifier("test"), ConstantNode(5)),
            parserFromLine("test[5]").parseValueProvider()
        )

        assertEquals(
            ArrayAccess(
                AstNode.fromIdentifier("test"),
                AstNode.fromOperation(NodeTypes.Addition, ConstantNode(5), ConstantNode(5))
            ),
            parserFromLine("test[5+5]").parseValueProvider()
        )
    }
}