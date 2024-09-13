package compiler.frontend

import ast.*


enum class VariableType {
    Local,
    Global,
}


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

    fun findScopedVariable(name: String, owner: FunctionDefinition, imports: List<String>): Variable?

    fun getVariablesForFunction(owner: FunctionDefinition): List<Variable>

    fun getAllGlobalVariables(): List<Variable>
}


private data class TypeEntry(
    val datatype: Datatype,
    val sourceFile: String,
) {
    val name = datatype.name
}


data class Variable(
    val datatype: Datatype,
    val variableType: VariableType,
    val name: String,
    val owner: FunctionDefinition,
) {
    val sourceFile = owner.sourceFile

}

class MutableSymbolTable : SymbolTable {


    private val types = mutableListOf<TypeEntry>()

    private val functions = mutableListOf<FunctionDefinition>()

    private val variables = mutableListOf<Variable>()

    fun addType(type: Datatype, sourceFile: String) {
        if (getType(type.name) != null) {
            throw FrontendCompilerError("Datatype with name: ${type.name} already added")
        }

        val entry = TypeEntry(type, sourceFile)
        types.add(entry)
    }


    override fun getType(name: String): Datatype? {

        val matches = types.filter { it.name == name }

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


    fun addVariable(
        type: VariableType,
        datatype: Datatype,
        name: String,
        owner: FunctionDefinition
    ) {

        variables.add(Variable(datatype, type, name, owner))
    }

    override fun findScopedVariable(name: String, owner: FunctionDefinition, imports: List<String>): Variable? {

        //First search local
        var match = variables.find { it.name == name && it.owner == owner && it.variableType == VariableType.Local }
        if (match != null)
            return match

        // Then global in same file

        match =
            variables.find { it.name == name && it.sourceFile == owner.sourceFile && it.variableType == VariableType.Global }
        if (match != null)
            return match

        for (import in imports) {
            match =
                variables.find { it.name == name && it.sourceFile == import && it.variableType == VariableType.Global }
            if (match != null)
                return match
        }

        return null
    }

    override fun getVariablesForFunction(owner: FunctionDefinition): List<Variable> {
        return variables.filter { it.owner == owner }
    }

    override fun getAllGlobalVariables(): List<Variable> {
        return variables.filter { it.variableType == VariableType.Global }
    }
}


