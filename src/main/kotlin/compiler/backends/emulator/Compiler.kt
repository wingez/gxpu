package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.backends.emulator.instructions.Instruction
import compiler.frontend.buildStruct
import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

data class GenerateLater(
    val instruction: Instruction,
    val pos: Int,
    private val generator: CodeGenerator
) {
    fun generate(args: Map<String, Int> = emptyMap()) {
        generator.generateAt(instruction.build(args), pos)
    }
}

enum class LinkType {
    FunctionAddress,
    VarsSize,
}

data class Link(
    val generateLater: GenerateLater,
    val linkSignature: FunctionSignature,
    val LinkType: LinkType,
    val offset: Int,
)

interface LinkAddressProvider {
    fun getFunctionAddress(signature: FunctionSignature): Int
    fun getFunctionVarsSize(signature: FunctionSignature): Int
}

class CodeGenerator {
    private val codeList = mutableListOf<UByte>()
    private val unpopulatedLinks = mutableListOf<Link>()
    private val dependents = mutableSetOf<FunctionSignature>()

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

    fun link(callInstruction: Instruction, functionSignature: FunctionSignature, type: LinkType, offset: Int = 0) {
        val generateLater = makeSpaceFor(callInstruction)
        link(generateLater, functionSignature, type, offset)
    }

    fun link(generateAt: GenerateLater, functionSignature: FunctionSignature, type: LinkType, offset: Int = 0) {
        unpopulatedLinks.add(Link(generateAt, functionSignature, type, offset))
        dependents.add(functionSignature)
    }

    fun applyLinks(addressProvider: LinkAddressProvider) {
        for (link in unpopulatedLinks) {
            var linkValue: Int
            var argName: String
            when (link.LinkType) {
                LinkType.FunctionAddress -> {
                    linkValue = addressProvider.getFunctionAddress(link.linkSignature)
                    argName = "addr"
                }
                LinkType.VarsSize -> {
                    linkValue = addressProvider.getFunctionVarsSize(link.linkSignature)
                    argName = "val"
                }
            }
            linkValue += link.offset
            link.generateLater.generate(mapOf(argName to linkValue))
        }
        unpopulatedLinks.clear()
    }

    val currentSize: Int
        get() = codeList.size

    fun getResultingCode(): List<UByte> {
        if (unpopulatedLinks.size > 0) {
            throw AssertionError()
        }

        return codeList.toList()
    }

    fun getDependents(): List<FunctionSignature> {
        return dependents.toList()
    }
}

data class CompiledProgram(
    val code: List<UByte>,
    val functionMapping: Map<BuiltFunction, Int>
)

interface BuiltInProvider {
    fun getSignatures(): List<FunctionSignature>
    fun getTypes(): Map<String, DataType>

    fun buildSignature(signature: FunctionSignature): BuiltFunction
}

private interface FunctionSource {
    val signature: FunctionSignature
    fun build(): BuiltFunction
}

private class BuiltinSource(
    val builtInProvider: BuiltInProvider,
    override val signature: FunctionSignature,
) : FunctionSource {

    override fun build(): BuiltFunction {
        return builtInProvider.buildSignature(signature)
    }
}

private class CodeSource(
    val node: AstNode,
    override val signature: FunctionSignature,
    val typeProvider: TypeProvider,
    val functionProvider: FunctionProvider,
) : FunctionSource {

    override fun build(): BuiltFunction {
        return buildFunctionBody(node.childNodes, signature, functionProvider, typeProvider)
    }
}


