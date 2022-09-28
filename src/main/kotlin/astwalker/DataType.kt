package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

class Datatype {

    private enum class DatatypeClass {
        void,
        integer,
        composite,
    }

    private val type: DatatypeClass

    private constructor(name: String, type: DatatypeClass) {
        this.name = name

        assert(type == DatatypeClass.void || type == DatatypeClass.integer)
        this.type = type
        compositeMembersNullable = null
    }

    constructor(name: String, members: Map<String, Datatype>) {
        this.name = name
        this.type = DatatypeClass.composite
        compositeMembersNullable = members

    }

    val name: String

    fun isComposite() = type == DatatypeClass.composite

    fun isPrimitive() = type == DatatypeClass.integer

    fun isVoid() = type == DatatypeClass.void

    private val compositeMembersNullable: Map<String, Datatype>?


    val compositeMembers: Map<String, Datatype>
        get() {
            assert(isComposite())
            return compositeMembersNullable!!
        }

    companion object {
        val Integer = Datatype("integer", DatatypeClass.integer)
        val Void = Datatype("void", DatatypeClass.void)
    }
}


class Variable {
    val datatype: Datatype

    private val primitiveValue: Int
    private val values: MutableMap<String, Variable>?

    constructor(datatype: Datatype) {
        assert(datatype.isVoid())
        this.datatype = datatype
        primitiveValue = 0
        values = null
    }

    constructor(datatype: Datatype, primitiveValue: Int) {
        assert(datatype.isPrimitive())
        this.datatype = datatype
        this.primitiveValue = primitiveValue
        values = null
    }

    constructor(datatype: Datatype, memberValues: Map<String, Variable>) {
        assert(datatype.isComposite())
        this.datatype = datatype
        primitiveValue = 0


        values = mutableMapOf()
        for ((memberName, requiredType) in datatype.compositeMembers.entries) {

            val providedVariable = memberValues.getValue(memberName)
            assert(providedVariable.datatype == requiredType)

            values[memberName] = providedVariable
        }
    }

    fun isPrimitive() = datatype.isPrimitive()

    fun getPrimitiveValue(): Int {
        assert(isPrimitive())
        return primitiveValue
    }

    fun isComposite() = datatype.isComposite()

    fun getField(name: String): Variable {
        assert(isComposite())

        if (name !in datatype.compositeMembers) {
            throw WalkerException("${datatype.name} does not contain member $name")
        }
        return values!!.getValue(name)
    }

    fun setField(name: String, value: Variable) {
        assert(isComposite())
        if (name !in datatype.compositeMembers) {
            throw WalkerException("${datatype.name} does not contain member $name")
        }
        values!![name] = value
    }


}

fun createTypeFromNode(node: AstNode, typeProvider: Map<String, Datatype>): Datatype {
    assert(node.type == NodeTypes.Struct)

    val members = mutableMapOf<String, Datatype>()
    val typeName = node.data as String

    for (memberDef in node.childNodes) {
        assert(memberDef.type == NodeTypes.MemberDeclaration)
        val member = memberDef.asMemberDeclaration()

        val memberName = member.name
        val memberTypeName = member.type

        if (memberName in members) {
            throw WalkerException("Member $memberName already exist")
        }
        if (memberTypeName !in typeProvider) {
            throw WalkerException("No type exist with name $memberTypeName")
        }
        val type = typeProvider.getValue(memberTypeName)


        members[memberName] = type
    }

    return Datatype(typeName, members)
}