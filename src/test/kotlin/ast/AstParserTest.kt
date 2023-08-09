package ast

import TokenAssign
import TokenBeginBlock
import TokenColon
import TokenComma
import TokenEOL
import TokenEndBlock
import TokenKeywordDef
import TokenLeftParenthesis
import TokenRightParenthesis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import tconstant
import tidentifier
import tokenizeLines
import tokens.*
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
        val tokens = tokenizeLines(
            """
            def test(param:type):
              print(5)
        """
        )
        assertEqualsIgnoreSource(
            function(
                "test", arguments = listOf(variable("param", "type")),
                body = printBody, null
            ),
            AstParser(tokens).parseFunctionDefinition(),
        )
    }

    @Test
    fun testFunctionReturnType() {
        val tokens = tokenizeLines(
            """
    def test():byte
      print(5)
    """
        )
        assertEqualsIgnoreSource(
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
        assertEqualsIgnoreSource(
            AstNode.fromFunction(
                "test", FunctionType.Normal, emptyList(), printBody,
                TypeDefinition.normal("byte", listOf(TypeDefinitionModifier.Array)), na
            ),
            parserFromFile(program).parseFunctionDefinition()
        )
    }

    @Test
    fun testNewVariable() {
        assertEqualsIgnoreSource(
            listOf(AstNode.fromNewVariable("var", TypeDefinition.normal("byte", emptyList()), null, na)),
            parserFromLine("val var:byte").parseNewValDeclaration(),
        )

        assertEqualsIgnoreSource(
            listOf(
                AstNode.fromNewVariable("var", null, constant(5), na),
                AstNode.fromAssign(identifier("var"), constant(5), na)
            ),
            parserFromLine("val var=5").parseNewValDeclaration(),
        )
    }

    @Test
    fun testReturn() {
        assertEquals(
            AstNode.fromReturn(null, na),
            parserFromLine("return").parseReturnStatement(),
        )

        assertEquals(
            AstNode.fromReturn(AstNode.fromBinaryOperation(TokenType.PlusSign, constant(5), identifier("a"), na), na),
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
            listOf(AstNode.fromAssign(identifier("a"), call("test", emptyList()), na)),
            parserFromLine("a=test()").parseExpression(),
        )
    }

    @Test
    fun testWhile() {
        assertEqualsIgnoreSource(
            AstNode.fromWhile(constant(1), printBody, na),
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
        assertEqualsIgnoreSource(
            AstNode.fromIf(constant(1), printBody, emptyList(), na),
            parserFromFile(
                """
            if 1:
              print(5)
        """
            ).parseIfStatement(),
        )
        assertEqualsIgnoreSource(
            AstNode.fromIf(
                AstNode.fromBinaryOperation(
                    TokenType.NotEqual,
                    AstNode.fromBinaryOperation(TokenType.MinusSign, identifier("a"), constant(2), na),
                    constant(0), na
                ),
                listOf(call("print", listOf(constant(5)))),
                emptyList(), na
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
        assertEqualsIgnoreSource(
            AstNode.fromIf(
                identifier("a"), printBody,
                listOf(call("print", listOf(constant(0)))), na
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
                    constant(5),
                    AstNode.fromString("hello", na),
                    na,
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
                    constant(5)
                }, na)
            ),
            parserFromLine("add(5,5)").parseExpression(),
        )
        assertEquals(
            listOf(
                AstNode.fromCall("add", FunctionType.Operator, List(2) {
                    constant(5)
                }, na)
            ),
            parserFromLine("5+5").parseExpression(),
        )
    }

    @Test
    fun testParseInstanceFunction() {
        assertEqualsIgnoreSource(
            AstNode.fromFunction(
                "hello", FunctionType.Instance, listOf(
                    AstNode.fromNewVariable(
                        "this",
                        TypeDefinition.normal("int", emptyList()),
                        null, na
                    )
                ), listOf(AstNode.fromCall("call", FunctionType.Normal, emptyList(), na)), null, na
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
            AstNode.fromCall("hello", FunctionType.Instance, listOf(constant(5)), na),
            parseExpression("5.hello()")
        )
    }

    @Test
    fun testInstanceFunctionComplexInstance() {
        assertEquals(
            AstNode.fromCall(
                "hello", FunctionType.Instance,
                listOf(AstNode.fromCall("add", FunctionType.Operator, listOf(constant(5), constant(6)), na)), na
            ),
            parseExpression("(5+6).hello()")
        )
    }

    @Test
    fun testParseImport() {
        assertEqualsIgnoreSource(
            AstNode.fromImport("test",na),
            parserFromFile(
                """
                import test
            """.trimIndent()
            ).parseImport()
        )
    }

}