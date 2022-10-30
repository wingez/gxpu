package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.FunctionType
import se.wingez.ast.NodeTypes

interface FunctionProvider {
    fun getFunctionMatching(name: String, functionType: FunctionType, parameterTypes: List<Datatype>): IFunction
}

data class FunctionDefinition(
    val name: String,
    val parameterTypes: List<Datatype>,
    val returnType: Datatype,
    val functionType: FunctionType,
) {
    fun matches(name: String, functionType: FunctionType, parameterTypes: List<Datatype>): Boolean {
        return name == this.name && functionType == this.functionType && parameterTypes == this.parameterTypes
    }
}

interface IFunction {
    val definition: FunctionDefinition
    fun execute(values: List<Value>, state: WalkerState): Value
}

class NodeFunction(
    val node: AstNode,
    override val definition: FunctionDefinition
) : IFunction {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        return state.walkFunction(node, values)
    }
}

fun definitionFromFuncNode(node: AstNode, typeProvider: TypeProvider): NodeFunction {
    assert(node.type == NodeTypes.Function)
    val funcNode = node.asFunction()

    val name = funcNode.name
    val parameters = funcNode.arguments.map { argNode ->
        assert(argNode.type == NodeTypes.NewVariable)
        val member = argNode.asNewVariable()
        assert(!member.hasTypeFromAssignment)
        val typeDefinition = member.optionalTypeDefinition
            ?: throw AssertionError()

        val paramType = typeProvider.getType(typeDefinition)
        paramType.instantiate()
    }
    val returnType = typeProvider.getType(funcNode.returnType).instantiate()
    val definition = FunctionDefinition(name, parameters, returnType, funcNode.type)
    return NodeFunction(node, definition)
}