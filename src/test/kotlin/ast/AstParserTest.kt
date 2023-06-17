package se.wingez.ast

import ast.expression.parseExpressionUntil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import se.wingez.*
import se.wingez.tokens.*
import java.io.StringReader

internal class AstParserTest {
    @Test
    fun testManyEOL() {
        val tokens = listOf(
            TokenEOL, TokenEOL, tidentifier("test"), TokenAssign,
            tconstant(5), TokenEOL
        )
        assertEquals(parseExpressions(tokens), listOf(assign("test", 5)))
    }

    @Test
    fun testExpressionAssignment() {
        val tokens = listOf(tidentifier("Test"), TokenAssign, tconstant(4), TokenEOL)
        val node = AstParser(tokens).parseExpression()
        assertEquals(node, listOf(assign("Test", 4)))
    }

    fun getFuncTokens(vararg parameters: String): List<Token> {
        val tokens = mutableListOf(TokenKeywordDef, tidentifier("test"), TokenLeftParenthesis)

        val numParams = parameters.size

        parameters.forEachIndexed { index, param ->
            tokens.add(tidentifier(param))
            tokens.add(TokenColon)
            tokens.add(tidentifier("byte"))
            if (index != numParams) {
                tokens.add(TokenComma)
            }
        }

        return tokens + listOf(
            TokenRightParenthesis, TokenColon, TokenEOL, TokenBeginBlock,
            tidentifier("print"), TokenLeftParenthesis, tconstant(5), TokenRightParenthesis, TokenEOL,
            TokenEndBlock
        )
    }

    private val printBody = listOf(call("print", listOf(constant(5))))


    @Test
    fun testParseFunction() {
        assertEquals(
            function("test", emptyList(), printBody, null),
            AstParser(getFuncTokens()).parseFunctionDefinition(),
        )
    }

    @Test
    fun testParseFunctionSingleParameter() {
        assertEquals(
            function(
                "test", arguments = listOf(variable("param1")),
                body = printBody, null
            ),
            AstParser(getFuncTokens("param1")).parseFunctionDefinition(),
        )
    }

    @Test
    fun testParseFunctionMultipleParameters() {
        assertEquals(
            function(
                "test", arguments = listOf(
                    variable("param1"),
                    variable("param2"),
                    variable("param3"),
                ), body = printBody, null
            ),
            AstParser(getFuncTokens("param1", "param2", "param3")).parseFunctionDefinition(),
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
            function(
                "test", arguments = listOf(variable("param", "type")),
                body = printBody, null
            ),
            AstParser(tokens).parseFunctionDefinition(),
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
            function("test", emptyList(), printBody, "byte"),
            AstParser(tokens).parseFunctionDefinition(),
        )
    }

    @Test
    fun testFunctionReturnTypeArray() {
        val program =
            """
            def test():byte[]
              print(5)
        """.trimIndent()
        assertEquals(
            AstNode.fromFunction(
                "test", FunctionType.Normal, emptyList(), printBody,
                TypeDefinition("byte", isArray = true)
            ),
            parserFromFile(program).parseFunctionDefinition()
        )
    }

    @Test
    fun testNewVariable() {
        assertEquals(
            listOf(AstNode.fromNewVariable("var", TypeDefinition("byte"), null)),
            parserFromLine("val var:byte").parseNewValDeclaration(),
        )

        assertEquals(
            listOf(
                AstNode.fromNewVariable("var", null, AstNode.fromConstant(5)),
                AstNode.fromAssign(AstNode.fromIdentifier("var"), AstNode.fromConstant(5))
            ),
            parserFromLine("val var=5").parseNewValDeclaration(),
        )
    }

