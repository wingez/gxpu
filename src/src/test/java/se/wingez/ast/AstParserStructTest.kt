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
                SingleOperationNode(
                    Operation.MemberAccess,
                    Identifier("member"),
                    Identifier("i")
                ),
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
                        SingleOperationNode(
                            Operation.MemberAccess, Identifier("a"), Identifier("member1")
                        ),
                        ConstantNode(2)
                    ),
                    AssignNode(
                        SingleOperationNode(
                            Operation.MemberAccess, Identifier("a"), Identifier("member2")
                        ),
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
            ).parseAssignment(), AssignNode(
                Identifier("a"),
                SingleOperationNode(
                    Operation.MemberAccess,
                    Identifier("s"), Identifier("member")
                ),
            )
        )
    }
}