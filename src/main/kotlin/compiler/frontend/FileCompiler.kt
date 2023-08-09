package compiler.frontend

import ast.AstNode
import ast.AstParser
import ast.NodeTypes
import compiler.*
import tokens.Token
import tokens.TokenType
import tokens.parseFile
import java.io.Reader
import java.io.StringReader

data class CompiledFile(
    val allTypes: List<Datatype>,
    val functions: List<FunctionContent>,
    val globals: GlobalsResult
)

fun compileFile(
    filename: String,
    reader: Reader,
    programCompiler: ProgramCompiler,
): CompiledFile {
    val tokens = parseFile(reader, filename)
    val nodes = AstParser(tokens).parse()


    val structNodes = nodes.filter { it.type == NodeTypes.Struct }
    val functionNodes = nodes.filter { it.type == NodeTypes.Function }
    val importNodes = nodes.filter { it.type == NodeTypes.Import }

    val globalsAndInitializationNodes =
        nodes.filter { it.type !in listOf(NodeTypes.Struct, NodeTypes.Function, NodeTypes.Import) }

    val types = mutableListOf<Datatype>()
    val allAvailableFunctionDefinitions = mutableListOf<FunctionDefinition>()

    val (builtInTypes, builtInFunctions) = programCompiler.importBuiltins()
    types.addAll(builtInTypes)
    allAvailableFunctionDefinitions.addAll(builtInFunctions)

    //TODO: apply import nodes

    val foundStructs = buildAllStructs(structNodes, types)
    types.addAll(foundStructs)


    val typeProvider = object : TypeProvider {
        override fun getType(name: String): Datatype? {
            return types.find { it.name == name }
        }
    }


    val functionBodiesWithDefinitions = mutableListOf<Pair<AstNode, FunctionDefinition>>()
    for (node in functionNodes) {
        val definition = definitionFromFunctionNode(node, typeProvider)
        allAvailableFunctionDefinitions.add(definition)
        functionBodiesWithDefinitions.add(node to definition)
    }


    val functionSignatureResolver = FunctionCollection(allAvailableFunctionDefinitions)

    val globals = compileGlobalAndInitialization(
        globalsAndInitializationNodes,
        functionSignatureResolver, typeProvider,
    )

    val functions = functionBodiesWithDefinitions.flatMap { (node, definition) ->
        compileFunctionBody(node.asFunction().body, definition, globals.variables, functionSignatureResolver, typeProvider)
    } + globals.initialization

    return CompiledFile(
        foundStructs, functions, globals
    )
}

fun buildAllStructs(
    nodes: List<AstNode>,
    existingTypes: List<Datatype>,
): List<Datatype> {

    val result = mutableListOf<Datatype>()

    val all = mutableListOf<Datatype>()
    all.addAll(existingTypes)


    val typeProvider = object : TypeProvider {
        override fun getType(name: String): Datatype? {
            return all.find { it.name == name }
        }
    }

    for (node in nodes) {
        val new = buildStruct(node, typeProvider)
        result.add(new)
        all.add(new)
    }

    return result
}

fun buildStruct(
    structNode: AstNode,
    typeProvider: TypeProvider,
): CompositeDatatype {

    require(structNode.type == NodeTypes.Struct)

    val typeName = structNode.data as String

    val members = structNode.childNodes.map { child ->
        val newValue = child.asNewVariable()

        val optionalTypeDef = newValue.optionalTypeDefinition
        checkNotNull(optionalTypeDef)
        val fieldType = typeProvider.getType(optionalTypeDef)
            ?: throw FrontendCompilerError("No type of type: $optionalTypeDef")

        val fieldName = newValue.name

        CompositeDataTypeField(fieldName, fieldType)
    }

    return CompositeDatatype(typeName, members)
}

fun compileFunctionBody(body: String, builtIns: BuiltInCollection): List<FunctionContent> {
    val tokens =
        parseFile(StringReader(body), "dummyfile") + listOf(Token(TokenType.EndBlock, "", SourceInfo.notApplicable))
    val nodes = AstParser(tokens).parseStatementsUntilEndblock()

    val types = TypeCollection(emptyList(), builtIns)

    return compileFunctionBody(
        AstNode.fromBody(nodes),
        mainDefinition,
        emptyList(),
        FunctionCollection(builtIns.functions),
        types
    )
}


fun compileFunctionBody(
    body: AstNode,
    definition: FunctionDefinition,
    globals: List<Variable>,
    functionProvider: FunctionSignatureResolver,
    typeProvider: TypeProvider,
    treatNewVariablesAs: VariableType = VariableType.Local,
): List<FunctionContent> {
    return FunctionCompiler(body, definition, functionProvider, typeProvider, treatNewVariablesAs, globals)
        .compileFunction()
}

data class GlobalsResult(
    val initialization: FunctionContent,
    val fields: CompositeDatatype,
    val variables: List<Variable>,
) {
    val needsInitialization
        get() = initialization.code.hasContent
}

val initializeGlobalsDefinition = DefinitionBuilder("initializeGlobals")
    .getDefinition()

fun compileGlobalAndInitialization(
    nodes: List<AstNode>,
    functionProvider: FunctionSignatureResolver,
    typeProvider: TypeProvider,
): GlobalsResult {
    return compileFunctionBody(
        AstNode.fromBody(nodes),
        initializeGlobalsDefinition, emptyList(), functionProvider, typeProvider, VariableType.Global,
    ).let {
        require(it.size == 1) { "lambdas in globals initialization not supported yet" }
        val globalsInit = it.first()
        GlobalsResult(globalsInit, globalsInit.fields, globalsInit.fields.compositeFields.map { field ->
            Variable(field, VariableType.Global)
        })
    }
}