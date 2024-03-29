package compiler.backends.astwalker

import compiler.frontend.*
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

data class Value(
    val datatype: Datatype,
    val primitives: List<PrimitiveValue>,
) {
    val asPrimitive: PrimitiveValue
        get() {
            require(datatype is PrimitiveDataType || datatype is PointerDatatype || datatype is FunctionDatatype)
            require(primitives.size == 1)
            return primitives.first()
        }

    fun getField(fieldName: String): Value {
        require(datatype is CompositeDatatype)
        val field = fieldOffset(datatype, fieldName)
        return Value(field.type, primitives.subList(field.offset, field.offset + sizeOf(field.type)))
    }

    companion object {
        val nothing = Value(Primitives.Nothing, emptyList())

        fun primitive(datatype: Datatype, value: Int): Value {
            return Value(datatype, listOf(PrimitiveValue.integer(value)))
        }

        fun pointer(value: ValueHolder.View): Value {
            return Value(value.datatype.pointerOf(), listOf(PrimitiveValue.pointer(value)))
        }
    }


}

data class PrimitiveValue private constructor(
    val integer: Int,
    private val _pointer: ValueHolder.View?,

    ) {
    val pointer
        get() = run {
            requireNotNull(_pointer)
            _pointer
        }

    companion object {
        fun integer(value: Int) = PrimitiveValue(value, null)
        fun pointer(value: ValueHolder.View) = PrimitiveValue(0, value)
    }
}

class ValueHolder(
    val datatype: Datatype,
    arraySize: Int = -1
) {

    val primitives: MutableList<PrimitiveValue>

    init {
        require(datatype is CompositeDatatype || datatype is ArrayDatatype)

        val size = when (datatype) {
            is CompositeDatatype -> sizeOf(datatype)
            is ArrayDatatype -> {
                require(arraySize >= 0)
                sizeOf(datatype.arrayType) * arraySize
            }

            else -> requireNotReached()
        }
        primitives = MutableList(size) { PrimitiveValue.integer(0) }
    }

    fun viewEntire(): View {
        return View(this, datatype, primitives.indices)
    }


    data class View(
        private val holder: ValueHolder,
        val datatype: Datatype,
        private val range: IntRange,
    ) {

        val isPrimitive get() = datatype is PrimitiveDataType || datatype is PointerDatatype

        init {
            if (isPrimitive) {
                require(range.count() == 1)
            }
        }

        fun viewField(fieldName: String): View {
            require(datatype is CompositeDatatype)

            val field = fieldOffset(datatype, fieldName)
            val start = range.first + field.offset
            return View(holder, field.type, start until (start + sizeOf(field.type)))
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
            holder.primitives[range.first] = value
        }

        fun getValue(): Value {
            val primitives = List(range.count()) { i -> holder.primitives[range.first + i] }
            return Value(datatype, primitives)
        }

        fun applyValue(values: Value) {
            require(values.primitives.size == range.count())
            for ((index, value) in values.primitives.withIndex()) {
                holder.primitives[range.first + index] = value
            }
        }

        fun arrayRead(index: Int): View {
            require(datatype is ArrayDatatype)
            val elementSize = sizeOf(datatype.arrayType)

            val startPos = elementSize * index + range.first

            return View(holder, datatype.arrayType, startPos until startPos + elementSize)
        }

        fun arraySize(): Int {
            require(datatype is ArrayDatatype)
            require(datatype.arrayType == Primitives.Integer)
            return range.count()
        }
    }
}

fun sizeOf(datatype: Datatype): Int {

    return when (datatype) {
        Primitives.Nothing -> requireNotReached()
        is PrimitiveDataType, is PointerDatatype -> 1
        is CompositeDatatype -> datatype.compositeFields.sumOf { sizeOf(it.type) }
        else -> TODO(datatype.toString())
    }
}

private data class FieldOffset(
    val offset: Int,
    val type: Datatype,
)

private fun fieldOffset(datatype: CompositeDatatype, fieldName: String): FieldOffset {
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

