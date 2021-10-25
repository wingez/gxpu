package se.wingez.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AstParserStructTest {

    @Test
    fun testStructBasic() {

        assertEquals(parserFromFile("""
        struct tmp:
          member1
          member2
            
        """).parseStruct(), StructNode("tmp", listOf(
                AssignTarget(MemberAccess("member1")),
                AssignTarget(MemberAccess("member2"))
        )))
        assertEquals(parserFromFile("""
        struct tmp:
          member1:byte
          member2:int
            
        """).parseStruct(), StructNode("tmp", listOf(
                AssignTarget(MemberAccess("member1"), "byte"),
                AssignTarget(MemberAccess("member2"), "int")
        )))

        assertEquals(parserFromFile("""
        struct tmp:
          member1:new int
            
        """).parseStruct(), StructNode("tmp", listOf(
                AssignTarget(MemberAccess("member1"), "int", true),
        )))
    }

    @Test
    fun testStructAssign() {
        assertEquals(parserFromFile("""
        member.i=5
            
        """).parseAssignment(), AssignNode(AssignTarget(MemberAccess("member", listOf(MemberAccessModifier("i")))),
                ConstantNode(5)))
    }

    @Test
    fun testStructAssignInFunction() {
        assertEquals(parserFromFile("""
        def main():
          a: type1
      
          a.member1=2
          a.member2=1
        """).parseFunctionDefinition(),
                FunctionNode("main", emptyList(), listOf(
                        AssignNode(AssignTarget(MemberAccess("a"), "type1"), null),
                        AssignNode(AssignTarget(MemberAccess("a", listOf(MemberAccessModifier("member1")))),
                                ConstantNode(2)),
                        AssignNode(AssignTarget(MemberAccess("a", listOf(MemberAccessModifier("member2")))),
                                ConstantNode(1)),
                ), "void"))
    }

    @Test
    fun testStructMemberRead() {
        assertEquals(parserFromFile("""
        a=s.member
        """).parseAssignment(), AssignNode(AssignTarget(MemberAccess("a")),
                MemberAccess("s", listOf(MemberAccessModifier("member")))))
    }
}