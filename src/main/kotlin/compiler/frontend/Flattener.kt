package se.wingez.compiler.frontend

import compiler.frontend.Datatype
import compiler.frontend.TypeProvider
import se.wingez.ast.*

class FrontendCompilerError(message: String) : Error(message)

interface Instruction {

}

enum class VariableType {
    Local
}

data class Variable(
    val name: String,
    val datatype: Datatype,
    val type: VariableType,
)

interface ValueExpression {
    val type: Datatype
}

class ConstantExpression(
    val value: Int,
) : ValueExpression {
    override val type: Datatype = Datatype.Integer
}

class VariableExpression(
    val variable: Variable,
) : ValueExpression {
    override val type = variable.datatype
}

class CallExpression(
    val function: FunctionDefinition,
    val parameters: List<ValueExpression>
) : ValueExpression {
    override val type: Datatype = function.returnType
}

class Execute(
    val expression: ValueExpression
) : Instruction

class Assign(
    val member: String,
    val value: ValueExpression,
) : Instruction

class CodeBlock(
    val label: String,
) {
    private val contents = mutableListOf<Instruction>()

    val instructions: List<Instruction>
        get() = contents

    fun addInstruction(instruction: Instruction) {
        contents.add(instruction)

    }

    /*fun newCodeBlock(label: String): CodeBlock {
        val new = CodeBlock(label)
        contents.add(Content(instruction = null, codeBlock = new))
        return new
    }*/
}

data class FunctionContent(
    val definition: FunctionDefinition,
    val codeBlock: CodeBlock,
    val localVariables: List<Variable>,
) {

}

private fun assertFunctionNode(node: AstNode): FunctionData {
    assert(node.type == NodeTypes.Function)
    return node.asFunction()
}

private fun flattenFunction(
    functionNode: AstNode,
    functionResolver: FunctionDefinitionResolver,
    variables: List<Variable>
): CodeBlock {
    val function = assertFunctionNode(functionNode)


    val mainCodeBlock = CodeBlock("function: ${function.name}")

    for (expression in functionNode.childNodes) {
        flattenStatement(expression, mainCodeBlock, functionResolver, variables)
    }

    return mainCodeBlock
}


fun compileFunction(
    functionNode: AstNode,
    functionProvider: FunctionDefinitionResolver,
    typeProvider: TypeProvider
): FunctionContent {

    // Step 1
    // Extract definition
    val definition = FunctionDefinition.fromFunctionNode(functionNode, typeProvider)

    // Step 2
    // Extract all local variables
    val localVariables = extractLocalVariables(functionNode, typeProvider, functionProvider)

    //TODO: Perhaps handle inlining here?

    // Step 3
    // Convert to code block
    val functionCodeBlock = flattenFunction(functionNode, functionProvider, localVariables)


    // Return
    return FunctionContent(
        definition = definition,
        codeBlock = functionCodeBlock,
        localVariables = localVariables,
    )

}

private fun flattenStatement(
    node: AstNode,
    codeBlock: CodeBlock,
    functionProvider: FunctionDefinitionResolver,
    variables: List<Variable>,
) {

    val instruction = when (node.type) {

        NodeTypes.NewVariable -> {
            val newVal = node.asNewVariable()
            if (newVal.hasTypeFromAssignment) {
                parseAssign(
                    AstNode.fromAssign(
                        target = AstNode.fromIdentifier(newVal.name),
                        value = newVal.assignmentType
                    ), functionProvider, variables
                )
            } else {
                null
            }
        }

        NodeTypes.Assign -> parseAssign(node, functionProvider, variables)


        else -> {
            val valueExpression = parseExpression(node, functionProvider, variables)
            Execute(valueExpression)
        }
    }

    if (instruction != null) {
        codeBlock.addInstruction(instruction)

    }

}

private fun parseExpression(
    node: AstNode,
    functionProvider: FunctionDefinitionResolver,
    variables: List<Variable>
): ValueExpression {
    return when (node.type) {
        NodeTypes.Call -> {
            flattenCall(node, functionProvider, variables)
        }

        NodeTypes.Constant -> ConstantExpression(node.asConstant())
        NodeTypes.Identifier -> VariableExpression(lookupVariable(node.asIdentifier(), variables))

        else -> throw AssertionError()
    }
}

private fun findTypeOfExpression(
    node: AstNode,
    functionProvider: FunctionDefinitionResolver,
    variables: List<Variable>
): Datatype {
    return parseExpression(node, functionProvider, variables).type
}

private fun parseAssign(
    node: AstNode,
    functionProvider: FunctionDefinitionResolver,
    variables: List<Variable>
): Instruction {
    val assign = node.asAssign()

    assert(assign.target.type == NodeTypes.Identifier)
    val targetName = assign.target.asIdentifier()

    val value = parseExpression(assign.value, functionProvider, variables)

    return Assign(targetName, value)
}

private fun flattenCall(
    callNode: AstNode,
    functionProvider: FunctionDefinitionResolver,
    variables: List<Variable>
): ValueExpression {
    assert(callNode.type == NodeTypes.Call)

    val callInfo = callNode.asCall()

    val parameters = callNode.childNodes.map { parseExpression(it, functionProvider, variables) }

    val parameterTypes = parameters.map { it.type }

    val function =
        functionProvider.getFunctionDefinitionMatching(callInfo.targetName, callInfo.functionType, parameterTypes)


    return CallExpression(function, parameters)
}


private fun extractLocalVariables(
    functionNode: AstNode,
    typeResolver: TypeProvider,
    functionProvider: FunctionDefinitionResolver
): List<Variable> {

    val foundVariables = mutableListOf<Variable>()

    for (node in iterateAstNode(functionNode)) {
        if (node.type == NodeTypes.NewVariable) {
            val newVariable = node.asNewVariable()

            //TODO handle name clashes
            val type: Datatype
            if (newVariable.hasTypeFromAssignment) {
                type = findTypeOfExpression(newVariable.assignmentType, functionProvider, foundVariables)
            } else {
                if (newVariable.optionalTypeDefinition == null) {
                    throw AssertionError()
                }
                type = typeResolver.getType(newVariable.optionalTypeDefinition)
            }

            foundVariables.add(Variable(newVariable.name, type, VariableType.Local))

        }
    }
    return foundVariables
}

fun lookupVariable(name: String, variables: List<Variable>): Variable {
    return variables.find { it.name == name }
        ?: throw FrontendCompilerError("variable $name not found")
}

