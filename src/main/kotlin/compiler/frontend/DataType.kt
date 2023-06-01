package compiler.frontend

import se.wingez.ast.TypeDefinition

class Datatype private constructor(
    val name: String,
    private val type: DatatypeClass,
    private val compositeMembersNullable: Map<String, Datatype>?,
    private val subTypeNullable: Datatype?,
    val defaultInstanceBehaviour: DefaultInstanceBehaviour,
) {

    private enum class DatatypeClass {
        Void,
        Integer,
        Composite,
        Bool,
        Array,
        Pointer,
    }

    enum class DefaultInstanceBehaviour {
        Raw,
        Pointer,
    }

    fun isComposite() = type == DatatypeClass.Composite || type == DatatypeClass.Array

    fun isPrimitive() = type == DatatypeClass.Integer || type == DatatypeClass.Bool

    fun isPointer() = type == DatatypeClass.Pointer

    fun isVoid() = type == DatatypeClass.Void

    fun isArray() = type == DatatypeClass.Array
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
            if (subTypeNullable != other.subTypeNullable) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (compositeMembersNullable?.hashCode() ?: 0)
        result = 31 * result + (subTypeNullable?.hashCode() ?: 0)
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
            return subTypeNullable!!
        }

    val pointerType: Datatype
        get() {
            assert(isPointer())
            return subTypeNullable!!
        }

    fun instantiate(): Datatype {
        return when (defaultInstanceBehaviour) {
            DefaultInstanceBehaviour.Raw -> this
            DefaultInstanceBehaviour.Pointer -> Pointer(this)
        }
    }

    override fun toString(): String {
        return name
    }

    companion object {
        val Integer = Datatype("integer", DatatypeClass.Integer, null, null, DefaultInstanceBehaviour.Raw)
        val Void = Datatype("void", DatatypeClass.Void, null, null, DefaultInstanceBehaviour.Raw)
        val Boolean = Datatype("bool", DatatypeClass.Bool, null, null, DefaultInstanceBehaviour.Raw)

        fun Composite(name: String, members: Map<String, Datatype>): Datatype {
            return Datatype(name, DatatypeClass.Composite, members, null, DefaultInstanceBehaviour.Pointer)
        }

        fun Array(arrayType: Datatype): Datatype {
            assert(!arrayType.isArray())
            val name = "array[$arrayType]"
            return Datatype(name, DatatypeClass.Array, null, arrayType, DefaultInstanceBehaviour.Pointer)
        }

        fun ArrayPointer(arrayType: Datatype): Datatype {
            return Pointer(Array(arrayType))
        }

        fun Pointer(toType: Datatype): Datatype {
            assert(!toType.isPointer())
            val name = "pointer[$toType]"
            return Datatype(name, DatatypeClass.Pointer, null, toType, DefaultInstanceBehaviour.Raw)
        }
    }
}

interface TypeProvider {
    fun getType(name: String): Datatype
    fun getType(typeDefinition: TypeDefinition): Datatype {
        val typeName = typeDefinition.typeName
        var type = getType(typeName)
        if (typeDefinition.isArray) {
            type = Datatype.Array(type)
        }
        return type
    }
}