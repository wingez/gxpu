package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

class WalkerException() : Exception()

class WalkerOutput() {

    val result = mutableListOf<String>()
}

fun walk(node: AstNode, output: WalkerOutput) {

    assert(node.type == NodeTypes.Function)

    for (child in node.childNodes) {

        when (child.type) {
            NodeTypes.Call -> {
                val funcNode = child.asCall()
                assert(funcNode.targetName == "print")
                output.result.add(getValueOf(funcNode.parameters.first()).toString())

            }
        }
    }

}

fun getValueOf(node: AstNode): Int {

    when (node.type) {
        NodeTypes.Constant -> return node.asConstant()
    }

    throw WalkerException()
}




