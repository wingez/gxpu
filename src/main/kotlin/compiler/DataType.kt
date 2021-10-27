package se.wingez.compiler

import se.wingez.byte


interface DataType {
    val size: UByte
    val name: String

    fun instantiate(explicitNew: Boolean): DataType
}


interface TypeProvider {
    fun getType(name: String): DataType
}

data class PrimitiveDatatype(
    override val size: UByte,
    override val name: String
) : DataType {
    override fun instantiate(explicitNew: Boolean): DataType {
        if (explicitNew)
            throw CompileError("Datatype $name is primitive and cannot apply 'new'")
        return this
    }
}

val byteType = PrimitiveDatatype(1u, "byte")
val voidType = PrimitiveDatatype(0u, "void")
val stackFrameType = PrimitiveDatatype(byte(SP_STACK_SIZE + PC_STACK_SIZE), "stackFrame")
val compareType = PrimitiveDatatype(0u, "compare")

val DEFAULT_TYPE = byteType

data class Pointer(
    val type: DataType
) : DataType {
    override val size: UByte = byte(POINTER_SIZE)
    override val name: String
        get() = "Pointer<${type.name}"

    override fun instantiate(explicitNew: Boolean): DataType {
        if (explicitNew) {
            throw AssertionError("This makes no sense")
        }
        return this
    }
}


data class StructDataField(
    val name: String,
    val offset: UByte,
    val type: DataType,
)

open class StructType(
    override val name: String,
    val fields: Map<String, StructDataField>,
) : DataType {

    fun hasField(field: String): Boolean {
        return field in fields
    }

    fun getField(field: String): StructDataField {
        return fields.getValue(field)
    }

    override val size: UByte
        get() = byte(fields.values.sumOf { it.type.size.toInt() })

    override fun instantiate(explicitNew: Boolean): DataType {
        if (explicitNew) {
            return this
        }
        return Pointer(this)
    }


    fun getDescription(): List<String> {
        val result = mutableListOf<String>()
        val items = fields.entries.toList()

        items.sortedBy { it.value.offset }.forEach { field ->
            val offset = field.value.offset.toString().padStart(3)
            result.add("$offset: ${field.key}: ${field.value.type.name}")
        }

        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is StructType) return false
        return size == other.size && name == other.name && fields == other.fields
    }


    override fun toString(): String {
        return "name=$name, size=$size, fields=$fields"
    }

    override fun hashCode(): Int {
        var result = size.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + fields.hashCode()
        return result
    }
}