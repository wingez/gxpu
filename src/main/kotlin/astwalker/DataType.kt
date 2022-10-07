package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

class Datatype private constructor(
    val name: String,
    private val type: DatatypeClass,
    private val compositeMembersNullable: Map<String, Datatype>?,
    private val arrayTypeNullable: Datatype?,
) {

    private enum class DatatypeClass {
        void,
        integer,
        composite,
        bool,
        array,
    }

    fun isComposite() = type == DatatypeClass.composite || type == DatatypeClass.array

    fun isPrimitive() = type == DatatypeClass.integer || type == DatatypeClass.bool

    fun isVoid() = type == DatatypeClass.void

    fun isArray() = type == DatatypeClass.array
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Datatype

        if (type != other.type) return false
        if (name != other.name) return false
        if (isComposite()) {
            if (compositeMembersNullable != other.compositeMembersNullable) {
                return false
            }
        }
        if (isArray()) {
            if (arrayTypeNullable != other.arrayTypeNullable) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (compositeMembersNullable?.hashCode() ?: 0)
        result = 31 * result + (arrayTypeNullable?.hashCode() ?: 0)
        return result
    }

    val compositeMembers: Map<String, Datatype>
        get() {
            assert(isComposite())
            if (isArray()) {
                return mapOf("size" to Integer)
            }
            return compositeMembersNullable!!
        }
    val arrayType: Datatype
        get() {
            assert(isArray())
            return arrayTypeNullable!!
        }

    override fun toString(): String {
        return name
    }

    companion object {
        val Integer = Datatype("integer", DatatypeClass.integer, null, null)
        val Void = Datatype("void", DatatypeClass.void, null, null)
        val Boolean = Datatype("bool", DatatypeClass.bool, null, null)

        fun Composite(name: String, members: Map<String, Datatype>): Datatype {
            return Datatype(name, DatatypeClass.composite, members, null)
        }

        fun Array(arrayType: Datatype): Datatype {
            assert(!arrayType.isArray())
            val name = "array[$arrayType]"
            return Datatype(name, DatatypeClass.array, null, arrayType)
        }
    }
}


class Variable {
    val datatype: Datatype

    private var primitiveValue: Int
    private val compositeValues: MutableMap<String, Variable>?
    private val arrayValues: MutableList<Variable>?

    constructor(datatype: Datatype) {
        assert(datatype.isVoid())
        this.datatype = datatype
        primitiveValue = 0
        compositeValues = null
        arrayValues = null
    }

    constructor(datatype: Datatype, primitiveValue: Int) {
        assert(datatype.isPrimitive())
        this.datatype = datatype
        this.primitiveValue = primitiveValue
        compositeValues = null
        arrayValues = null
    }

    constructor(datatype: Datatype, memberValues: Map<String, Variable>) {
        assert(datatype.isComposite())
        this.datatype = datatype
        primitiveValue = 0
        arrayValues = null

        compositeValues = mutableMapOf()
        for ((memberName, requiredType) in datatype.compositeMembers.entries) {

            val providedVariable = memberValues.getValue(memberName)
            assert(providedVariable.datatype == requiredType)

            compositeValues[memberName] = providedVariable
        }
    }

    constructor(datatype: Datatype, size: Int, sizeAgain: Int) {
        assert(datatype.isArray())
        this.datatype = datatype
        primitiveValue = 0
        compositeValues = null

        arrayValues = (0 until size).map { createDefaultVariable(datatype.arrayType) }.toMutableList()
    }

    fun isPrimitive() = datatype.isPrimitive()

    fun getPrimitiveValue(): Int {
        assert(isPrimitive())
        return primitiveValue
    }

    fun isComposite() = datatype.isComposite()
    fun isArray() = datatype.isArray()


    fun getField(name: String): Variable {
        assert(isComposite())


        if (name !in datatype.compositeMembers) {
            throw WalkerException("${datatype.name} does not contain member $name")
        }
        if (isArray()) {
            assert(name == "size")
            return Variable(Datatype.Integer, arrayValues!!.size)
        }

        return compositeValues!!.getValue(name)
    }

    fun setField(name: String, value: Variable) {
        assert(isComposite())
        if (isArray()) {
            throw WalkerException("Cannot change size of array")
        }

        if (name !in datatype.compositeMembers) {
            throw WalkerException("${datatype.name} does not contain member $name")
        }
        compositeValues!![name] = value
    }

    fun copyFrom(copyFrom: Variable) {
        assert(copyFrom.datatype == datatype)
        primitiveValue = copyFrom.primitiveValue

        if (isArray()) {
            arrayValues!!.clear()
            arrayValues.addAll(copyFrom.arrayValues!!)
        } else if (isComposite()) {
            compositeValues!!.clear()
            compositeValues.putAll(copyFrom.compositeValues!!)
        }
    }

    fun arrayAccess(index: Int): Variable {
        val values = arrayValues!!
        if (index !in values.indices) {
            throw WalkerException("Index out of range")
        }
        return values[index]
    }
}

fun createDefaultVariable(datatype: Datatype): Variable {
    if (datatype.isPrimitive()) {
        return Variable(datatype, 0)
    }
    if (datatype.isArray()) {
        return Variable(datatype, 0, 0)
    }
    if (datatype.isComposite()) {

        val members = datatype.compositeMembers.entries.associate { (name, memberType) ->
            Pair(name, createDefaultVariable(memberType))
        }
        return Variable(datatype, members)
    }
    if (datatype.isVoid()) {
        return Variable(datatype)
    }

    throw WalkerException("Cannot instanciate empty variable of type $datatype")
}

fun createTypeFromNode(node: AstNode, typeProvider: TypeProvider): Datatype {
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
        val type = typeProvider.getType(memberTypeName)

        members[memberName] = type
    }

    return Datatype.Composite(typeName, members)
}