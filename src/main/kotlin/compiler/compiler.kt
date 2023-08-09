package compiler

import ast.AstNode
import ast.FunctionType
import ast.NodeTypes
import compiler.frontend.*

interface BackendCompiler {
    fun buildAndRun(intermediateProgram: CompiledIntermediateProgram): List<Int>
}

interface BuiltInCollection : TypeProvider, FunctionSignatureResolver {
    val types: List<Datatype>
    val functions: List<FunctionDefinition>

    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition {
        return FunctionCollection(functions).getFunctionDefinitionMatching(name, functionType, parameterTypes)
    }

    override fun getType(name: String): Datatype? {
        return types.find { it.name == name }
    }
}

class FunctionCollection(
    private val signatures: List<FunctionDefinition>,
) : FunctionSignatureResolver {
    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition {
        return signatures.find { it.matches(name, functionType, parameterTypes) }
            ?: throw FrontendCompilerError("write something here $name $functionType $parameterTypes")
    }
}

class TypeCollection(
    nodes: List<AstNode>,
    builtIns: BuiltInCollection
) : TypeProvider {

    private val result = mutableListOf<Datatype>()

    val allTypes get() = result

    override fun getType(name: String): Datatype? {
        return result.find { it.name == name }
    }

    init {
        fun addType(type: Datatype) {
            if (getType(type.name) != null) {
                throw FrontendCompilerError("Datatype with name: ${type.name} already added")
            }
            result.add(type)
        }

        for (type in builtIns.types) {
            addType(type)
        }

        for (node in nodes) {
            require(node.type == NodeTypes.Struct)
            val struct = buildStruct(node, this)
            addType(struct)
        }
    }
}

fun compileAndRunProgram(
    fileName: String,
    backendCompiler: BackendCompiler,
    builtIns: BuiltInCollection
): List<Int> {
    val compiledProgram = compileProgram(fileName, builtIns)
    return backendCompiler.buildAndRun(compiledProgram)
}

fun compileAndRunBody(
    body: String,
    backendCompiler: BackendCompiler,
    builtIns: BuiltInCollection,
): List<Int> {
    val f = compileProgramFromSingleBody(body, builtIns)
    return backendCompiler.buildAndRun(f)
}

