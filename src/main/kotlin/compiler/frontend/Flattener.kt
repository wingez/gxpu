package compiler.frontend

import ast.expression.OperatorBuiltIns
import ast.*

const val RETURN_VALUE_NAME = "result"

val functionEntryLabel = Label("function_entry")

class FrontendCompilerError(message: String) : Error(message)

interface Instruction

enum class VariableType {
    Local,
    Parameter,
    Result,
}

data class Variable(
    val name: String,
    val datatype: Datatype,
    val type: VariableType,
)

interface ValueExpression {
    val type: Datatype
}

interface AddressExpression {
    val type: Datatype
}

class ConstantExpression(
    val value: Int,
) : ValueExpression {
    override val type: Datatype = Datatype.Integer
}

class VariableExpression(
    val variable: Variable,
) : ValueExpression, AddressExpression {
    override val type = variable.datatype
}

class AddressOf(
    val value: AddressExpression
) : ValueExpression {
    override val type: Datatype
        get() = Datatype.Pointer(value.type)
}

class DerefToAddress(
    val value: ValueExpression
) : AddressExpression {
    override val type: Datatype
        get() = value.type.pointerType
}

class DerefToValue(
    val value: AddressExpression
) : ValueExpression {
    override val type: Datatype
        get() = value.type.pointerType
}


class MemberAccess(
    val name: String,
    val value: ValueExpression,
) : ValueExpression {
    override val type: Datatype
        get() = value.type.fieldType(name)
}

class CallExpression(
    val function: FunctionDefinition,
    val parameters: List<ValueExpression>
) : ValueExpression {
    override val type: Datatype = function.returnType
}

class StringExpression(
    val string: String
) : ValueExpression {
    override val type: Datatype = Datatype.Str
}

class Execute(
    val expression: ValueExpression
) : Instruction

class Assign(
    val target: AddressExpression,
    val value: ValueExpression,
) : Instruction

class Jump(
    val label: Label
) : Instruction

class JumpOnTrue(
    val condition: ValueExpression,
    val label: Label,
) : Instruction


class JumpOnFalse(
    val condition: ValueExpression,
    val label: Label,
) : Instruction

class Return : Instruction

data class Label(
    val identifier: String
)

data class IntermediateCode(
    val instructions: List<Instruction>,
    val labels: Map<Label, Int>
)

data class FunctionContent(
    val definition: FunctionDefinition,
    val localVariables: List<Variable>,
    val code: IntermediateCode,
)


private class CodeBlock(
    val label: Label,
) {

    data class Content(
        val instruction: Instruction? = null,
        val codeBlock: CodeBlock? = null,
    )

    val contents = mutableListOf<Content>()

    fun addInstruction(instruction: Instruction) {
        contents.add(Content(instruction = instruction))
    }

    fun newCodeBlock(label: Label): CodeBlock {
        val new = CodeBlock(label)
        contents.add(Content(instruction = null, codeBlock = new))
        return new
    }
}

private fun assertFunctionNode(node: AstNode): FunctionData {
    assert(node.type == NodeTypes.Function)
    return node.asFunction()
}

private data class LoopContext(
    val endLabel: Label
)

