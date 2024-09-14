package compiler.frontend

import ast.*

const val RETURN_VALUE_NAME = "result"

val functionEntryLabel = Label("function_entry")

class FrontendCompilerError(message: String) : Error(message)


data class FunctionContent(
    val definition: FunctionDefinition,
    val instructions: List<Instruction>,
    val labels: Map<Label, Int>,
) {
    val hasContent
        get() =
            // Check if instructions are empty or only contains a return statement
            instructions.any { it !is Return }// every function  has an implicit return. Ignore that
}

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
    private val symbolTable: MutableSymbolTable,
    private val treatNewVariablesAs: VariableType,
    private val imports: List<String>,
) {
    lateinit var lambdas: List<FunctionContent>


    var controlStatementCounter = 0
    var tempBlockCounter = 0

    fun compileFunction(): List<FunctionContent> {
        require(body.type == NodeTypes.Body)

        // Step 1
        // Extract lambdas
        lambdas = extractLambdas()

        // Step 2
        // Extract all variables
        addVariables()

        //TODO: Perhaps handle inlining here?

        //TODO: Perhaps extract strings??

        // Step 3
        // Convert to code block
        val functionCodeBlock = flattenFunction()


        // Step 4
        // Flatten the nested code blocks and place labels
        val codeContent = flattenCodeBlock(definition, functionCodeBlock)

        // Return
        return lambdas + codeContent
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
                    symbolTable,
                    treatNewVariablesAs,
                    imports,
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
                parseValueExpression(node, codeBlock, null)
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
        codeBlock.addInstruction(ReturnNothing())
    }

//    private fun parseAddressExpression(
//        node: AstNode,
//    ): AddressExpression {
//        return when (node.type) {
//            NodeTypes.Identifier -> VariableExpression(lookupVariable(node.asIdentifier()))
//
//            NodeTypes.Deref -> {
//                DerefToAddress(parseValueExpression(node.child))
//            }
//
//            NodeTypes.MemberAccess -> {
//                val structAddress = parseAddressExpression(node.child)
//                val name = node.data as String
//                val type = structAddress.type
//                if (!(type is CompositeDatatype && type.containsField(name))) {
//                    throw FrontendCompilerError("Type ${structAddress.type} contains no field \"$name\"")
//                }
//
//                AddressMemberAccess(structAddress, name)
//            }
//
//            else -> throw FrontendCompilerError("Cannot get address of  ${node.type}")
//        }
//    }

    private fun parseValueExpression(
        node: AstNode,
        currentCodeBlock: CodeBlock,
        placeCallResultIn: String?
    ): ValueExpr {
        return when (node.type) {
            NodeTypes.Call -> {
                handleCall(node, currentCodeBlock, placeCallResultIn)
            }

            NodeTypes.Constant -> IntConstant(node.asConstant())
            NodeTypes.Identifier -> parseIdentifier(node.asIdentifier())
            //NodeTypes.String -> StringExpression(node.asString())
            //NodeTypes.MemberAccess -> parseMemberAccess(node)
//            NodeTypes.ArrayAccess -> {
//                val member = parseValueExpression(node.asArrayAccess().parent)
//                val index = parseValueExpression(node.asArrayAccess().index)
//
//                //TODO: generic-ify
//                val definition = BuiltInSignatures.arrayRead
//                CallExpression(definition, listOf(member, index))
//            }

//            NodeTypes.AddressOf -> {
//                val pointerTo = parseAddressExpression(node.child)
//                AddressOf(pointerTo)
//            }

//            NodeTypes.Deref -> {
//                val pointer = parseAddressExpression(node.child)
//                if (pointer.type !is PointerDatatype) {
//                    throw FrontendCompilerError("Must be a pointer")
//                }
//                DerefToValue(pointer)
//            }

//            NodeTypes.FunctionReference -> {
//                // TODO: this only applies to lambdas, make work for anything
//                val function = lambdas.find { it.definition.functionName == node.asIdentifier() }
//                require(function != null)
//                FunctionReference(function.definition)
//            }

            else -> throw AssertionError("Cannot parse node ${node.type} yet")
        }
    }

//    private fun parseMemberAccess(node: AstNode): ValueExpression {
//        val value = parseValueExpression(node.childNodes.first())
//        val memberName = node.asIdentifier()
//
//        val type = value.type
//
//        if (!(type is CompositeDatatype && type.containsField(memberName))) {
//            throw FrontendCompilerError("Type ${value.type} has no field $memberName")
//        }
//
//        return ValueMemberAccess(value, memberName)
//    }

    private fun findTypeOfExpression(
        node: AstNode,

        ): Datatype {
        return when (node.type) {
            NodeTypes.Constant -> Primitives.Integer
            NodeTypes.Identifier -> parseIdentifier(node.asIdentifier()).type

            NodeTypes.Call -> {
                val callInfo = node.asCall()
                val parameterTypes = callInfo.parameters.map { findTypeOfExpression(it) }

                val function =
                    symbolTable.getFunctionDefinitionMatching(
                        callInfo.targetName,
                        callInfo.functionType,
                        parameterTypes
                    )

                return function.returnType
            }

            else -> TODO(node.type.toString())
        }
    }

    private fun parseIf(
        node: AstNode,
        currentCodeBlock: CodeBlock,
        loopContext: LoopContext?
    ) {
        val ifNode = node.asIf()

        val condition = parseValueExpression(ifNode.condition, currentCodeBlock, null)
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
                    parseValueExpression(ifNode.condition, currentCodeBlock, null),
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


        val id = controlStatementCounter++

        val whileBodyLabel = Label("while-$id-begin")
        val endLabel = Label("while-$id-end")
        val loopContext = LoopContext(endLabel)

        val bodyCodeBlock = currentCodeBlock.newCodeBlock(whileBodyLabel)


        val condition = parseValueExpression(whileNode.condition, bodyCodeBlock, null)
        if (condition.type != Primitives.Boolean) {
            throw FrontendCompilerError("type of condition must be bool, not ${condition.type}")
        }


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


        assert(assign.target.type == NodeTypes.Identifier)

        val targetVariableName = assign.target.asIdentifier()
        val targetVariable = symbolTable.findScopedVariable(targetVariableName, definition, imports)!!


        val placeCallResultIn = when (targetVariable.variableType) {
            VariableType.Local -> targetVariableName
            VariableType.Global -> nextTempValue()
        }


        val valueIsIn = parseValueExpression(assign.value, currentCodeBlock, placeCallResultIn)

        currentCodeBlock.addInstruction(TempValue(placeCallResultIn, valueIsIn))


        assert(targetVariable.datatype == valueIsIn.type)

        if (targetVariable.variableType == VariableType.Global) {
            TODO("STORE")
        }
//        if (targetVariable.variableType == VariableType.Local) {
//            currentCodeBlock.addInstruction(TempValue(placeCallResultIn, valueIsIn))
//        }


//
//        if (assign.target.type == NodeTypes.ArrayAccess) {
//            // Special case for writing to array
//            // TODO: generic-ify this
//
//            val arrayAccess = assign.target.asArrayAccess()
//
//            val array = parseValueExpression(arrayAccess.parent)
//            if (array.type != Primitives.Integer.arrayPointerOf())
//                TODO(array.type.toString())
//
//            val index = parseValueExpression(arrayAccess.index)
//
//
//            currentCodeBlock.addInstruction(
//                Execute(
//                    CallExpression(
//                        BuiltInSignatures.arrayWrite, listOf(array, index, value)
//                    )
//                )
//            )
//            return
//        }
//
//        val target = parseAddressExpression(assign.target)
//
//
//        currentCodeBlock.addInstruction(Assign(target, value))
    }

    private fun nextTempValue(): String {
        return (tempBlockCounter++).toString()
    }

    private fun handleCall(
        callNode: AstNode,
        currentCodeBlock: CodeBlock,
        placeCallResultIn: String?,
    ): ValueExpr {
        assert(callNode.type == NodeTypes.Call)

        val callInfo = callNode.asCall()

        val parameters = callNode.childNodes.map { parseValueExpression(it, currentCodeBlock, null) }

        val parameterTypes = parameters.map { it.type }

        val function =
            symbolTable.getFunctionDefinitionMatching(callInfo.targetName, callInfo.functionType, parameterTypes)

        val tempValName = placeCallResultIn ?: nextTempValue()

        currentCodeBlock.addInstruction(TempValue(tempValName, Call(function, parameters)))

        return LocalValueRef(tempValName, function.returnType)
    }


    private fun addVariables(
    ) {

        // Result variable
        if (definition.returnType != Primitives.Nothing) {
            require(treatNewVariablesAs == VariableType.Local)

            symbolTable.addVariable(
                treatNewVariablesAs,
                definition.returnType,
                RETURN_VALUE_NAME,
                definition,
            )
        }

        // Params
        for ((paramName, paramType) in definition.parameters) {
            require(treatNewVariablesAs == VariableType.Local)

            symbolTable.addVariable(VariableType.Local, paramType, paramName, definition)

        }

        // VariableDeclarations
        for (node in iterateNodeRecursively(body)) {
            if (node.type == NodeTypes.NewVariable) {
                val newVariable = node.asNewVariable()

                val name = newVariable.name
                val fieldName = newVariable.name


                //TODO handle name clashes
                val type: Datatype
                if (newVariable.hasTypeFromAssignment) {
                    type = findTypeOfExpression(newVariable.assignmentType)
                } else {
                    checkNotNull(newVariable.optionalTypeDefinition)

                    type = requireTypeFromTypeDefinition(newVariable.optionalTypeDefinition, symbolTable)
                }
                symbolTable.addVariable(treatNewVariablesAs, type, name, definition)
            }
        }
    }


    private fun parseIdentifier(name: String): ValueExpr {


        val variable = symbolTable.findScopedVariable(name, definition, imports)
            ?: throw FrontendCompilerError("variable $name not found")

        return when (variable.variableType) {
            VariableType.Local -> {
                LocalValueRef(variable.name, variable.datatype)
            }

            else -> TODO(variable.variableType.toString())
        }


    }
}


private fun flattenCodeBlock(definition: FunctionDefinition, codeBlock: CodeBlock): FunctionContent {

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

    return FunctionContent(definition, instructions, labels)

}


