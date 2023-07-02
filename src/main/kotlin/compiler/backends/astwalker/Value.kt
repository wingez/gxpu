package compiler.backends.astwalker

import compiler.frontend.Datatype
import requireNotReached

/*
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
*/
interface VariableProvider {
    fun getTypeOfVariable(variableName: String): Datatype
}

fun createFromString(string: String): Value {
    TODO()
    /*
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
*/
}

data class Value(
    val datatype: Datatype,
    val primitives: List<PrimitiveValue>,
) {
    val asPrimitive: PrimitiveValue
        get() {
            require(datatype.isPrimitive)
            require(primitives.size == 1)
            return primitives.first()
        }

    fun getField(fieldName: String): Value {
        val field = fieldOffset(datatype, fieldName)
        return Value(field.type, primitives.subList(field.offset, field.offset + sizeOf(field.type)))
    }

    companion object {
        val void = Value(Datatype.Void, emptyList())

        fun primitive(datatype: Datatype, value: Int): Value {
            return Value(datatype, listOf(PrimitiveValue.integer(value)))
        }

        fun pointer(value: FieldsHolder.FieldsView): Value {
            return Value(Datatype.Pointer(value.datatype), listOf(PrimitiveValue.pointer(value)))
        }
    }


}

data class PrimitiveValue private constructor(
    val integer: Int,
    private val _pointer: FieldsHolder.FieldsView?,

    ) {
    val pointer
        get() = run {
            requireNotNull(_pointer)
            _pointer
        }

    companion object {
        fun integer(value: Int) = PrimitiveValue(value, null)
        fun pointer(value: FieldsHolder.FieldsView) = PrimitiveValue(0, value)
    }
}

class FieldsHolder(
    val datatype: Datatype,
) {
    init {
        require(datatype.isComposite)
    }

    private val fieldValues = MutableList(sizeOf(datatype)) { PrimitiveValue.integer(0) }

    fun viewEntire(): FieldsView {
        return FieldsView(this, datatype, fieldValues.indices)
    }


    data class FieldsView(
        private val holder: FieldsHolder,
        val datatype: Datatype,
        private val range: IntRange,
    ) {

        val isPrimitive get() = datatype.isPrimitive

        init {
            if (isPrimitive) {
                require(range.count() == 1)
            }
        }

        fun viewField(fieldName: String): FieldsView {
            require(!isPrimitive)

            val field = fieldOffset(datatype, fieldName)
            val start = range.first + field.offset
            return FieldsView(holder, field.type, start until (start + sizeOf(field.type)))
        }

        fun pointerTo(): PrimitiveValue {
            return PrimitiveValue.pointer(this)
        }

        fun getPrimitiveValue(): PrimitiveValue {
            require(isPrimitive)
            return getValue().asPrimitive
        }

        fun setPrimitiveValue(value: PrimitiveValue) {
            require(isPrimitive)
            holder.fieldValues[range.first] = value
        }

        fun getValue(): Value {
            val primitives = List(range.count()) { i -> holder.fieldValues[range.first + i] }
            return Value(datatype, primitives)
        }

        fun applyValue(values: Value) {
            require(values.primitives.size == range.count())
            for ((index, value) in values.primitives.withIndex()) {
                holder.fieldValues[range.first + index] = value
            }
        }
    }


}


fun sizeOf(datatype: Datatype): Int {
    if (datatype.isPrimitive) {
        return 1
    }
    if (datatype.isComposite) {
        return datatype.compositeFields.sumOf { sizeOf(it.type) }
    } else {
        TODO(datatype.toString())
    }
}

private data class FieldOffset(
    val offset: Int,
    val type: Datatype,
)

private fun fieldOffset(datatype: Datatype, fieldName: String): FieldOffset {
    require(datatype.isComposite)

    var offset = 0
    for (field in datatype.compositeFields) {
        if (field.name == fieldName) {
            return FieldOffset(offset, field.type)
        } else {
            offset += sizeOf(field.type)
        }
    }
    requireNotReached()
}

