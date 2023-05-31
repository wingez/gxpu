package se.wingez.compiler.flattener

import se.wingez.ast.AstNode
import se.wingez.ast.FunctionType
import se.wingez.ast.NodeTypes
import se.wingez.ast.iterateAstNode
import se.wingez.astwalker.*
import se.wingez.compiler.frontend.FunctionDefinition
import se.wingez.compiler.frontend.FunctionProvider
import se.wingez.compiler.frontend.IFunction

interface Instruction {

}

interface ValueExpression {
    val type: Datatype
}

class ConstantExpression(
    val value: Int,
) : ValueExpression {
    override val type: Datatype = Datatype.Integer
}

class CallExpression(
    val function: IFunction,
    val parameters: List<ValueExpression>
) : ValueExpression {
    override val type: Datatype = function.definition.returnType
}

class Execute(
    val expression: ValueExpression
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

class Function(
    override val definition: FunctionDefinition,
    val codeBlock: CodeBlock,
) : IFunction {

}

private fun assertFunctionNode(node: AstNode) {
    assert(node.type == NodeTypes.Function)
}


fun <T : IFunction> flattenFunction(functionNode: AstNode, functionProvider: FunctionProvider<T>): Function {

    assertFunctionNode(functionNode)

    val function = functionNode.asFunction()

    //TODO add parameters
    val definition = FunctionDefinition(
        name = function.name,
        parameterTypes = emptyList(),
        returnType = Datatype.Void,
        functionType = FunctionType.Normal,
    )

    val mainCodeBlock = CodeBlock("function: ${function.name}")

    for (expression in functionNode.childNodes) {
        flattenExpression(expression, mainCodeBlock, functionProvider)
    }
    return Function(definition, mainCodeBlock)
}

private fun <T : IFunction> flattenExpression(
    node: AstNode,
    codeBlock: CodeBlock,
    functionProvider: FunctionProvider<T>
) {

    val valueExpression = parseExpression(node, functionProvider)

    val instruction = Execute(valueExpression)
    codeBlock.addInstruction(instruction)

}

private fun <T : IFunction> parseExpression(node: AstNode, functionProvider: FunctionProvider<T>): ValueExpression {
    return when (node.type) {
        NodeTypes.Call -> {
            flattenCall(node, functionProvider)
        }
        NodeTypes.Constant -> ConstantExpression(node.asConstant())

        else -> throw AssertionError()
    }
}

private fun <T : IFunction> flattenCall(callNode: AstNode, functionProvider: FunctionProvider<T>): ValueExpression {
    assert(callNode.type == NodeTypes.Call)

    val callInfo = callNode.asCall()

    val parameters = callNode.childNodes.map { parseExpression(it, functionProvider) }

    val parameterTypes = parameters.map { it.type }

    val function = functionProvider.getFunctionMatching(callInfo.targetName, callInfo.functionType, parameterTypes)


    return CallExpression(function, parameters)
}


fun compile(functionNode: AstNode, typeResolver: TypeProvider) {

    assertFunctionNode(functionNode)

    // Step 1
    // Extract all local variables
    val localVariables = extractLocalVariables(functionNode, typeResolver)

    //TODO: Perhaps handle inlining here?

    // Step 2
    // Convert to code block
    //val functionCodeBlock = flattenFunction(functionNode)


}

private data class LocalVariable(
    val name: String,
    val type: Datatype,
)


private fun extractLocalVariables(functionNode: AstNode, typeResolver: TypeProvider): List<LocalVariable> {

    val foundVariables = mutableListOf<LocalVariable>()

    for (node in iterateAstNode(functionNode)) {
        if (node.type == NodeTypes.NewVariable) {
            val newVariable = node.asNewVariable()

            //TODO handle name clashes

            val type = typeResolver.getType(newVariable.optionalTypeDefinition!!)

            foundVariables.add(LocalVariable(newVariable.name, type))

        }
    }
    return foundVariables
}



