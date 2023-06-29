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
): Datatype {

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

    return Datatype.Composite(typeName, members)
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
): FunctionContent {
    return FunctionCompiler(body, definition, functionProvider, typeProvider, VariableType.Local, globals)
        .compileFunction()
}

data class GlobalsResult(
    val initialize: FunctionContent,
    val variables: List<Variable>,
)

val initializeGlobalsSignature = SignatureBuilder("initializeGlobals")
    .getSignature()

fun compileGlobalAndInitialization(
    nodes: List<AstNode>,
    functionProvider: FunctionSignatureResolver,
    typeProvider: TypeProvider,
): GlobalsResult {
    val definition = FunctionDefinition(initializeGlobalsSignature, emptyList())
    return compileFunctionBody(
        AstNode.fromBody(nodes),
        definition, emptyList(), functionProvider, typeProvider
    ).let {
        GlobalsResult(it, it.fields.compositeFields.map { field ->
            Variable(field, VariableType.Global)
        })
    }
}