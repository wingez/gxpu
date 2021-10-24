package se.wingez.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import se.wingez.*
import java.io.StringReader

internal class AstParserTest {

    companion object {

        fun parse(tokens: List<Token>): List<AstNode> {
            return AstParser(tokens).parse()
        }

        fun parserFromLine(line: String): AstParser {
            return AstParser(parseLine(line))
        }

        fun parserFromFile(file: String): AstParser {
            return AstParser(parseFile(StringReader(file)))
        }

        fun parseExpressions(tokens: List<Token>): List<AstNode> {
            return AstParser(tokens + listOf(TokenEndBlock)).parseStatementsUntilEndblock()
        }

        fun assign(to: String, value: Int): AssignNode {
            return AssignNode(AssignTarget(MemberAccess(to)), ConstantNode(value))
        }
    }


    @Test
    fun testManyEOL() {
        val tokens = listOf(
            TokenEOL, TokenEOL, TokenIdentifier("test"), TokenAssign,
            TokenNumericConstant(5), TokenEOL
        )
        assertEquals(parseExpressions(tokens), listOf(assign("test", 5)))
    }

    @Test
    fun testExpressionAssignment() {
        val tokens = listOf(TokenIdentifier("Test"), TokenAssign, TokenNumericConstant(4), TokenEOL)
        val node = AstParser(tokens).parseStatement()
        assertEquals(node, assign("Test", 4))
    }

    @Test
    fun testExpressionPrint() {
        val node = AstParser(parseLine("print(5)")).parseStatement()
        assertEquals(node, PrintNode(ConstantNode(5)))
    }

    fun getFuncTokens(vararg parameters: String): List<Token> {
        val tokens = mutableListOf(TokenKeywordDef, TokenIdentifier("test"), TokenLeftParenthesis)

        val numParams = parameters.size

        parameters.forEachIndexed { index, param ->
            tokens.add(TokenIdentifier(param))
            if (index != numParams) {
                tokens.add(TokenComma)
            }
        }

        return tokens + listOf(
            TokenRightParenthesis, TokenColon, TokenEOL, TokenBeginBlock,
            TokenKeywordPrint, TokenLeftParenthesis, TokenNumericConstant(5), TokenRightParenthesis, TokenEOL,
            TokenEndBlock
        )
    }

    private val printBody = listOf(PrintNode(ConstantNode(5)))


    @Test
    fun testParseFunction() {
        assertEquals(
            AstParser(getFuncTokens()).parseFunctionDefinition(),
            FunctionNode("test", emptyList(), printBody, "void")
        )
    }

    @Test
    fun testParseFunctionSingleParameter() {
        assertEquals(
            AstParser(getFuncTokens("param1")).parseFunctionDefinition(),
            FunctionNode(
                "test", arguments = listOf(AssignTarget(MemberAccess("param1"))),
                body = printBody, "void"
            )
        )
    }

    @Test
    fun testParseFunctionMultipleParameters() {
        assertEquals(
            AstParser(getFuncTokens("param1", "param2", "param3")).parseFunctionDefinition(),
            FunctionNode(
                "test", arguments = listOf(
                    AssignTarget(MemberAccess("param1")),
                    AssignTarget(MemberAccess("param2")),
                    AssignTarget(MemberAccess("param3")),
                ), body = printBody, "void"
            )
        )
    }

    @Test
    fun testParseFunctionParameterType() {
        val tokens = parseFile(
            StringReader(
                """
            def test(param:type):
              print(5)
        """
            )
        )
        assertEquals(
            AstParser(tokens).parseFunctionDefinition(),
            FunctionNode(
                "test", arguments = listOf(AssignTarget(MemberAccess("param"), "type")),
                body = printBody, "void"
            )
        )
    }

    @Test
    fun testFunctionReturnType() {
        val tokens = parseFile(
            StringReader(
                """
    def test():byte
      print(5)
    """
            )
        )
        assertEquals(
            AstParser(tokens).parseFunctionDefinition(),
            FunctionNode("test", emptyList(), printBody, "byte")
        )
    }

    @Test
    fun testReturn() {
        assertEquals(
            parserFromLine("return").parseReturnStatement(),
            ReturnNode()
        )

        assertEquals(
            parserFromLine("return 5+a").parseReturnStatement(),
            ReturnNode(SingleOperationNode(Operation.Plus, ConstantNode(5), MemberAccess("a")))
        )
    }

    @Test
    fun testCallNoParameters() {
        assertEquals(
            parserFromLine("func()").parseCall(),
            CallNode("func", emptyList())
        )
    }

    @Test
    fun testCallParameters() {
        assertEquals(
            parserFromLine("func(5)").parseCall(),
            CallNode("func", listOf(ConstantNode(5)))
        )

        assertEquals(
            parserFromLine("func(5,10,test)").parseCall(),
            CallNode(
                "func", listOf(
                    ConstantNode(5),
                    ConstantNode(10),
                    MemberAccess("test"),
                )
            )
        )
    }

    @Test
    fun testAssignCall() {
        assertEquals(
            parserFromLine("a=test()").parseAssignment(),
            AssignNode(AssignTarget(MemberAccess("a")), CallNode("test", emptyList()))
        )
    }

    @Test
    fun testWhile() {
        assertEquals(
            parserFromFile(
                """
          while 1:
            print(5)
        """
            ).parseWhileStatement(),
            WhileNode(ConstantNode(1), printBody)
        )
    }

    @Test
    fun testIf() {
        assertEquals(
            parserFromFile(
                """
            if 1:
              print(5)
        """
            ).parseIfStatement(),
            IfNode(ConstantNode(1), printBody, emptyList())
        )
    }

    @Test
    fun testIfElse() {
        assertEquals(
            parserFromFile(
                """
            if a:
              print(5)
            else:
              print(0)
    
        """
            ).parseIfStatement(),
            IfNode(
                MemberAccess("a"), printBody,
                listOf(PrintNode(ConstantNode(0)))
            )
        )
    }
}