package compiler.frontend

import compiler.backends.emulator.*
import compiler.backends.emulator.TypeProvider
import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.ast.ParserError
import se.wingez.compiler.CompileError

class StructBuilder {
    private var currentSize = 0
    private val fields = mutableMapOf<String, StructDataField>()

    fun addMember(name: String, type: DataType): StructBuilder {

        if (name in fields) {
            throw CompileError("Duplicate field: $name")
        }

        fields[name] = StructDataField(name, currentSize, type)
        currentSize += type.size
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

        val fieldType: DataType = if (memberDeclaration.optionalTypeDefinition!!.typeName.isEmpty())
            DEFAULT_TYPE
        else
            typeProvider.getType(memberDeclaration.optionalTypeDefinition)

        builder.addMember(memberDeclaration.name, fieldType)
    }

    return builder.getStruct(node.data as String)
}