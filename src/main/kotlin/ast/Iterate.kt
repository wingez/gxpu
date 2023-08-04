package ast

import requireNotReached


fun iterateNodeRecursively(node: AstNode): Iterable<AstNode> {
    val result = mutableListOf<AstNode>()
    iterateNodeRecursivelyImpl(node, result)
    return result
}

private fun iterateNodeRecursivelyImpl(node: AstNode, list: MutableList<AstNode>) {
    list.add(node)
    for (child in node.childNodes) {
        iterateNodeRecursivelyImpl(child, list)
    }
}


interface AstTreeModify {
    fun needReplace(): Boolean
    fun getReplacingNodes(): List<AstNode>
}

fun keep(): AstTreeModify {
    return object : AstTreeModify {
        override fun needReplace(): Boolean = false
        override fun getReplacingNodes(): List<AstNode> = requireNotReached()
    }
}


private data class ReplaceWith(
    private val nodes: List<AstNode>
) : AstTreeModify {
    override fun needReplace(): Boolean = true
    override fun getReplacingNodes(): List<AstNode> = nodes
}

fun replaceWith(node: AstNode): AstTreeModify {

    return ReplaceWith(listOf(node))

}

fun delete(): AstTreeModify {
    return object : AstTreeModify {
        override fun needReplace(): Boolean = true
        override fun getReplacingNodes(): List<AstNode> = emptyList()
    }
}


fun iterateAndModify(node: AstNode, function: (AstNode) -> AstTreeModify): AstNode {

    val result = iterateAndModifyImpl(listOf(node), function)

    check(result.size == 1) { result.size.toString() }

    return result.first()
}

private fun iterateAndModifyImpl(nodes: List<AstNode>, function: (AstNode) -> AstTreeModify): List<AstNode> {

    val result = mutableListOf<AstNode>()

    for (node in nodes) {

        val children = iterateAndModifyImpl(node.childNodes, function)

        val nodeModified = node.copy(childNodes = children)

        val replaceResult = function.invoke(nodeModified)
        if (replaceResult.needReplace()) {
            result.addAll(replaceResult.getReplacingNodes())
        } else {
            result.add(nodeModified)
        }
    }

    return result
}