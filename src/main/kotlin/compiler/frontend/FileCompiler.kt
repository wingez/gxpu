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

data class CompiledIntermediateFile(
    val allTypes: List<Datatype>,
    val functions: List<FunctionContent>,
    val globals: GlobalsResult
)

fun compileFile(
    filename: String,
    reader: Reader,
    programCompiler: ProgramCompiler,
): CompiledIntermediateFile {
    val tokens = parseFile(reader, filename)
    val nodes = AstParser(tokens).parse()


    val structNodes = nodes.filter { it.type == NodeTypes.Struct }
    val functionNodes = nodes.filter { it.type == NodeTypes.Function }
    val importNodes = nodes.filter { it.type == NodeTypes.Import }

    val globalsAndInitializationNodes =
        nodes.filter { it.type !in listOf(NodeTypes.Struct, NodeTypes.Function, NodeTypes.Import) }

    val types = mutableListOf<Datatype>()
    val allAvailableFunctionDefinitions = mutableListOf<FunctionDefinition>()

    //Import builtins
    val (builtInTypes, builtInFunctions) = programCompiler.importBuiltins()
    types.addAll(builtInTypes)
    allAvailableFunctionDefinitions.addAll(builtInFunctions)

    //Import
    for (node in importNodes) {
        val toImport = node.asIdentifier()
        val (importedTypes, importedFunctions) = programCompiler.import(toImport)
        types.addAll(importedTypes)
        allAvailableFunctionDefinitions.addAll(importedFunctions)
    }

    val foundStructs = buildAllStructs(structNodes, types)
    types.addAll(foundStructs)


    val typeProvider = object : TypeProvider {
        override fun getType(name: String): Datatype? {
            return types.find { it.name == name }
        }
    }


    val functionBodiesWithDefinitions = mutableListOf<Pair<AstNode, FunctionDefinition>>()
    for (node in functionNodes) {
        val definition = definitionFromFunctionNode(node, filename, typeProvider)
        allAvailableFunctionDefinitions.add(definition)
        functionBodiesWithDefinitions.add(node to definition)
    }


    val functionSignatureResolver = FunctionCollection(allAvailableFunctionDefinitions)

    val globals = compileGlobalAndInitialization(
        globalsAndInitializationNodes, filename,
        functionSignatureResolver, typeProvider,
    )

    val functions = functionBodiesWithDefinitions.flatMap { (node, definition) ->
        compileFunctionBody(
            node.asFunction().body,
            definition,
            globals.variables,
            functionSignatureResolver,
            typeProvider,
            "",
            VariableType.Local,
        )
    } + globals.initialization

    return CompiledIntermediateFile(
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


fun compileFunctionBody(
    body: AstNode,
    definition: FunctionDefinition,
    globals: Map<String, Variable>,
    functionProvider: FunctionSignatureResolver,
    typeProvider: TypeProvider,
    variablePrefix: String,
    treatNewVariablesAs: VariableType,
): List<FunctionContent> {
    return FunctionCompiler(
        body,
        definition,
        functionProvider,
        typeProvider,
        treatNewVariablesAs,
        variablePrefix,
        globals
    )
        .compileFunction()
}

data class GlobalsResult(
    val initialization: FunctionContent,
    val fields: CompositeDatatype,
    val variables: Map<String, Variable>,
) {
    val needsInitialization
        get() = initialization.code.hasContent
}

fun compileGlobalAndInitialization(
    nodes: List<AstNode>,
    filename: String,
    functionProvider: FunctionSignatureResolver,
    typeProvider: TypeProvider,
): GlobalsResult {


    val initializeGlobalsDefinition = DefinitionBuilder("initializeGlobals")
        .setSourceFile(filename)
        .getDefinition()

    return compileFunctionBody(
        AstNode.fromBody(nodes),
        initializeGlobalsDefinition, emptyMap(), functionProvider, typeProvider, "$filename-", VariableType.Global,
    ).let {
        require(it.size == 1) { "lambdas in globals initialization not supported yet" }
        val globalsInit = it.first()
        GlobalsResult(globalsInit, globalsInit.fields,  globalsInit.definedVariables)
    }
}