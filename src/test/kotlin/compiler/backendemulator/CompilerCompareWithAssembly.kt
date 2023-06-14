package compiler.backendemulator

import compiler.backends.emulator.*
import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.Datatype
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import se.wingez.ast.*
import se.wingez.compiler.backends.emulator.EmulatorInstruction
import se.wingez.compiler.backends.emulator.Reference
import se.wingez.compiler.backends.emulator.emulate
import se.wingez.compiler.dummyTypeContainer
import se.wingez.compiler.frontend.*
import se.wingez.tokens.parseFile
import java.io.StringReader
import kotlin.test.assertEquals


class DummyBuiltInProvider(
    private val builtIns: List<BuiltIn> = listOf(Print(), Bool(), ByteAddition(), ByteSubtraction())
) : BuiltInProvider, FunctionDefinitionResolver {
    override fun getSignatures(): List<FunctionDefinition> {
        return builtIns.map { it.signature }
    }

    override fun getTypes(): Map<String, Datatype> {
        return mapOf(
            "void" to Datatype.Void,
            "byte" to Datatype.Integer,
            "int" to Datatype.Integer,
        )
    }

    override fun buildSignature(signature: FunctionDefinition): BuiltFunction {
        for (builtIn in builtIns) {
            if (builtIn.signature == signature) {

                val instructions = builtIn.compile()
                instructions.first().addReference(Reference(signature, functionEntryLabel))

                val variables = mutableListOf<Variable>()
                if (builtIn.signature.returnType != Datatype.Void) {
                    variables.add(Variable("result", builtIn.signature.returnType, VariableType.Result))
                }
                for ((index, parameterType) in builtIn.signature.parameterTypes.withIndex()) {
                    variables.add(Variable("param$index", parameterType, VariableType.Parameter))
                }

                val layout = calculateLayout(variables, dummyDatatypeSizeProvider)

                return BuiltFunction(builtIn.signature, layout, instructions)
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
            if (builtIn.signature.matches(name, functionType, parameterTypes)) {
                return builtIn.signature
            }
        }

        throw AssertionError()
    }
}

fun buildSingleMainFunction(nodes: List<AstNode>): CompiledProgram {
    val node = function("main", emptyList(), nodes, "")
    val c = Compiler(DummyBuiltInProvider(), listOf(node))
    return c.buildProgram()
}

fun buildBody(body: String): List<EmulatorInstruction> {
    val tokens = parseFile(StringReader(body))
    val nodes = parseExpressions(tokens)


    val node = function("main", emptyList(), nodes, "void")
    val signature = FunctionDefinition.fromFunctionNode(node, dummyTypeContainer)

    val builtFunction =
        buildFunctionBody(node, signature, DummyBuiltInProvider(), dummyTypeContainer, dummyDatatypeSizeProvider)


    return builtFunction.instructions
}

fun buildProgram(body: String): CompiledProgram {
    val nodes = parserFromFile(body).parse()

    val c = Compiler(DummyBuiltInProvider(), nodes)
    val program = c.buildProgram()

    return program
}

fun shouldMatch(code: List<EmulatorInstruction>, expected: List<EmulatorInstruction>) {

    //Fixme compare references also


    assertEquals(
        expected.map { it.instruction }, code.map { it.instruction },
        (listOf("Disassembled: ") + code.map { it.toString() }).joinToString(
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
    shouldMatch(code.instructions, expected)
}

class CompilerCompareWithAssembly {
    @Test
    fun testFpOffset() {


        /**
        #Expected
        ldfp #100
        ldsp #100
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
                emulate(DefaultEmulator.ldfp, "val" to 100),
                emulate(DefaultEmulator.ldsp, "val" to 100),
                // Call
                emulate(DefaultEmulator.call_addr, "addr" to Reference(mainSignature, functionEntryLabel)),
                // On return
                emulate(DefaultEmulator.exit),
                //Start of function
                emulate(DefaultEmulator.ret),
            ), code.instructions
        )
    }

    @Test
    fun testBasicAssign() {

        val expected = """
        addsp #1
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
        ADDSP #2
        PUSH #5
        POP[FP #0]
        PUSH [FP #0]
        POP [FP #1]
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
       
        jmpz #end
        PUSH #1
        CALL #0
        subSP #1
        :end
        RET
        """
        val body = """
          if bool(5):
            print(1)
        """
        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testIfElse() {
        val expected = """
        PUSH #5
        TST POP
        JMPZ #else
        PUSH #1
        CALL #0
        subSP #1
        JMP #end
        :else
        PUSH #2
        CALL #0
        subSP #1
        :end
        RET
        """
        val body = """
          if bool(5):
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
        SUBSP #1
        JMP #0
        RET 
        """
        val body = """
          while bool(5):
            print(1)
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testCall() {
        val expected = """
        LDFP #100
        LDSP #100
        CALL #main
        exit
        
        :main
        CALL #test1
        PUSH #3
        CALL #print
        SUBSP #1
        RET
        
        :print
        LDA [FP #-1]
        OUT
        RET
        
        
        :test1 
        PUSH #10
        CALL #print
        SUBSP #1
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