    @Test
    fun testReturn() {
        assertEquals(
            AstNode.fromReturn(),
            parserFromLine("return").parseReturnStatement(),
        )

        assertEquals(
            AstNode.fromReturn(AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), identifier("a"))),
            parserFromLine("return 5+a").parseReturnStatement(),
        )
    }

    @Test
    fun testCallNoParameters() {
        assertEquals(
            listOf(call("func", emptyList())),
            parserFromLine("func()").parseExpression(),
        )
    }

    @Test
    fun testCallParameters() {
        assertEquals(
            listOf(call("func", listOf(constant(5)))),
            parserFromLine("func(5)").parseExpression(),
        )

        assertEquals(
            listOf(
                call(
                    "func", listOf(
                        constant(5),
                        constant(10),
                        identifier("test"),
                    )
                )
            ),
            parserFromLine("func(5,10,test)").parseExpression(),
        )
    }

    @Test
    fun testAssignCall() {
        assertEquals(
            listOf(AstNode.fromAssign(identifier("a"), call("test", emptyList()))),
            parserFromLine("a=test()").parseExpression(),
        )
    }

    @Test
    fun testWhile() {
        assertEquals(
            AstNode.fromWhile(constant(1), printBody),
            parserFromFile(
                """
          while 1:
            print(5)
        """
            ).parseWhileStatement(),
        )
    }

    @Test
    fun testIf() {
        assertEquals(
            AstNode.fromIf(constant(1), printBody, emptyList()),
            parserFromFile(
                """
            if 1:
              print(5)
        """
            ).parseIfStatement(),
        )
        assertEquals(
            AstNode.fromIf(
                AstNode.fromBinaryOperation(
                    TokenType.NotEqual,
                    AstNode.fromBinaryOperation(TokenType.MinusSign, identifier("a"), constant(2)),
                    constant(0)
                ),
                listOf(call("print", listOf(constant(5)))),
                emptyList()
            ),
            parserFromFile(
                """
            if (a-2)!=0:
              print(5)
            """
            ).parseIfStatement(),
        )
    }

    @Test
    fun testIfElse() {
        assertEquals(
            AstNode.fromIf(
                identifier("a"), printBody,
                listOf(call("print", listOf(constant(0))))
            ),
            parserFromFile(
                """
            if a:
              print(5)
            else:
              print(0)
    
        """
            ).parseIfStatement(),
        )
    }

    @Test
    fun testComplexCondition() {
        assertDoesNotThrow {
            parserFromFile(
                """
            while (counter) != (arr - size):
              print(arr[counter])
            
        """
            ).parseWhileStatement()
        }
    }

    @Test
    fun testString() {
        assertEquals(
            listOf(
                AstNode.fromBinaryOperation(
                    TokenType.PlusSign,
                    AstNode.fromConstant(5),
                    AstNode.fromString("hello"),

                    )
            ),
            parserFromLine(
                "5+\"hello\""

            ).parseExpression(),
        )
    }

    @Test
    fun testCallFunctionType() {
        assertEquals(
            listOf(
                AstNode.fromCall("add", FunctionType.Normal, List(2) {
                    AstNode.fromConstant(5)
                })
            ),
            parserFromLine("add(5,5)").parseExpression(),
        )
        assertEquals(
            listOf(
                AstNode.fromCall("add", FunctionType.Operator, List(2) {
                    AstNode.fromConstant(5)
                })
            ),
            parserFromLine("5+5").parseExpression(),
        )
    }

    @Test
    fun testParseInstanceFunction() {
        assertEquals(
            AstNode.fromFunction(
                "hello", FunctionType.Instance, listOf(
                    AstNode.fromNewVariable(
                        "this",
                        TypeDefinition("int"),
                        null
                    )
                ), listOf(AstNode.fromCall("call", FunctionType.Normal, emptyList())), null
            ),
            parserFromFile(
                """
                def (this:int) hello():
                  call()
            """.trimIndent()
            ).parseFunctionDefinition()
        )
    }

    @Test
    fun testParseInstanceFunctionCall() {
        assertEquals(
            AstNode.fromCall("hello", FunctionType.Instance, listOf(constant(5))),
            parseExpression("5.hello()")
        )
    }

    @Test
    fun testInstanceFunctionComplexInstance() {
        assertEquals(
            AstNode.fromCall(
                "hello", FunctionType.Instance,
                listOf(AstNode.fromCall("add", FunctionType.Operator, listOf(constant(5), constant(6))))
            ),
            parseExpression("(5+6).hello()")
        )
    }
}