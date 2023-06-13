package compiler.frontend

import compiler.backends.emulator.*
import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.ast.ParserError
import compiler.backends.emulator.CompileError

class StructBuilder {
    private var currentSize = 0
    private val fields = mutableMapOf<String, StructDataField>()

    private val datatypeLayoutProvider = dummyDatatypeSizeProvider //FIXME

    fun addMember(name: String, type: Datatype): StructBuilder {

        if (name in fields) {
            throw CompileError("Duplicate field: $name")
        }
        val size = datatypeLayoutProvider.sizeOf(type)

        fields[name] = StructDataField(name, type, currentSize, size)
        currentSize += size
        return this
    }

    fun hasField(name: String): Boolean {
        return name in fields
    }

    fun getStruct(name: String): StructType {
        return StructType(name, fields)
    }

    fun getFields(): Map<String, StructDataField> {
        return fields.toMap()
    }

    fun getCurrentSize(): Int {
        return currentSize
    }
}


fun buildStruct(node: AstNode, typeProvider: TypeProvider): StructType {
    val builder = StructBuilder()
    for (member in node.childNodes) {
        if (member.type != NodeTypes.NewVariable)
            throw ParserError("Only primitive declarations allowed for structs. Not $member")

        val memberDeclaration = member.asNewVariable()

        val fieldType: Datatype = if (memberDeclaration.optionalTypeDefinition!!.typeName.isEmpty())
            Datatype.Void
        else {
            throw NotImplementedError()
            //typeProvider.getType(memberDeclaration.optionalTypeDefinition)
        }
        builder.addMember(memberDeclaration.name, fieldType)
    }

    return builder.getStruct(node.data as String)
}