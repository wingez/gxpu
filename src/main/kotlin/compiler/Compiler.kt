package se.wingez.compiler

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.emulator.DefaultEmulator
import se.wingez.instructions.Instruction

data class GenerateLater(
    val instruction: Instruction,
    val pos: Int,
    private val generator: CodeGenerator
) {
    fun generate(args: Map<String, Int> = emptyMap()) {
        generator.generateAt(instruction.build(args), pos)
    }
}

data class Link(
    val generateLater: GenerateLater,
    val linkSignature: FunctionSignature,
)

fun interface LinkAddressProvider {
    fun getAddress(signature: FunctionSignature): Int
}

class CodeGenerator {
    private val codeList = mutableListOf<UByte>()
    private val links = mutableListOf<Link>()

    fun generate(code: List<UByte>) {
        codeList.addAll(code)
    }

    fun generateAt(code: List<UByte>, at: Int) {
        code.forEachIndexed { index, byte ->
            codeList[at + index] = byte
        }
    }

    fun makeSpaceFor(instruction: Instruction): GenerateLater {
        val pos = currentSize
        for (i in 0 until instruction.size) {
            generate(listOf(0u))
        }
        return GenerateLater(instruction, pos, this)
    }

    fun link(callInstruction: Instruction, functionSignature: FunctionSignature) {
        val generateLater = makeSpaceFor(callInstruction)
        links.add(Link(generateLater, functionSignature))
    }

    fun applyLinks(addressProvider: LinkAddressProvider) {
        for (link in links) {
            val index = addressProvider.getAddress(link.linkSignature)
            link.generateLater.generate(mapOf("addr" to index))
        }
        links.clear()
    }

    val currentSize: Int
        get() = codeList.size

    val resultingCode: List<UByte>
        get() = codeList.toList()

}

data class IncludedFunction(
    val name: String,
    val signature: FunctionSignature,
    val generator: CodeGenerator,
)


data class CompiledProgram(
    val code: List<UByte>,
    val functionMapping: Map<FunctionSignature, Int>
)

class Compiler(
    val nodes: List<AstNode>
) : TypeProvider, FunctionProvider {

    val includedFunctions = mutableListOf<IncludedFunction>()


    val includedTypes = mutableMapOf<String, DataType>(
        "void" to voidType,
        "byte" to byteType,
    )


    override fun getType(name: String): DataType {
        if (name.isEmpty()) {
            return DEFAULT_TYPE
        }
        if (name !in includedTypes) {
            throw CompileError("No type with name $name found")
        }
        return includedTypes.getValue(name)
    }

    override fun includeFunction(name: String, parameters: List<DataType>): FunctionSignature {

        // First search already included functions
        val alreadyIncluded =
            includedFunctions.find { it.signature.name == name && it.signature.parameterSignature == parameters }
        if (alreadyIncluded != null)
            return alreadyIncluded.signature

        // Then search builtins
        val builtIn = findBuiltin(name, parameters)
        if (builtIn != null) {

            val builtInGenerator = CodeGenerator()

            builtIn.compile(builtInGenerator)

            val included = IncludedFunction(builtIn.name, builtIn.signature, builtInGenerator)
            includedFunctions.add(included)
            return included.signature
        }

        // search other nodes
        for (node in nodes.filter { it.type == NodeTypes.Function }) {
            val signature = calculateSignature(node, this)
            if (signature.name == name && signature.parameterSignature == parameters) {

                val generator = buildFunction(node, signature)


                val included = IncludedFunction(name, signature, generator)
                includedFunctions.add(included)

                return signature
            }
        }

        throw CompileError("No matching function found with name :$name")
    }

    fun buildFunction(node: AstNode, signature: FunctionSignature): CodeGenerator {
        assertValidFunctionNode(node)

//        val signature = calculateSignature(node, this)
        val generator = buildFunctionBody(node.childNodes, signature, this)

        return generator
    }

    fun buildStruct(node: AstNode) {
        val struct = buildStruct(node, this)
        if (struct.name in includedTypes) {
            throw CompileError("Function ${struct.name} already exists")
        }
        includedTypes[struct.name] = struct
    }

    fun buildProgram(): CompiledProgram {

        for (node in nodes.filter { it.type == NodeTypes.Struct }) {
            buildStruct(node)
        }

        val headerGenerator = CodeGenerator()
        val mainFunction = includeFunction(MAIN_NAME, emptyList())

        headerGenerator.generate(DefaultEmulator.ldfp.build(mapOf("val" to STACK_START)))
        headerGenerator.generate(DefaultEmulator.ldsp.build(mapOf("val" to STACK_START)))

        headerGenerator.generate(DefaultEmulator.sub_sp.build(mapOf("val" to mainFunction.sizeOfVars)))
        val callMain = headerGenerator.makeSpaceFor(DefaultEmulator.call_addr)
        headerGenerator.generate(DefaultEmulator.exit.build())


        val resultingCode = mutableListOf<UByte>()
        val placedFunctions = mutableMapOf<FunctionSignature, Int>()

        for (i in headerGenerator.resultingCode) {
            resultingCode.add(0u)
        }

        for (toInclude in includedFunctions) {
            val index = resultingCode.size
            placedFunctions[toInclude.signature] = index

            val indexProvider = LinkAddressProvider { signature ->
                placedFunctions.getValue(signature)
            }

            toInclude.generator.applyLinks(indexProvider)
            resultingCode.addAll(toInclude.generator.resultingCode)
        }

        val mainIndex = placedFunctions.getValue(mainFunction)

        callMain.generate(mapOf("addr" to mainIndex))

        headerGenerator.resultingCode.forEachIndexed { index, value ->
            resultingCode[index] = value
        }


        return CompiledProgram(resultingCode, placedFunctions)
    }


}