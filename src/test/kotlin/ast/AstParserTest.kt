package se.wingez.ast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import se.wingez.tokens.*
import java.io.StringReader

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

private fun assign(to: String, value: Int): AstNode {
    return AstNode.fromAssign(identifier(to), constant(value))
}

fun value(text: String): AstNode {
    return parserFromFile(text).parseValueProvider()
}

fun identifier(name: String): AstNode {
    return AstNode.fromIdentifier(name)
}

fun constant(value: Int): AstNode {
    return AstNode.fromConstant(value)
}

fun variable(name: String, type: String, explicitNew: Boolean = false, isArray: Boolean = false): AstNode {
    return AstNode.fromMemberDeclaration(MemberDeclarationData(name, type, explicitNew, isArray))
}

fun call(target: String, parameters: List<AstNode>): AstNode {
    return AstNode.fromCall(target, parameters)
}

internal class AstParserTest {
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
        assertEquals(node, AstNode.fromPrint(constant(5)))
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

    private val printBody = listOf(AstNode.fromPrint(constant(5)))


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
                "test", arguments = listOf(variable("param1", "")),
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
                    variable("param1", ""),
                    variable("param2", ""),
                    variable("param3", ""),
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
                "test", arguments = listOf(variable("param", "type")),
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
            ReturnNode(AstNode.fromOperation(NodeTypes.Addition, constant(5), identifier("a")))
        )
    }

    @Test
    fun testCallNoParameters() {
        assertEquals(
            parserFromLine("func()").parseCall(true),
            call("func", emptyList())
        )
    }

    @Test
    fun testCallParameters() {
        assertEquals(
            parserFromLine("func(5)").parseCall(true),
            call("func", listOf(constant(5)))
        )

        assertEquals(
            parserFromLine("func(5,10,test)").parseCall(true),
            call(
                "func", listOf(
                    constant(5),
                    constant(10),
                    identifier("test"),
                )
            )
        )
    }

    @Test
    fun testAssignCall() {
        assertEquals(
            parserFromLine("a=test()").parseAssignment(),
            AstNode.fromAssign(identifier("a"), call("test", emptyList()))
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
            WhileNode(constant(1), printBody)
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
            IfNode(constant(1), printBody, emptyList())
        )
        assertEquals(
            parserFromFile(
                """
            if (a-2)!=0:
              print(5)
            """
            ).parseIfStatement(),
            IfNode(
                AstNode.fromOperation(
                    NodeTypes.NotEquals,
                    AstNode.fromOperation(NodeTypes.Subtraction, identifier("a"), constant(2)),
                    constant(0)
                ),
                listOf(AstNode.fromPrint(constant(5))),
                emptyList()
            )
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
                identifier("a"), printBody,
                listOf(AstNode.fromPrint(constant(0)))
            )
        )
    }

    @Test
    fun testComplexCondition() {
        assertDoesNotThrow {
            parserFromFile(
                """
            while (counter) != (arr -> size):
              print(arr[counter])
            
        """
            ).parseWhileStatement()
        }
    }
}