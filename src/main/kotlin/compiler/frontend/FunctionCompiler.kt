package compiler.frontend

import ast.*
import compiler.BuiltInSignatures

const val RETURN_VALUE_NAME = "result"

val functionEntryLabel = Label("function_entry")

class FrontendCompilerError(message: String) : Error(message)

interface Instruction

enum class VariableType {
    Local,
    Global,
}

data class Variable(
    val field: CompositeDataTypeField,
    val type: VariableType,
) {
    val name get() = field.name
    val datatype get() = field.type
}

interface ValueExpression {
    val type: Datatype
}

interface AddressExpression {
    val type: Datatype
}

class ConstantExpression(
    val value: Int,
) : ValueExpression {
    override val type: Datatype = Primitives.Integer
}

class VariableExpression(
    val variable: Variable,
) : ValueExpression, AddressExpression {
    override val type = variable.datatype
}


class AddressMemberAccess(
    val of: AddressExpression,
    val memberName: String,
) : AddressExpression {

    init {
        require(of.type is CompositeDatatype)
    }

    override val type: Datatype
        get() = (of.type as CompositeDatatype).fieldType(memberName)
}

class AddressOf(
    val value: AddressExpression
) : ValueExpression {
    override val type: Datatype
        get() = value.type.pointerOf()
}


class DerefToAddress(
    val value: ValueExpression
) : AddressExpression {
    override val type: Datatype
        get() = (value.type as PointerDatatype).pointerType
}

class ValueMemberAccess(val of: ValueExpression, val memberName: String) : ValueExpression {
    override val type: Datatype
        get() = (of.type as CompositeDatatype).fieldType(memberName)
}

class DerefToValue(
    val value: AddressExpression
) : ValueExpression {
    override val type: Datatype
        get() = (value.type as PointerDatatype).pointerType
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
    override val type: Datatype = Primitives.Str
}

data class FunctionReference(
    val function: FunctionDefinition,
) : ValueExpression {
    override val type: Datatype
        get() = FunctionDatatype(function.signature)
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
) {
    val hasContent
        get() =
            // Check if instructions are empty or only contains a return statement
            instructions.any { it !is Return }// every function  has an implicit return. Ignore that
}

