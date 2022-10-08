package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes


class Variable private constructor(
    val datatype: Datatype,
    private var primitiveValue: Int,
    private val compositeValues: MutableMap<String, Variable>?,
    private val arrayValues: MutableList<Variable>?,
) {

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
            return Variable.primitive(Datatype.Integer, arrayValues!!.size)
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

    companion object {
        fun void(): Variable {
            return Variable(Datatype.Void, 0, null, null)
        }

        fun primitive(datatype: Datatype, primitiveValue: Int): Variable {
            assert(datatype.isPrimitive())
            return Variable(datatype, primitiveValue, null, null)
        }

        fun composite(datatype: Datatype, memberValues: Map<String, Variable>): Variable {

            assert(datatype.isComposite())

            val compositeValues = mutableMapOf<String, Variable>()
            for ((memberName, requiredType) in datatype.compositeMembers.entries) {

                val providedVariable = memberValues.getValue(memberName)
                assert(providedVariable.datatype == requiredType)

                compositeValues[memberName] = providedVariable
            }
            return Variable(datatype, 0, compositeValues, null)
        }

        fun array(datatype: Datatype, size: Int): Variable {
            assert(datatype.isArray())

            val arrayValues = (0 until size).map { createDefaultVariable(datatype.arrayType) }.toMutableList()
            return Variable(datatype, 0, null, arrayValues)
        }
    }
}

fun createDefaultVariable(datatype: Datatype): Variable {
    if (datatype.isPrimitive()) {
        return Variable.primitive(datatype, 0)
    }
    if (datatype.isArray()) {
        return Variable.array(datatype, 0)
    }
    if (datatype.isComposite()) {

        val members = datatype.compositeMembers.entries.associate { (name, memberType) ->
            Pair(name, createDefaultVariable(memberType))
        }
        return Variable.composite(datatype, members)
    }
    if (datatype.isVoid()) {
        return Variable.void()
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

fun createFromString(string: String): Variable {

    val arrayType = Datatype.Array(Datatype.Integer)
    val stringLength = string.length

    val resultVariable = Variable.array(arrayType, stringLength)

    string.forEachIndexed { index, char ->
        val toAssign = Variable.primitive(Datatype.Integer, char.code)
        resultVariable.arrayAccess(index).copyFrom(toAssign)
    }

    return resultVariable
}