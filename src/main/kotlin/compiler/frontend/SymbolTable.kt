package compiler.frontend

import ast.*


interface SymbolTable {

    fun getType(name: String): Datatype?

    fun requireType(name: String): Datatype {
        return getType(name)
            ?: throw FrontendCompilerError("Could not find type: $name")
    }

    fun typeExists(name: String): Boolean {
        return getType(name) != null
    }

    fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition
}


private data class TypeEntry(
    val datatype: Datatype,
    val sourceFile: String,
) {
    val name = datatype.name
}


class MutableSymbolTable : SymbolTable {


    private val entries = mutableListOf<TypeEntry>()

    private val functions = mutableListOf<FunctionDefinition>()


    fun addType(type: Datatype, sourceFile: String) {
        if (getType(type.name) != null) {
            throw FrontendCompilerError("Datatype with name: ${type.name} already added")
        }

        val entry = TypeEntry(type, sourceFile)
        entries.add(entry)
    }


    override fun getType(name: String): Datatype? {

        val matches = entries.filter { it.name == name }

        if (matches.isEmpty()) {
            return null
        }
        if (matches.size > 1)
            throw FrontendCompilerError("Multiple entries for $name found")

        return matches.first().datatype
    }


    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition {
        val matches = functions.filter { it.matches(name, functionType, parameterTypes) }

        if (matches.isEmpty())
            throw FrontendCompilerError("No function found matching $name")

        if (matches.size > 1)
            throw FrontendCompilerError("Multiple matches found")

        return matches.first()

    }

    fun addFunction(func: FunctionDefinition) {

        //TODO unique tests
        functions.add(func)
    }
}


