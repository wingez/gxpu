package se.wingez.ast

interface NodeContainer {
    fun getNodes(): Iterable<AstNode>
}