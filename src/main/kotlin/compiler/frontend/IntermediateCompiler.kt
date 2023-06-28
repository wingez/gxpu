package compiler.frontend

import ast.AstNode
import ast.NodeTypes

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

        CompositeDataTypeField(fieldName, fieldType, FieldAnnotation.None)
    }

    return Datatype.Composite(typeName, members)
}

fun compileFunctionBody(
    body:AstNode,
    definition: FunctionDefinition,
    functionProvider: FunctionDefinitionResolver,
    typeProvider: TypeProvider,
):FunctionContent{

}
fun compileFunction(
    functionNode: AstNode,
    functionProvider: FunctionDefinitionResolver,
    typeProvider: TypeProvider
): FunctionContent {

    return FunctionCompiler(functionProvider, typeProvider).compileFunction(functionNode)

}
