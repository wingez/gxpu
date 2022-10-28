package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

class Value private constructor(
    val datatype: Datatype,
    private val primitiveValue: Int,
    private val compositeValueHolders: Map<String, ValueHolder>?,
    private val arrayValueHolders: List<ValueHolder>?,
    private val pointerTargetHolder: IValueHolder?,
) {

    fun isPrimitive() = datatype.isPrimitive()

    fun getPrimitiveValue(): Int {
        assert(isPrimitive())
        return primitiveValue
    }

    fun isComposite() = datatype.isComposite()
    fun isArray() = datatype.isArray()

    fun isPointer() = datatype.isPointer()

    fun derefPointer(): IValueHolder {
        assert(isPointer())
        return pointerTargetHolder!!
    }

    fun getFieldValueHolder(name: String): IValueHolder {
        assert(isComposite())


        if (name !in datatype.compositeMembers) {
            throw WalkerException("${datatype.name} does not contain member $name")
        }
        if (isArray()) {
            assert(name == "size")
            return ConstantValueHolder(Value.primitive(Datatype.Integer, arrayValueHolders!!.size))
        }
        return compositeValueHolders!!.getValue(name)
    }

    fun setField(name: String, value: Value) {
        assert(isComposite())
        if (isArray()) {
            throw WalkerException("Cannot change size of array")
        }

        if (name !in datatype.compositeMembers) {
            throw WalkerException("${datatype.name} does not contain member $name")
        }
        val holder = compositeValueHolders!!.getValue(name)
        holder.value = value
    }


    fun arrayAccess(index: Int): ValueHolder {
        val values = arrayValueHolders!!
        if (index !in values.indices) {
            throw WalkerException("Index out of range")
        }
        return values[index]
    }

    override fun toString(): String {
        if (isPrimitive()) {
            return "Variable(type=$datatype, value=$primitiveValue)"
        }
        return super.toString()
    }

    companion object {
        fun void(): Value {
            return Value(Datatype.Void, 0, null, null, null)
        }

        fun primitive(datatype: Datatype, primitiveValue: Int): Value {
            assert(datatype.isPrimitive())
            return Value(datatype, primitiveValue, null, null, null)
        }

        fun composite(datatype: Datatype, memberValues: Map<String, Value>): Value {

            assert(datatype.isComposite())

            val compositeValues = mutableMapOf<String, ValueHolder>()
            for ((memberName, requiredType) in datatype.compositeMembers.entries) {

                val providedValue = memberValues.getValue(memberName)
                assert(providedValue.datatype == requiredType)

                val holder = ValueHolder(requiredType)
                holder.value = providedValue

                compositeValues[memberName] = holder
            }
            return Value(datatype, 0, compositeValues, null, null)
        }

        fun array(datatype: Datatype, size: Int): Value {
            assert(datatype.isArray())

            val arrayValues = (0 until size).map { createDefaultValue(datatype.arrayType) }
                .map {
                    val holder = ValueHolder(it.datatype)
                    holder.value = it
                    holder
                }.toMutableList()

            return Value(datatype, 0, null, arrayValues, null)
        }

        fun pointer(pointTo: IValueHolder): Value {
            return Value(Datatype.Pointer(pointTo.type), 0, null, null, pointTo)
        }
    }
}

interface IValueHolder {
    val type: Datatype
    var value: Value
}

private class ConstantValueHolder(private val constant: Value) : IValueHolder {
    override val type = constant.datatype
    override var value: Value
        get() = constant
        set(value) {
            throw WalkerException("Cannot assign to constant")
        }
}

class ValueHolder(
    override val type: Datatype,
) : IValueHolder {

    override var value = createDefaultValue(type)
}

fun createDefaultValue(datatype: Datatype): Value {
    if (datatype.isPrimitive()) {
        return Value.primitive(datatype, 0)
    }
    if (datatype.isArray()) {
        return Value.array(datatype, 0)
    }
    if (datatype.isComposite()) {

        val members = datatype.compositeMembers.entries.associate { (name, memberType) ->
            Pair(name, createDefaultValue(memberType))
        }
        return Value.composite(datatype, members)
    }
    if (datatype.isVoid()) {
        return Value.void()
    }
    if (datatype.isPointer()) {
        val holder = ValueHolder(datatype.pointerType)
        holder.value = createDefaultValue(datatype.pointerType)
        return Value.pointer(holder)
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
            val arrayPointerType = findType(arrayAccess.parent, variableProvider, functionProvider)
            assert(arrayPointerType.isPointer())
            val arrayType = arrayPointerType.pointerType
            assert(arrayType.isArray())
            // what this is an array of
            return arrayType.arrayType
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
        resultValue.arrayAccess(index).value = toAssign
    }

    val holder = ConstantValueHolder(resultValue)

    return Value.pointer(holder)
}