class FunctionCompiler(
    val functionProvider: FunctionDefinitionResolver,
    val typeProvider: TypeProvider,
) {
    val variables = mutableListOf<Variable>()

    lateinit var definition: FunctionDefinition


    var controlStatementCounter = 0

    fun compileFunction(functionNode: AstNode): FunctionContent {
        assertFunctionNode(functionNode)

        // Step 1
        // Extract definition
        definition = FunctionDefinition.fromFunctionNode(functionNode, typeProvider)

        // Step 2
        // Extract all variables
        addVariables(functionNode)

        //TODO: Perhaps handle inlining here?

        //TODO: Perhaps extract strings??

        // Step 3
        // Convert to code block
        val functionCodeBlock = flattenFunction(functionNode)


        // Step 4
        // Flatten the nested code blocks and place labels
        val codeContent = flattenCodeBlock(functionCodeBlock)

        // Return
        return FunctionContent(
            definition = definition,
            localVariables = variables,
            code = codeContent,
        )
    }


    private fun flattenFunction(
        functionNode: AstNode,
    ): CodeBlock {
        val function = assertFunctionNode(functionNode)

        val mainCodeBlock = CodeBlock(functionEntryLabel)

        flattenStatements(functionNode.childNodes.first(), mainCodeBlock, loopContext = null)

        addReturn(mainCodeBlock) //TODO: only add return if needed

        return mainCodeBlock
    }

    private fun flattenStatements(
        node: AstNode,
        codeBlock: CodeBlock,
        loopContext: LoopContext?
    ) {
        require(node.type==NodeTypes.Body)

        for (statementNode in node.childNodes) {
            flattenStatement(statementNode, codeBlock, loopContext)
        }
    }

    private fun flattenStatement(
        node: AstNode,
        codeBlock: CodeBlock,
        loopContext: LoopContext?,
    ) {

        when (node.type) {

            NodeTypes.NewVariable -> {
                //Do nothing
            }

            NodeTypes.Assign -> parseAssign(node, codeBlock)
            NodeTypes.If -> parseIf(node, codeBlock, loopContext)
            NodeTypes.While -> parseWhile(node, codeBlock)
            NodeTypes.Break -> {
                loopContext ?: throw FrontendCompilerError("No loop to break from")
                codeBlock.addInstruction(Jump(loopContext.endLabel))
            }

            NodeTypes.Return -> addReturn(codeBlock)

            else -> {
                val valueExpression = parseValueExpression(node)
                codeBlock.addInstruction(Execute(valueExpression))
            }
        }
    }

    private fun addReturn(codeBlock: CodeBlock) {
        codeBlock.addInstruction(Return())
    }

    private fun parseAddressExpression(
        node: AstNode,
    ): AddressExpression {
        return when (node.type) {
            NodeTypes.Identifier -> VariableExpression(lookupVariable(node.asIdentifier(), variables))

            NodeTypes.Deref -> {
                DerefToAddress(parseValueExpression(node.child))
            }

            else -> throw FrontendCompilerError("Cannot get address of  ${node.type}")
        }
    }

    private fun parseValueExpression(
        node: AstNode,
    ): ValueExpression {
        return when (node.type) {
            NodeTypes.Call -> {
                flattenCall(node)
            }

            NodeTypes.Constant -> ConstantExpression(node.asConstant())
            NodeTypes.Identifier -> VariableExpression(lookupVariable(node.asIdentifier(), variables))
            NodeTypes.String -> StringExpression(node.asString())
            NodeTypes.MemberAccess -> parseMemberAccess(node)
            NodeTypes.ArrayAccess -> {
                val member = parseValueExpression(node.asArrayAccess().parent)
                val index = parseValueExpression(node.asArrayAccess().index)

                //TODO: generic-ify
                val definition = FunctionDefinition(
                    OperatorBuiltIns.ArrayRead, listOf(Datatype.ArrayPointer(Datatype.Integer), Datatype.Integer),
                    Datatype.Integer, FunctionType.Operator
                )
                CallExpression(definition, listOf(member, index))
            }

            NodeTypes.AddressOf -> {
                val pointerTo = parseAddressExpression(node.child)
                AddressOf(pointerTo)
            }

            NodeTypes.Deref -> {
                val pointer = parseAddressExpression(node.child)
                if (!pointer.type.isPointer) {
                    throw ParserError("Must be a pointer")
                }
                DerefToValue(pointer)
            }

            else -> throw AssertionError("Cannot parse node ${node.type} yet")
        }
    }

    private fun parseMemberAccess(node: AstNode): ValueExpression {
        val value = parseValueExpression(node.childNodes.first())
        val memberName = node.asIdentifier()

        if (!value.type.isComposite || value.type.containsField(memberName)) {
            throw FrontendCompilerError("Type ${value.type} has no field $memberName")
        }

        return MemberAccess(memberName, value)
    }

    private fun findTypeOfExpression(
        node: AstNode,
    ): Datatype {
        return parseValueExpression(node).type
    }

    private fun parseIf(
        node: AstNode,
        currentCodeBlock: CodeBlock,
        loopContext: LoopContext?
    ) {

        val ifNode = node.asIf()

        val condition = parseValueExpression(ifNode.condition)
        if (condition.type != Datatype.Boolean) {
            throw FrontendCompilerError("type of condition must be bool")
        }


        val id = controlStatementCounter++

        val trueLabel = Label("if-$id-true")
        val elseLabel = Label("if-$id-else")
        val endLabel = Label("if-$id-end")


        if (!ifNode.hasElse) {
            //Only if body

            currentCodeBlock.addInstruction(
                JumpOnFalse(
                    condition,
                    endLabel
                )
            )
            val trueBodyCodeBlock = currentCodeBlock.newCodeBlock(trueLabel)
            flattenStatements(ifNode.ifBody, trueBodyCodeBlock, loopContext)

            currentCodeBlock.newCodeBlock(endLabel)

        } else {
            // If and Else

            currentCodeBlock.addInstruction(
                JumpOnFalse(
                    parseValueExpression(ifNode.condition),
                    elseLabel
                )
            )

            val trueBodyCodeBlock = currentCodeBlock.newCodeBlock(trueLabel)
            flattenStatements(ifNode.ifBody, trueBodyCodeBlock, loopContext)
            trueBodyCodeBlock.addInstruction(Jump(endLabel))

            val elseBodyCodeBlock = currentCodeBlock.newCodeBlock(elseLabel)
            flattenStatements(ifNode.elseBody, elseBodyCodeBlock, loopContext)

            currentCodeBlock.newCodeBlock(endLabel)
        }
    }

    private fun parseWhile(
        node: AstNode,
        currentCodeBlock: CodeBlock,
    ) {

        val whileNode = node.asWhile()

        val condition = parseValueExpression(whileNode.condition)
        if (condition.type != Datatype.Boolean) {
            throw FrontendCompilerError("type of condition must be bool, not ${condition.type}")
        }

        val id = controlStatementCounter++

        val whileBodyLabel = Label("while-$id-begin")
        val endLabel = Label("while-$id-end")
        val loopContext = LoopContext(endLabel)

        val bodyCodeBlock = currentCodeBlock.newCodeBlock(whileBodyLabel)


        bodyCodeBlock.addInstruction(
            JumpOnFalse(
                condition,
                endLabel
            )
        )
        flattenStatements(whileNode.body, bodyCodeBlock, loopContext)
        bodyCodeBlock.addInstruction(Jump(whileBodyLabel))
        currentCodeBlock.newCodeBlock(endLabel)

    }


    private fun parseAssign(
        node: AstNode,
        currentCodeBlock: CodeBlock,
    ) {
        val assign = node.asAssign()

        val value = parseValueExpression(assign.value)


        if (assign.target.type == NodeTypes.ArrayAccess) {
            // Special case for writing to array
            // TODO: generic-ify this

            val arrayAccess = assign.target.asArrayAccess()

            val array = parseValueExpression(arrayAccess.parent)
            if (array.type != Datatype.ArrayPointer(Datatype.Integer))
                TODO(array.type.toString())

            val index = parseValueExpression(arrayAccess.index)


            currentCodeBlock.addInstruction(
                Execute(
                    CallExpression(
                        FunctionDefinition(
                            OperatorBuiltIns.ArrayWrite, listOf(
                                Datatype.ArrayPointer(Datatype.Integer), Datatype.Integer,
                                Datatype.Integer
                            ), Datatype.Void, FunctionType.Operator
                        ), parameters = listOf(array, index, value)
                    )
                )
            )
            return
        }

        val target = parseAddressExpression(assign.target)


        currentCodeBlock.addInstruction(Assign(target, value))
    }

    private fun flattenCall(
        callNode: AstNode,
    ): ValueExpression {
        assert(callNode.type == NodeTypes.Call)

        val callInfo = callNode.asCall()

        val parameters = callNode.childNodes.map { parseValueExpression(it) }

        val parameterTypes = parameters.map { it.type }

        val function =
            functionProvider.getFunctionDefinitionMatching(callInfo.targetName, callInfo.functionType, parameterTypes)


        return CallExpression(function, parameters)
    }


    private fun addVariables(
        functionNode: AstNode,
    ) {

        if (definition.returnType != Datatype.Void) {
            variables.add(Variable(RETURN_VALUE_NAME, definition.returnType, VariableType.Result))
        }


        for ((parameterName, type) in parameterTypes(functionNode, typeProvider)) {
            variables.add(Variable(parameterName, type, VariableType.Parameter))
        }


        for (node in iterateAstNode(functionNode)) {
            if (node.type == NodeTypes.NewVariable) {
                val newVariable = node.asNewVariable()

                //TODO handle name clashes
                val type: Datatype
                if (newVariable.hasTypeFromAssignment) {
                    type = findTypeOfExpression(newVariable.assignmentType)
                } else {
                    checkNotNull(newVariable.optionalTypeDefinition)

                    type = typeProvider.getType(newVariable.optionalTypeDefinition)
                        ?: throw FrontendCompilerError("No type of type ${newVariable.optionalTypeDefinition}")
                }

                variables.add(Variable(newVariable.name, type, VariableType.Local))

            }
        }
    }

    private fun lookupVariable(name: String, variables: List<Variable>): Variable {
        return variables.find { it.name == name }
            ?: throw FrontendCompilerError("variable $name not found")
    }
}


private fun flattenCodeBlock(codeBlock: CodeBlock): IntermediateCode {

    val labels = mutableMapOf<Label, Int>()
    val instructions = mutableListOf<Instruction>()


    fun placeCodeBlockRecursive(block: CodeBlock) {

        assert(!labels.contains(block.label))
        labels[block.label] = instructions.size

        for (content in block.contents) {

            if (content.instruction != null) {
                instructions.add(content.instruction)
            } else {
                placeCodeBlockRecursive(content.codeBlock!!)
            }
        }
    }

    placeCodeBlockRecursive(codeBlock)

    return IntermediateCode(instructions, labels)

}


