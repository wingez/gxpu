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
        val Integer = Datatype("integer", DatatypeClass.integer, null, null, ReadBehaviour.Copy)
        val Void = Datatype("void", DatatypeClass.void, null, null, ReadBehaviour.Copy)
        val Boolean = Datatype("bool", DatatypeClass.bool, null, null, ReadBehaviour.Copy)

        fun Composite(name: String, members: Map<String, Datatype>): Datatype {
            return Datatype(name, DatatypeClass.composite, members, null, ReadBehaviour.Reference)
        }

        fun Array(arrayType: Datatype): Datatype {
            assert(!arrayType.isArray())
            val name = "array[$arrayType]"
            return Datatype(name, DatatypeClass.array, null, arrayType, ReadBehaviour.Reference)
        }
    }
}