class Compiler(
    val builtInProvider: BuiltInProvider,
    val nodes: List<AstNode>
) : TypeProvider, FunctionProvider {

    private val functionSources = mutableListOf<FunctionSource>()

    val includedTypes = mutableMapOf<String, DataType>()


    override fun getType(name: String): DataType {
        if (name.isEmpty()) {
            return DEFAULT_TYPE
        }
        if (name !in includedTypes) {
            throw CompileError("No type with name $name found")
        }
        return includedTypes.getValue(name)
    }

    override fun findSignature(name: String, parameterSignature: List<DataType>): FunctionSignature {
        for (source in functionSources) {
            if (source.signature.matchesHeader(name, parameterSignature)) {
                return source.signature
            }
        }
        TODO()
    }

    fun buildStructs() {
        for (node in nodes.filter { it.type == NodeTypes.Struct }) {

            val struct = buildStruct(node, this)

            if (struct.name in includedTypes) {
                throw CompileError("Function ${struct.name} already exists")
            }
            includedTypes[struct.name] = struct
        }
    }

    fun buildProgram(): CompiledProgram {

        includedTypes.putAll(builtInProvider.getTypes())

        buildStructs()

        for (signature in builtInProvider.getSignatures()) {
            functionSources.add(BuiltinSource(builtInProvider, signature))
        }
        for (functionNode in nodes.filter { it.type == NodeTypes.Function }) {
            val signature = FunctionSignature.fromNode(functionNode, this)
            functionSources.add(CodeSource(functionNode, signature, this, this))
        }

        val includedFunctions = mutableMapOf<FunctionSignature, BuiltFunction>()

        for (source in functionSources) {

            val builtFunction = source.build()

            includedFunctions[source.signature] = builtFunction
        }


        val mainSignature = SignatureBuilder("main")
            .setReturnType(voidType)
            .getSignature()


        val headerGenerator = CodeGenerator()

        headerGenerator.generate(DefaultEmulator.ldfp.build(mapOf("val" to STACK_START)))
        headerGenerator.generate(DefaultEmulator.ldsp.build(mapOf("val" to STACK_START)))

        val allocMainVars = headerGenerator.makeSpaceFor(DefaultEmulator.sub_sp)
        val callMain = headerGenerator.makeSpaceFor(DefaultEmulator.call_addr)
        headerGenerator.generate(DefaultEmulator.exit.build())


        val resultingCode = mutableListOf<UByte>()

        val alreadyPlaced = mutableMapOf<FunctionSignature, Int>()

        val toPlace = mutableListOf(mainSignature)

        for (i in headerGenerator.getResultingCode()) {
            resultingCode.add(0u)
        }

        while (toPlace.size > 0) {

            val top = toPlace.removeLast()

            if (alreadyPlaced.contains(top)) {
                continue
            }

            val included = includedFunctions.getValue(top)

            val notAddedDependents = included.generator.getDependents()
                .filter { it !in alreadyPlaced }
                .filter { it != top }


            if (notAddedDependents.isNotEmpty()) {
                // Dependents still needed to take care of
                toPlace.add(top)
                toPlace.addAll(notAddedDependents)
                continue
            }


            // All dependents added, lets add this
            alreadyPlaced[included.signature] = resultingCode.size

            val indexProvider = object : LinkAddressProvider {
                override fun getFunctionAddress(signature: FunctionSignature): Int {
                    return alreadyPlaced.getValue(signature)
                }

                override fun getFunctionVarsSize(signature: FunctionSignature): Int {
                    return includedFunctions.getValue(signature).sizeOfVars
                }
            }

            included.generator.applyLinks(indexProvider)
            resultingCode.addAll(included.generator.getResultingCode())

        }


        callMain.generate(mapOf("addr" to alreadyPlaced.getValue(mainSignature)))
        allocMainVars.generate(mapOf("val" to includedFunctions.getValue(mainSignature).sizeOfVars))

        headerGenerator.getResultingCode().forEachIndexed { index, value ->
            resultingCode[index] = value
        }

        val placedMap = mutableMapOf<BuiltFunction, Int>()
        for (entry in alreadyPlaced) {
            val layout = includedFunctions.getValue(entry.key)

            placedMap[layout] = entry.value

        }

        return CompiledProgram(resultingCode, placedMap)
    }


}