package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.backends.emulator.instructions.Instruction
import compiler.frontend.Datatype
import compiler.frontend.TypeProvider
import compiler.frontend.buildStruct
import se.wingez.ast.AstNode
import se.wingez.ast.FunctionType
import se.wingez.ast.NodeTypes
import se.wingez.compiler.frontend.FunctionDefinition
import se.wingez.compiler.frontend.FunctionDefinitionResolver
import se.wingez.compiler.frontend.VariableType

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
    val linkSignature: FunctionDefinition,
    val LinkType: LinkType,
    val offset: Int,
)

interface LinkAddressProvider {
    fun getFunctionAddress(signature: FunctionDefinition): Int
    fun getFunctionVarsSize(signature: FunctionDefinition): Int
}

class CodeGenerator {
    private val codeList = mutableListOf<UByte>()
    private val unpopulatedLinks = mutableListOf<Link>()
    private val dependents = mutableSetOf<FunctionDefinition>()

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

    fun link(callInstruction: Instruction, functionSignature: FunctionDefinition, type: LinkType, offset: Int = 0) {
        val generateLater = makeSpaceFor(callInstruction)
        link(generateLater, functionSignature, type, offset)
    }

    fun link(generateAt: GenerateLater, functionSignature: FunctionDefinition, type: LinkType, offset: Int = 0) {
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

    fun getDependents(): List<FunctionDefinition> {
        return dependents.toList()
    }
}

data class CompiledProgram(
    val code: List<UByte>,
    val functionMapping: Map<BuiltFunction, Int>
)

interface BuiltInProvider {
    fun getSignatures(): List<FunctionDefinition>
    fun getTypes(): Map<String, Datatype>

    fun buildSignature(signature: FunctionDefinition): BuiltFunction
}

private interface FunctionSource {
    val signature: FunctionDefinition
    fun build(): BuiltFunction
}

private class BuiltinSource(
    val builtInProvider: BuiltInProvider,
    override val signature: FunctionDefinition,
) : FunctionSource {

    override fun build(): BuiltFunction {
        return builtInProvider.buildSignature(signature)
    }
}

private class CodeSource(
    val node: AstNode,
    override val signature: FunctionDefinition,
    val typeProvider: TypeProvider,
    val functionProvider: FunctionDefinitionResolver,
    val datatypeLayoutProvider: DatatypeLayoutProvider,
) : FunctionSource {

    override fun build(): BuiltFunction {
        return buildFunctionBody(node, signature, functionProvider, typeProvider, datatypeLayoutProvider)
    }
}


class Compiler(
    val builtInProvider: BuiltInProvider,
    val nodes: List<AstNode>
) : TypeProvider, FunctionDefinitionResolver {

    private val functionSources = mutableListOf<FunctionSource>()

    val includedTypes = mutableMapOf<String, Datatype>()


    override fun getType(name: String): Datatype {
        if (name.isEmpty()) {
            return Datatype.Integer
        }
        if (name !in includedTypes) {
            throw CompileError("No type with name $name found")
        }
        return includedTypes.getValue(name)
    }

    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition {
        for (source in functionSources) {
            if (source.signature.matches(name, functionType, parameterTypes)) {
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
            //TODO:
            //includedTypes[struct.name] = struct
        }
    }

    fun buildProgram(): CompiledProgram {

        includedTypes.putAll(builtInProvider.getTypes())

        buildStructs()

        for (signature in builtInProvider.getSignatures()) {
            functionSources.add(BuiltinSource(builtInProvider, signature))
        }
        for (functionNode in nodes.filter { it.type == NodeTypes.Function }) {
            val signature = FunctionDefinition.fromFunctionNode(functionNode, this)
            functionSources.add(CodeSource(functionNode, signature, this, this, dummyDatatypeSizeProvider))
        }

        val includedFunctions = mutableMapOf<FunctionDefinition, BuiltFunction>()

        for (source in functionSources) {

            val builtFunction = source.build()

            includedFunctions[source.signature] = builtFunction
        }


        val mainSignature = SignatureBuilder("main")
            .setReturnType(Datatype.Void)
            .getSignature()


        val headerGenerator = CodeGenerator()

        headerGenerator.generate(DefaultEmulator.ldfp.build(mapOf("val" to STACK_START)))
        headerGenerator.generate(DefaultEmulator.ldsp.build(mapOf("val" to STACK_START)))

        val allocMainVars = headerGenerator.makeSpaceFor(DefaultEmulator.sub_sp)
        val callMain = headerGenerator.makeSpaceFor(DefaultEmulator.call_addr)
        headerGenerator.generate(DefaultEmulator.exit.build())


        val resultingCode = mutableListOf<UByte>()

        val alreadyPlaced = mutableMapOf<FunctionDefinition, Int>()

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
                override fun getFunctionAddress(signature: FunctionDefinition): Int {
                    return alreadyPlaced.getValue(signature)
                }

                override fun getFunctionVarsSize(signature: FunctionDefinition): Int {
                    return includedFunctions.getValue(signature).layout.sizeOfType(VariableType.Local)
                }
            }

            included.generator.applyLinks(indexProvider)
            resultingCode.addAll(included.generator.getResultingCode())

        }


        callMain.generate(mapOf("addr" to alreadyPlaced.getValue(mainSignature)))
        allocMainVars.generate(mapOf("val" to includedFunctions.getValue(mainSignature).layout.sizeOfType(VariableType.Local)))

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

val dummyDatatypeSizeProvider = object : DatatypeLayoutProvider {
    override fun sizeOf(dataType: Datatype): Int {
        return when (dataType) {
            Datatype.Integer -> 1
            else -> throw NotImplementedError()
        }


    }
}