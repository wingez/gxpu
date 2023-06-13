package se.wingez.compiler

import compiler.backends.emulator.*
import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.Datatype
import compiler.frontend.StructBuilder
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import se.wingez.ast.*
import se.wingez.byte
import se.wingez.compiler.frontend.FunctionDefinition
import se.wingez.compiler.frontend.FunctionDefinitionResolver
import se.wingez.tokens.parseFile
import java.io.StringReader
import kotlin.test.assertEquals


class DummyBuiltInProvider(
    private val builtIns: List<BuiltIn> = listOf(Print())//, ByteAddition(), ByteSubtraction()) TODO:
) : BuiltInProvider, FunctionDefinitionResolver {
    override fun getSignatures(): List<FunctionDefinition> {
        return builtIns.map { it.signature }
    }

    override fun getTypes(): Map<String, Datatype> {
        return mapOf(
            "void" to Datatype.Void,
            "byte" to Datatype.Integer,
        )
    }

    override fun buildSignature(signature: FunctionDefinition): BuiltFunction {
        for (builtIn in builtIns) {
            if (builtIn.signature == signature) {

                val generator = CodeGenerator()
                builtIn.compile(generator)

                throw NotImplementedError()
                //FIXME
                //return BuiltFunction(builtIn.signature, generator, StructBuilder().getStruct("dummy"), 0)
            }
        }
        throw AssertionError()
    }

    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition {
        for (builtIn in builtIns) {
            if (builtIn.signature.matches(name,functionType, parameterTypes)) {
                return builtIn.signature
            }
        }

        throw AssertionError()
    }
}

fun buildSingleMainFunction(nodes: List<AstNode>): List<UByte> {
    val node = function("main", emptyList(), nodes, "")
    val c = Compiler(DummyBuiltInProvider(), listOf(node))
    return c.buildProgram().code
}

fun buildBody(body: String): List<UByte> {
    val tokens = parseFile(StringReader(body))
    val nodes = parseExpressions(tokens)


    val node = function("main", emptyList(), nodes, "void")
    val signature = FunctionDefinition.fromFunctionNode(node, dummyTypeContainer)

    val builtFunction = buildFunctionBody(node, signature, DummyBuiltInProvider(), dummyTypeContainer, dummyDatatypeSizeProvider)

    builtFunction.generator.applyLinks(object : LinkAddressProvider {
        override fun getFunctionAddress(signature: FunctionDefinition): Int {
            return 0
        }

        override fun getFunctionVarsSize(signature: FunctionDefinition): Int {
            return 0
        }

    })

    return builtFunction.generator.getResultingCode()
}

fun buildProgram(body: String): List<UByte> {
    val nodes = parserFromFile(body).parse()

    val c = Compiler(DummyBuiltInProvider(), nodes)
    val program = c.buildProgram()

    return program.code
}

fun shouldMatch(code: List<UByte>, expected: List<UByte>) {
    assertEquals(
        code, expected,
        (listOf("Disassembled: ") + DefaultEmulator().instructionSet.disassemble(code)).joinToString(
            "\n"
        )
    )
}

fun bodyShouldMatchAssembled(body: String, expectedAssembly: String) {

    val code = buildBody(body)
    val expected = DefaultEmulator().instructionSet.assembleMnemonicFile(StringReader(expectedAssembly))

    shouldMatch(code, expected)
}

fun programShouldMatchAssembled(program: String, expectedAssembly: String) {
    val code = buildProgram(program)
    val expected = DefaultEmulator().instructionSet.assembleMnemonicFile(StringReader(expectedAssembly))
    shouldMatch(code, expected)
}

class CompilerTest {
    @Test
    fun testFpOffset() {


        /**
        #Expected
        ldfp #255
        ldsp #255
        subsp #0
        subsp #0
        call #7
        exit

        ret
         */

        val code = buildSingleMainFunction(emptyList())
        assertEquals(
            listOf(
                // Init stack and frame
                DefaultEmulator.ldfp.id, byte(255),
                DefaultEmulator.ldsp.id, byte(255),
                // Place local vars
                DefaultEmulator.sub_sp.id, byte(0),
                // Call
                DefaultEmulator.call_addr.id, byte(9),
                // On return
                DefaultEmulator.exit.id,
                //Start of function
                DefaultEmulator.ret.id,
            ), code
        )
    }

    @Test
    fun testBasicAssign() {

        val expected = """
        push #5
        pop [FP #0]
        ret
        """

        val body = """
          val var:byte=5
        """
        bodyShouldMatchAssembled(body, expected)

    }

    @Test
    fun testCopy() {
        val expected = """
        PUSH #5
        POP[FP #2]
        PUSH [FP #2]
        POP [FP #3]
        RET
        """

        val body = """
          val val1:byte=5
          val val2:byte=val1
        """
        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testPrint() {
        val expected = """
        // main
        push #5
        call #0
        subsp #1
        ret
        """

        val body = """
          print(5)
        """
        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testIfNoElse() {
        val expected = """
        PUSH #5
        TST POP
       
        jmpz #11
        PUSH #1
        CALL #0
        ADDSP #1
        RET
        """
        val body = """
          if 5:
            print(1)
        """
        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testIfElse() {
        val expected = """
        PUSH #5
        TST POP
        JMPZ #13
        PUSH #1
        CALL #0
        ADDSP #1
        JMP #19
        PUSH #2
        CALL #0
        ADDSP #1
        RET
        """
        val body = """
          if 5:
            print(1)
          else:
            print(2)
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testWhile() {
        val expected = """
        PUSH #5
        TST POP
        JMPZ #13
        PUSH #1
        CALL #0
        ADDSP #1
        JMP #0
        RET 
        """
        val body = """
          while 5:
            print(1)
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testCall() {
        val expected = """
        LDFP #255
        LDSP #255
        SUBSP #0
        CALL #20
        exit
        // print
        LDA [FP #2]
        OUT
        RET
        
       // test1 
        PUSH #10
        CALL #9
        ADDSP #1
        RET
        //main
        SUBSP #0
        CALL #13
        ADDSP #0
        PUSH #3
        CALL #9
        ADDSP #1
        RET
        """
        val body = """
          def test1():
            print(10)
            
          def main():
            test1()
            print(3)
        """

        programShouldMatchAssembled(body, expected)
    }

    @Test
    @Disabled("Not implemented yet")
    fun testPointerDeref() {
        val expected = """
        LDFP #255
        LDSP #255
        SUBSP #1
        CALL #24
        exit
        # test1 
        LDA FP #0
        ADDA #2
        LDA [A #0]
        ADDA #0
        PUSHA
        LDA [[SP #0]]
        ADDSP #1
        out
        RET
        
        # main
        LDA FP #0
        ADDA #2
        ADDA #0
        PUSHA
        LDA #11
        STA [[SP #0]]
        
        ADDSP #1
        SUBSP #0
        SUBSP #0

        LDA FP #0
        ADDA #2
        PUSHA
        
        CALL #9
        ADDSP #1
        
        ret
         
        """
        val body = """
          struct type:
            val1:byte
           
          def toCall(param:type):
            print(param->val1)
          
          def main():
            a:new type
            
            a.val1=11
            
            toCall(a)
        """

        programShouldMatchAssembled(body, expected)
    }
}