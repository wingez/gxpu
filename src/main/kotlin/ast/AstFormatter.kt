package ast

private const val indent = "  "

class AstFormatterError(msg: String) : Error(msg)

fun formatAst(node: AstNode): String {

    val header = when (node.type) {
        NodeTypes.Constant -> node.asConstant().toString()
        NodeTypes.Call -> "call ${node.asCall().targetName}"

        else -> {
            throw AstFormatterError("Dont know how to format node of type ${node.type} (yet)")
        }
    }

    val body = node.childNodes.joinToString("\n") { formatAst(it) }.prependIndent(indent)
    if (body.isBlank()) {
        return header
    }
    return header + "\n" + body
}