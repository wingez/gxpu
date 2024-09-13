package compiler.frontend

import ast.*
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
    symbolTable: SymbolTable,
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
    //TODO
    //val (builtInTypes, builtInFunctions) = programCompiler.importBuiltins()
    //types.addAll(builtInTypes)
    //allAvailableFunctionDefinitions.addAll(builtInFunctions)

    //Import
//    for (node in importNodes) {
//        val toImport = node.asIdentifier()
//        val (importedTypes, importedFunctions) = programCompiler.import(toImport)
//        types.addAll(importedTypes)
//        allAvailableFunctionDefinitions.addAll(importedFunctions)
//    }

    val foundStructs = buildAllStructs(structNodes, symbolTable)
    types.addAll(foundStructs)


    val functionBodiesWithDefinitions = mutableListOf<Pair<AstNode, FunctionDefinition>>()
    for (node in functionNodes) {
        val definition = definitionFromFunctionNode(node, filename, symbolTable)
        allAvailableFunctionDefinitions.add(definition)
        functionBodiesWithDefinitions.add(node to definition)
    }


    val globals = compileGlobalAndInitialization(
        globalsAndInitializationNodes, filename,
        symbolTable,
    )

    val functions = functionBodiesWithDefinitions.flatMap { (node, definition) ->
        compileFunctionBody(
            node.asFunction().body,
            definition,
            globals.variables,
            symbolTable,
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
    symbolTable: SymbolTable,
): List<Datatype> {

    val result = mutableListOf<Datatype>()

    for (node in nodes) {
        val new = buildStruct(node, symbolTable)
        result.add(new)
    }

    return result
}

fun buildStruct(
    structNode: AstNode,
    symbolTable: SymbolTable,
): CompositeDatatype {

    require(structNode.type == NodeTypes.Struct)

    val typeName = structNode.data as String

    val members = structNode.childNodes.map { child ->
        val newValue = child.asNewVariable()

        val optionalTypeDef = newValue.optionalTypeDefinition
        checkNotNull(optionalTypeDef)
        val fieldType = requireTypeFromTypeDefinition(optionalTypeDef, symbolTable)

        val fieldName = newValue.name

        CompositeDataTypeField(fieldName, fieldType)
    }

    return CompositeDatatype(typeName, members)
}

fun requireTypeFromTypeDefinition(typeDefinition: TypeDefinition, symbolTable: SymbolTable): Datatype {
    return when (typeDefinition.base) {
        is StaticBase -> {
            val typeName = typeDefinition.base.name
            var type = symbolTable.getType(typeName) ?: throw FrontendCompilerError("No type of type: $typeDefinition")
            if (typeDefinition.hasModifier(TypeDefinitionModifier.Array)) {
                type = type.arrayOf()
            }
            if (typeDefinition.hasModifier(TypeDefinitionModifier.Pointer)) {
                type = type.pointerOf()
            }
            type
        }

        else -> TODO(typeDefinition.base.toString())
    }
}


fun compileFunctionBody(
    body: AstNode,
    definition: FunctionDefinition,
    globals: Map<String, Variable>,
    symbolTable: SymbolTable,
    variablePrefix: String,
    treatNewVariablesAs: VariableType,
): List<FunctionContent> {
    return FunctionCompiler(
        body,
        definition,
        symbolTable,
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
    symbolTable: SymbolTable,
): GlobalsResult {


    val initializeGlobalsDefinition = DefinitionBuilder("initializeGlobals")
        .setSourceFile(filename)
        .getDefinition()

    return compileFunctionBody(
        AstNode.fromBody(nodes),
        initializeGlobalsDefinition, emptyMap(), symbolTable, "$filename-", VariableType.Global,
    ).let {
        require(it.size == 1) { "lambdas in globals initialization not supported yet" }
        val globalsInit = it.first()
        GlobalsResult(globalsInit, globalsInit.fields, globalsInit.definedVariables)
    }
}