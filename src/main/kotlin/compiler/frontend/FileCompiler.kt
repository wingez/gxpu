package compiler.frontend

import ast.*
import compiler.*
import tokens.Token
import tokens.TokenType
import tokens.parseFile
import java.io.Reader
import java.io.StringReader

data class CompiledIntermediateFile(
    val functions: List<FunctionContent>,
    val globalInit: FunctionContent,
)

fun compileFile(
    filename: String,
    nodes: List<AstNode>,
    symbolTable: MutableSymbolTable,
): CompiledIntermediateFile {


    val structNodes = nodes.filter { it.type == NodeTypes.Struct }
    val functionNodes = nodes.filter { it.type == NodeTypes.Function }

    val globalsAndInitializationNodes =
        nodes.filter { it.type !in listOf(NodeTypes.Struct, NodeTypes.Function, NodeTypes.Import) }


    buildAllStructs(structNodes, symbolTable, filename)


    val functionBodiesWithDefinitions = mutableListOf<Pair<AstNode, FunctionDefinition>>()
    for (node in functionNodes) {
        val definition = definitionFromFunctionNode(node, filename, symbolTable)

        symbolTable.addFunction(definition)

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
            symbolTable,
            "",
            VariableType.Local,
        )
    } + globals

    return CompiledIntermediateFile(
        functions, globals
    )
}

fun buildAllStructs(
    nodes: List<AstNode>,
    symbolTable: MutableSymbolTable,
    sourceFile: String,
) {
    for (node in nodes) {
        val new = buildStruct(node, symbolTable)
        symbolTable.addType(new, sourceFile)
    }
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
    )
        .compileFunction()
}


fun compileGlobalAndInitialization(
    nodes: List<AstNode>,
    filename: String,
    symbolTable: SymbolTable,
): FunctionContent {


    val initializeGlobalsDefinition = DefinitionBuilder("initializeGlobals")
        .setSourceFile(filename)
        .getDefinition()

    return compileFunctionBody(
        AstNode.fromBody(nodes),
        initializeGlobalsDefinition, symbolTable, "$filename-", VariableType.Global,
    ).let {
        require(it.size == 1) { "lambdas in globals initialization not supported yet" }
        it.first()
    }
}