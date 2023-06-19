package compiler.backends.astwalker

import ast.AstNode
import ast.NodeTypes
import compiler.frontend.Datatype
import compiler.frontend.TypeProvider
import compiler.frontend.FunctionDefinitionResolver


class Value private constructor(
    val datatype: Datatype,
    private val primitiveValue: Int = 0,
    private val pointerTargetHolder: ValueHolder? = null,
    private val arrayValueHolders: List<ValueHolder>? = null
) {

    fun isPrimitive() = datatype.isPrimitive()
    fun isArray() = datatype.isArray()

    fun getPrimitiveValue(): Int {
        assert(isPrimitive())
        return primitiveValue
    }

    val arraySize: Int
        get() {
            check(isArray())
            return arrayValueHolders!!.size
        }

    fun arrayHolderAt(index: Int): ValueHolder {
        check(isArray())
        if (index !in arrayValueHolders!!.indices) {
            throw WalkerException("index outside of array")
        }
        return arrayValueHolders[index]
    }

    fun isPointer() = datatype.isPointer()

    fun derefPointer(): ValueHolder {
        assert(isPointer())
        return pointerTargetHolder!!
    }

    override fun toString(): String {
        if (isPrimitive()) {
            return "Variable(type=$datatype, value=$primitiveValue)"
        }
        return super.toString()
    }

    companion object {
        fun void(): Value {
            return Value(Datatype.Void, 0, null)
        }

        fun primitive(datatype: Datatype, primitiveValue: Int): Value {
            assert(datatype.isPrimitive())
            return Value(datatype, primitiveValue, null)
        }

        fun pointer(pointTo: ValueHolder): Value {
            return Value(Datatype.Pointer(pointTo.type), 0, pointTo)
        }

        fun array(type: Datatype, holders: List<ValueHolder>): Value {
            check(type.isArray())
            return Value(type, arrayValueHolders = holders)
        }
    }
}

class ValueHolder(
    val type: Datatype,
) {
    var value = createDefaultValue(type)
}

fun createDefaultValue(datatype: Datatype): Value {
    if (datatype.isPrimitive()) {
        return Value.primitive(datatype, 0)
    }
    if (datatype.isVoid()) {
        return Value.void()
    }
    if (datatype.isPointer()) {
        val holder = ValueHolder(datatype.pointerType)
        holder.value = createDefaultValue(datatype.pointerType)
        return Value.pointer(holder)
    }
    if (datatype.isArray()){
        return Value.array(datatype, emptyList())
    }

    throw WalkerException("Cannot instanciate empty variable of type $datatype")
}

fun findType(
    node: AstNode,
    variableProvider: VariableProvider,
    functionProvider: FunctionDefinitionResolver
): Datatype {

    return when (node.type) {
        NodeTypes.Identifier -> variableProvider.getTypeOfVariable(node.asIdentifier())
        NodeTypes.Constant -> Datatype.Integer
        NodeTypes.String -> Datatype.Array(Datatype.Integer)
        NodeTypes.Call -> {
            val callNode = node.asCall()
            val parameterTypes = callNode.parameters.map { findType(it, variableProvider, functionProvider) }
            return functionProvider.getFunctionDefinitionMatching(
                callNode.targetName, callNode.functionType, parameterTypes
            ).returnType
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
    functionProvider: FunctionDefinitionResolver,
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

    val arrayContentType = Datatype.Integer

    val arrayType = Datatype.Array(arrayContentType)


    val arrayValueHolders = string.map { char ->

        ValueHolder(arrayContentType)
            .apply {
                value = Value.primitive(arrayContentType, char.code)
            }
    }

    val arrayHolder = ValueHolder(arrayType)
    arrayHolder.value = Value.array(arrayType, arrayValueHolders)

    return Value.pointer(arrayHolder)

}