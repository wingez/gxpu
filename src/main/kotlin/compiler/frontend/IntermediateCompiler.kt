package compiler.frontend

import ast.AstNode
import ast.AstParser
import ast.NodeTypes
import compiler.*
import tokens.Token
import tokens.TokenType
import tokens.parseFile
import java.io.StringReader

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

fun compileFunctionBody(body: String, builtIns: BuiltInCollection): FunctionContent {
    val tokens =
        parseFile(StringReader(body), "dummyfile") + listOf(Token(TokenType.EndBlock, "", SourceInfo.notApplicable))
    val nodes = AstParser(tokens).parseStatementsUntilEndblock()

    val types = TypeCollection(emptyList(), builtIns)

    return compileFunctionBody(
        AstNode.fromBody(nodes),
        FunctionDefinition(mainSignature, emptyList()),
        emptyList(),
        builtIns,
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
): FunctionContent {
    return FunctionCompiler(body, definition, functionProvider, typeProvider, treatNewVariablesAs, globals)
        .compileFunction()
}

data class GlobalsResult(
    val initialize: FunctionContent,
    val fields: CompositeDatatype,
    val variables: List<Variable>,
) {
    val needsInitialization get() = initialize.code.instructions.any { it !is Return } // every functions  has an implicit return. Ignore that
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
        GlobalsResult(it, it.fields, it.fields.compositeFields.map { field ->
            Variable(field, VariableType.Global)
        })
    }
}