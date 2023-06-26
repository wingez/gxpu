package compiler.backends.astwalker

import ast.AstNode
import ast.NodeTypes
import compiler.frontend.Datatype
import compiler.frontend.FunctionDefinitionResolver


data class VariableHandle(
    val accessors: List<String>,
    val type: Datatype,
)

class Value private constructor(
    val datatype: Datatype,
    private val primitiveValue: Int = 0,
    private val pointerTargetHolder: CompositeValueHolder? = null,
    private val arrayValueHolders: List<PrimitiveValueHolder>? = null,
    private val compositeValues: Map<VariableHandle, Value>? = null
) {

    fun isPrimitive() = datatype.isPrimitive
    fun isArray() = datatype.isArray
    fun isPointer() = datatype.isPointer
    fun isComposite() = datatype.isComposite

    fun getPrimitiveValue(): Int {
        assert(isPrimitive())
        return primitiveValue
    }

    val arraySize: Int
        get() {
            check(isArray())
            return arrayValueHolders!!.size
        }

    fun arrayHolderAt(index: Int): PrimitiveValueHolder {
        check(isArray())
        if (index !in arrayValueHolders!!.indices) {
            throw WalkerException("index outside of array")
        }
        return arrayValueHolders[index]
    }

    fun derefPointer(): CompositeValueHolder {
        assert(isPointer())
        return pointerTargetHolder!!
    }

    fun getField(fieldName: String): Value {
        require(isComposite())
        require(datatype.containsField(fieldName))

        val fieldType = datatype.fieldType(fieldName)
        if (!fieldType.isComposite) {
            return compositeValues!!.getValue(VariableHandle(listOf(fieldName), fieldType))
        } else {
            return composite(
                fieldType,
                compositeValues!!.keys.filter { it.accessors.first() == fieldName }.associate {
                    VariableHandle(it.accessors.subList(1, it.accessors.size), it.type) to compositeValues.getValue(it)
                })
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
            return Value(Datatype.Void, 0, null)
        }

        fun primitive(datatype: Datatype, primitiveValue: Int): Value {
            assert(datatype.isPrimitive)
            return Value(datatype, primitiveValue, null)
        }

        fun pointer(datatype: Datatype, pointTo: CompositeValueHolder?): Value {
            if (pointTo != null) {
                require(datatype == pointTo.type)
            }
            return Value(Datatype.Pointer(datatype), 0, pointTo)
        }

        fun array(type: Datatype, holders: List<PrimitiveValueHolder>): Value {
            check(type.isArray)
            return Value(type, arrayValueHolders = holders)
        }

        fun composite(type: Datatype, fields: Map<VariableHandle, Value>): Value {
            require(type.isComposite)
            require(fields.values.all { !it.isComposite() })
            return Value(type, compositeValues = fields)
        }
    }
}

class PrimitiveValueHolder(
    val type: Datatype,
) {
    var value = createDefaultValue(type)
}

fun createDefaultValue(datatype: Datatype): Value {
    if (datatype.isPrimitive) {
        return Value.primitive(datatype, 0)
    }
    if (datatype.isVoid) {
        return Value.void()
    }
    if (datatype.isPointer) {
        return Value.pointer(datatype.pointerType, null)
    }
    if (datatype.isArray) {
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
            assert(arrayPointerType.isPointer)
            val arrayType = arrayPointerType.pointerType
            assert(arrayType.isArray)
            // what this is an array of
            return arrayType.arrayType
        }

        else -> throw WalkerException()
    }
}

interface VariableProvider {
    fun getTypeOfVariable(variableName: String): Datatype
}

fun createFromString(string: String): Value {

    val arrayContentType = Datatype.Integer

    val arrayType = Datatype.Array(arrayContentType)


    val arrayValueHolders = string.map { char ->

        PrimitiveValueHolder(arrayContentType)
            .apply {
                value = Value.primitive(arrayContentType, char.code)
            }
    }

    val arrayHolder = PrimitiveValueHolder(arrayType)
    arrayHolder.value = Value.array(arrayType, arrayValueHolders)

    return Value.pointer(arrayType, CompositeValueHolder(arrayType, emptyMap(), arrayHolder))

}