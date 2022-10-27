package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

class Value private constructor(
    val datatype: Datatype,
    private var primitiveValue: Int,
    private val compositeValues: MutableMap<String, Value>?,
    private val arrayValues: MutableList<Value>?,
) {

    fun isPrimitive() = datatype.isPrimitive()

    fun getPrimitiveValue(): Int {
        assert(isPrimitive())
        return primitiveValue
    }

    fun isComposite() = datatype.isComposite()
    fun isArray() = datatype.isArray()


    fun getField(name: String): Value {
        assert(isComposite())


        if (name !in datatype.compositeMembers) {
            throw WalkerException("${datatype.name} does not contain member $name")
        }
        if (isArray()) {
            assert(name == "size")
            return primitive(Datatype.Integer, arrayValues!!.size)
        }

        return compositeValues!!.getValue(name)
    }

    fun setField(name: String, value: Value) {
        assert(isComposite())
        if (isArray()) {
            throw WalkerException("Cannot change size of array")
        }

        if (name !in datatype.compositeMembers) {
            throw WalkerException("${datatype.name} does not contain member $name")
        }
        compositeValues!![name] = value
    }


    fun arrayAccess(index: Int): Value {
        val values = arrayValues!!
        if (index !in values.indices) {
            throw WalkerException("Index out of range")
        }
        return values[index]
    }

    fun read(): Value {
        return when (datatype.readBehaviour) {
            Datatype.ReadBehaviour.Reference -> this
            Datatype.ReadBehaviour.Copy -> copy()
        }
    }

    private fun copy(): Value {
        return Value(datatype, primitiveValue, compositeValues, arrayValues)
    }

    fun copyFrom(copyFrom: Value) {
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

    override fun toString(): String {
        if (isPrimitive()) {
            return "Variable(type=$datatype, value=$primitiveValue)"
        }
        return super.toString()
    }

    companion object {
        fun void(): Value {
            return Value(Datatype.Void, 0, null, null)
        }

        fun primitive(datatype: Datatype, primitiveValue: Int): Value {
            assert(datatype.isPrimitive())
            return Value(datatype, primitiveValue, null, null)
        }

        fun composite(datatype: Datatype, memberValues: Map<String, Value>): Value {

            assert(datatype.isComposite())

            val compositeValues = mutableMapOf<String, Value>()
            for ((memberName, requiredType) in datatype.compositeMembers.entries) {

                val providedVariable = memberValues.getValue(memberName)
                assert(providedVariable.datatype == requiredType)

                compositeValues[memberName] = providedVariable
            }
            return Value(datatype, 0, compositeValues, null)
        }

        fun array(datatype: Datatype, size: Int): Value {
            assert(datatype.isArray())

            val arrayValues = (0 until size).map { createDefaultVariable(datatype.arrayType) }.toMutableList()
            return Value(datatype, 0, null, arrayValues)
        }
    }
}

fun createDefaultVariable(datatype: Datatype): Value {
    if (datatype.isPrimitive()) {
        return Value.primitive(datatype, 0)
    }
    if (datatype.isArray()) {
        return Value.array(datatype, 0)
    }
    if (datatype.isComposite()) {

        val members = datatype.compositeMembers.entries.associate { (name, memberType) ->
            Pair(name, createDefaultVariable(memberType))
        }
        return Value.composite(datatype, members)
    }
    if (datatype.isVoid()) {
        return Value.void()
    }

    throw WalkerException("Cannot instanciate empty variable of type $datatype")
}

fun findType(node: AstNode, variableProvider: VariableProvider, functionProvider: FunctionProvider): Datatype {

    return when (node.type) {
        NodeTypes.Identifier -> variableProvider.getTypeOfVariable(node.asIdentifier())
        NodeTypes.Constant -> Datatype.Integer
        NodeTypes.String -> Datatype.Array(Datatype.Integer)
        NodeTypes.Call -> {
            val callNode = node.asCall()
            val parameterTypes = callNode.parameters.map { findType(it, variableProvider, functionProvider) }
            return functionProvider.getFunctionMatching(callNode.targetName, parameterTypes).definition.returnType
        }

        NodeTypes.ArrayAccess -> {
            val arrayAccess = node.asArrayAccess()

            // datatype for the array
            val arrayType = findType(arrayAccess.parent, variableProvider, functionProvider)
            // what this is an array of
            val type = arrayType.arrayType

            return type
        }

        else -> throw WalkerException()
    }
}

interface VariableProvider {
    fun getTypeOfVariable(variableName: String): Datatype
}

fun createTypeFromNode(
    node: AstNode,
    variableProvider: VariableProvider,
    functionProvider: FunctionProvider,
    typeProvider: TypeProvider
): Datatype {
    assert(node.type == NodeTypes.Struct)

    val members = mutableMapOf<String, Datatype>()
    val typeName = node.data as String

    for (child in node.childNodes) {
        val newValue = child.asNewVariable()

        val type: Datatype
        val optionalTypeDef = newValue.optionalTypeDefinition
        if (optionalTypeDef != null) {
            type = typeProvider.getType(optionalTypeDef)
        } else {
            type = findType(newValue.assignmentType, variableProvider, functionProvider)
        }

        val memberName = newValue.name
        if (memberName in members) {
            throw WalkerException("Member $memberName already exist")
        }

        members[memberName] = type
    }

    return Datatype.Composite(typeName, members)
}

fun createFromString(string: String): Value {

    val arrayType = Datatype.Array(Datatype.Integer)
    val stringLength = string.length

    val resultValue = Value.array(arrayType, stringLength)

    string.forEachIndexed { index, char ->
        val toAssign = Value.primitive(Datatype.Integer, char.code)
        resultValue.arrayAccess(index).copyFrom(toAssign)
    }

    return resultValue
}