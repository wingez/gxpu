package se.wingez.compiler.backends.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.astwalker.TypeProvider
import se.wingez.compiler.frontend.FunctionDefinition
import se.wingez.compiler.frontend.IFunction

interface IWalkerFunction : IFunction {
    fun execute(values: List<Value>, state: WalkerState): Value
}

class NodeFunction(
    val node: AstNode,
    override val definition: FunctionDefinition
) : IWalkerFunction {
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