data class FunctionContent(
    val definition: FunctionDefinition,
    val code: IntermediateCode,
    val fields: CompositeDatatype,
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

private fun assertFunctionNode(node: AstNode): AstNode.FunctionNode {
    assert(node.type == NodeTypes.Function)
    return node.asFunction()
}

private data class LoopContext(
    val endLabel: Label
)

class FunctionCompiler(
    private var body: AstNode,
    private val definition: FunctionDefinition,
    private val functionProvider: FunctionSignatureResolver,
    private val typeProvider: TypeProvider,
    private val treatNewVariablesAs: VariableType,
    private val globalVariables: List<Variable>,
) {
    lateinit var fieldDatatype: CompositeDatatype
    lateinit var lambdas: List<FunctionContent>

    val variables = mutableListOf<Variable>().apply { addAll(globalVariables) }

    var controlStatementCounter = 0

    fun compileFunction(): List<FunctionContent> {
        require(body.type == NodeTypes.Body)

        // Step 1
        // Extract lambdas
        lambdas = extractLambdas()

        // Step 2
        // Extract all variables
        fieldDatatype = addVariables()

        //TODO: Perhaps handle inlining here?

        //TODO: Perhaps extract strings??

        // Step 3
        // Convert to code block
        val functionCodeBlock = flattenFunction()


        // Step 4
        // Flatten the nested code blocks and place labels
        val codeContent = flattenCodeBlock(functionCodeBlock)

        // Return
        return lambdas + FunctionContent(
            definition = definition,
            fields = fieldDatatype,
            code = codeContent,
        )
    }

    private fun extractLambdas(): List<FunctionContent> {

        val listOfExtractedLambdas = mutableListOf<FunctionContent>()
        var counter = 0

        body = iterateAndModify(body) { node ->
            if (node.type == NodeTypes.Lambda) {

                //TODO: guarantee uniqueness
                val lambdaName = "lambda-${definition.name}-${counter++}"

                val lambdaDefinition = DefinitionBuilder(lambdaName)
                    .setSourceFile(definition.sourceFile)
                    .getDefinition()

                FunctionCompiler(
                    node.child,
                    lambdaDefinition,
                    functionProvider,
                    typeProvider,
                    treatNewVariablesAs,
                    globalVariables
                )
                    .compileFunction()
                    .let { listOfExtractedLambdas.addAll(it) }

                replaceWith(AstNode(NodeTypes.FunctionReference, lambdaName, emptyList(), SourceInfo.notApplicable))
            } else {
                keep()
            }
        }

        return listOfExtractedLambdas
    }

    private fun flattenFunction(): CodeBlock {
        val mainCodeBlock = CodeBlock(functionEntryLabel)

        flattenStatements(body, mainCodeBlock, loopContext = null)

        addReturn(mainCodeBlock) //TODO: only add return if needed

        return mainCodeBlock
    }

    private fun flattenStatements(
        node: AstNode,
        codeBlock: CodeBlock,
        loopContext: LoopContext?
    ) {
        require(node.type == NodeTypes.Body)

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

            NodeTypes.Return -> parseReturn(node, codeBlock)

            else -> {
                val valueExpression = parseValueExpression(node)
                codeBlock.addInstruction(Execute(valueExpression))
            }
        }
    }

    private fun parseReturn(node: AstNode, codeBlock: CodeBlock) {
        if (node.asReturn().hasValue()) {
            parseAssign(
                AstNode.fromAssign(
                    AstNode.fromIdentifier(RETURN_VALUE_NAME, node.sourceInfo),
                    node.child,
                    node.sourceInfo
                ), codeBlock
            )
        }
        addReturn(codeBlock)
    }

    private fun addReturn(codeBlock: CodeBlock) {
        codeBlock.addInstruction(Return())
    }

    private fun parseAddressExpression(
        node: AstNode,
    ): AddressExpression {
        return when (node.type) {
            NodeTypes.Identifier -> VariableExpression(lookupVariable(node.asIdentifier()))

            NodeTypes.Deref -> {
                DerefToAddress(parseValueExpression(node.child))
            }

            NodeTypes.MemberAccess -> {
                val structAddress = parseAddressExpression(node.child)
                val name = node.data as String
                val type = structAddress.type
                if (!(type is CompositeDatatype && type.containsField(name))) {
                    throw FrontendCompilerError("Type ${structAddress.type} contains no field \"$name\"")
                }

                AddressMemberAccess(structAddress, name)
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
            NodeTypes.Identifier -> VariableExpression(lookupVariable(node.asIdentifier()))
            NodeTypes.String -> StringExpression(node.asString())
            NodeTypes.MemberAccess -> parseMemberAccess(node)
            NodeTypes.ArrayAccess -> {
                val member = parseValueExpression(node.asArrayAccess().parent)
                val index = parseValueExpression(node.asArrayAccess().index)

                //TODO: generic-ify
                val definition = BuiltInSignatures.arrayRead
                CallExpression(definition, listOf(member, index))
            }

            NodeTypes.AddressOf -> {
                val pointerTo = parseAddressExpression(node.child)
                AddressOf(pointerTo)
            }

            NodeTypes.Deref -> {
                val pointer = parseAddressExpression(node.child)
                if (pointer.type !is PointerDatatype) {
                    throw FrontendCompilerError("Must be a pointer")
                }
                DerefToValue(pointer)
            }
            NodeTypes.FunctionReference -> {
                // TODO: this only applies to lambdas, make work for anything
                val function = lambdas.find { it.definition.name == node.asIdentifier() }
                require(function != null)
                FunctionReference(function.definition)
            }
            else -> throw AssertionError("Cannot parse node ${node.type} yet")
        }
    }

    private fun parseMemberAccess(node: AstNode): ValueExpression {
        val value = parseValueExpression(node.childNodes.first())
        val memberName = node.asIdentifier()

        val type = value.type

        if (!(type is CompositeDatatype && type.containsField(memberName))) {
            throw FrontendCompilerError("Type ${value.type} has no field $memberName")
        }

        return ValueMemberAccess(value, memberName)
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
        if (condition.type != Primitives.Boolean) {
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
        if (condition.type != Primitives.Boolean) {
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
            if (array.type != Primitives.Integer.arrayPointerOf())
                TODO(array.type.toString())

            val index = parseValueExpression(arrayAccess.index)


            currentCodeBlock.addInstruction(
                Execute(
                    CallExpression(
                        BuiltInSignatures.arrayWrite, listOf(array, index, value)
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


    private fun addVariables(): CompositeDatatype {

        if (definition.returnType != Primitives.Nothing) {
            variables.add(
                Variable(
                    CompositeDataTypeField(
                        RETURN_VALUE_NAME,
                        definition.returnType,
                    ),
                    VariableType.Local,
                )
            )
        }

        for ((paramName, paramType) in definition.parameters) {
            variables.add(Variable(CompositeDataTypeField(paramName, paramType), treatNewVariablesAs))
        }


        for (node in iterateNodeRecursively(body)) {
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
                variables.add(Variable(CompositeDataTypeField(newVariable.name, type), treatNewVariablesAs))
            }
        }
        return CompositeDatatype(
            definition.name,
            variables.filter { it.type == treatNewVariablesAs }.map { it.field })
    }

    private fun lookupVariable(name: String): Variable {
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


