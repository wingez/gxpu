package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

interface FunctionProvider {
    fun getFunctionMatching(name: String, parameterTypes: List<Datatype>): IFunction
}

data class FunctionDefinition(
    val name: String,
    val parameterTypes: List<Datatype>,
    val returnType: Datatype,
) {
    fun matches(name: String, parameterTypes: List<Datatype>): Boolean {
        return name == this.name && parameterTypes == this.parameterTypes
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

        typeProvider.getType(typeDefinition)
    }
    val returnType = typeProvider.getType(funcNode.returnType)
    val definition = FunctionDefinition(name, parameters, returnType)
    return NodeFunction(node, definition)
}