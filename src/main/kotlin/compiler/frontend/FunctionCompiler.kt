package compiler.frontend

import ast.*
import compiler.BuiltInSignatures

const val RETURN_VALUE_NAME = "result"

val functionEntryLabel = Label("function_entry")

class FrontendCompilerError(message: String) : Error(message)


data class FunctionContent(
    val definition: FunctionDefinition,
    val instructions: List<Pair<IRinstruction, List<Label>>>,
) {
    val hasContent
        get() =
            // Check if instructions are empty or only contains a return statement
            instructions.any { it.first !is Return && it.first !is ReturnNothing }// every function  has an implicit return. Ignore that
}

private class CodeBlock(
    val label: Label,
) {

    data class Content(
        val instruction: IRinstruction? = null,
        val codeBlock: CodeBlock? = null,
    )

    val contents = mutableListOf<Content>()

    fun addInstruction(instruction: IRinstruction) {
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

        for (param in symbolTable.getVariablesForFunction(definition)) {
            if (param.variableType == VariableType.Local) {
                mainCodeBlock.addInstruction(TempValue(param.name, AllocStack(param.datatype)))
            }
        }

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
            }

            NodeTypes.Assign -> parseAssign(node, codeBlock)
            NodeTypes.If -> parseIf(node, codeBlock, loopContext)
            NodeTypes.While -> parseWhile(node, codeBlock)
            NodeTypes.Break -> {
                loopContext ?: throw FrontendCompilerError("No loop to break from")
                codeBlock.addInstruction(Jump(loopContext.endLabel))
            }

            NodeTypes.Return -> parseReturn(node, codeBlock)

            NodeTypes.Call -> callAndIgnoreResult(node, codeBlock)

            else -> {
                parseValueExpression(node, codeBlock)
            }
        }
    }

    private fun callAndIgnoreResult(node: AstNode, codeBlock: CodeBlock) {
        require(node.type == NodeTypes.Call)

        parseValueExpression(node, codeBlock)
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

    private fun parseValueRecursive(node: AstNode, currentCodeBlock: CodeBlock): ValueExpr {

        return when (node.type) {
            NodeTypes.Call -> {
                handleCall(node, currentCodeBlock)
            }

            NodeTypes.Constant -> IntConstant(node.asConstant())
            NodeTypes.Identifier -> parseIdentifierAsValue(node.asIdentifier())

            NodeTypes.MemberAccess -> {
                val baseMember = parseValueRecursive(node.child, currentCodeBlock)

                val name = node.data as String
                val type = baseMember.type
                require(type is PointerDatatype)
                if (!(type.pointerType is CompositeDatatype && type.pointerType.containsField(name))) {
                    throw FrontendCompilerError("Type $type contains no field \"$name\"")
                }

                val memberPtr = nextTempValue(GetMemberPtr(baseMember, name))

                currentCodeBlock.addInstruction(memberPtr)
                memberPtr.referTo()
            }

            NodeTypes.ArrayAccess -> {

                val arrayPointer = parseValueExpression(node.asArrayAccess().parent, currentCodeBlock)
                val index = parseValueExpression(node.asArrayAccess().index, currentCodeBlock)


                val arrayPointerType = arrayPointer.type
                require(arrayPointerType is PointerDatatype)
                require(index.type == Primitives.Integer)

                require(arrayPointerType.pointerType is CompositeDatatype)

                val rawArrayPointer = nextTempValue(GetMemberPtr(arrayPointer, "array"))
                currentCodeBlock.addInstruction(rawArrayPointer)

                val indexPtr = nextTempValue(GetElementPtr(rawArrayPointer.referTo(), index))
                currentCodeBlock.addInstruction(indexPtr)

                return indexPtr.referTo()
            }

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

    private fun parseValueExpression(
        node: AstNode,
        currentCodeBlock: CodeBlock,
    ): ValueExpr {

        val expectedType = findTypeOfExpression(node)

        val value = parseValueRecursive(node, currentCodeBlock)

        if (value.type == expectedType) {
            // ok
            return value
        }

        //We got a pointer

        val recievedType = value.type
        require(recievedType is PointerDatatype && recievedType.pointerType == expectedType)

        val load = nextTempValue(Load(value))
        currentCodeBlock.addInstruction(load)

        return load.referTo()
    }

    private fun findTypeOfExpression(
        node: AstNode,

        ): Datatype {
        return when (node.type) {
            NodeTypes.Constant -> Primitives.Integer
            NodeTypes.Identifier -> findTypeOfIdentifier(node.asIdentifier())

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

            NodeTypes.MemberAccess -> {
                var baseType = findTypeOfExpression(node.child)

                if (baseType is PointerDatatype) {
                    baseType = baseType.pointerType
                }

                require(baseType is CompositeDatatype)

                baseType.fieldType(node.data as String)
            }

            NodeTypes.ArrayAccess -> {
                val arrayAccess = node.asArrayAccess()
                var baseType = findTypeOfExpression(arrayAccess.parent)

                if (baseType is PointerDatatype) {
                    baseType = baseType.pointerType
                }

                require(baseType is CompositeDatatype)
                baseType = baseType.fieldType("array")
                require(baseType is RawArrayDatatype)

                baseType.arrayType
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

        val condition = parseValueExpression(ifNode.condition, currentCodeBlock)
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
                    parseValueExpression(ifNode.condition, currentCodeBlock),
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


        val condition = parseValueExpression(whileNode.condition, bodyCodeBlock)
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


        val destination = getDynamicAddress(assign.target, currentCodeBlock)


        val value = parseValueExpression(assign.value, currentCodeBlock)

        currentCodeBlock.addInstruction(Store(value, destination))

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


    private fun getDynamicAddress(targetNode: AstNode, currentCodeBlock: CodeBlock): ValueExpr {
        val allowedNodes = listOf(NodeTypes.Identifier, NodeTypes.MemberAccess, NodeTypes.ArrayAccess)

        if (targetNode.type in allowedNodes) {
            return parseValueRecursive(targetNode, currentCodeBlock)
        }

        throw FrontendCompilerError("Cannot parse node of type ${targetNode.type} to address")
    }

    private fun nextTempValue(value: ValueExpr): TempValue {
        val name = (tempBlockCounter++).toString()
        return TempValue(name, value)
    }

    private fun handleCall(
        callNode: AstNode,
        currentCodeBlock: CodeBlock,
    ): ValueExpr {
        assert(callNode.type == NodeTypes.Call)


        val callInfo = callNode.asCall()

        val parameters = callNode.childNodes.map { parseValueExpression(it, currentCodeBlock) }

        val parameterTypes = parameters.map { it.type }

        val function =
            symbolTable.getFunctionDefinitionMatching(callInfo.targetName, callInfo.functionType, parameterTypes)

        if (function == BuiltInSignatures.createArray) {
            return createArray(Primitives.Integer, parameters.first(), currentCodeBlock)
        }
        if (function == BuiltInSignatures.arraySize) {
            return getArraySize(parameters.first(), currentCodeBlock)
        }


        val call = Call(function, parameters)

        val result = nextTempValue(call)

        currentCodeBlock.addInstruction(result)

        return result.referTo()
    }

    private fun createArray(type: Datatype, size: ValueExpr, currentCodeBlock: CodeBlock): ValueExpr {

        //Create the array
        val arrayPointer = nextTempValue(AllocStackArray(type, size))
        currentCodeBlock.addInstruction(arrayPointer)

        //Pointer to size
        val arraySizePointer = nextTempValue(GetMemberPtr(arrayPointer.referTo(), "size"))
        currentCodeBlock.addInstruction(arraySizePointer)
        currentCodeBlock.addInstruction(Store(size, arraySizePointer.referTo()))

        return arrayPointer.referTo()
    }

    private fun getArraySize(array: ValueExpr, currentCodeBlock: CodeBlock): ValueExpr {

        val arrayPointerType = array.type

        require(arrayPointerType is PointerDatatype)
        require(arrayPointerType.pointerType is CompositeDatatype)

        val sizePointer = nextTempValue(GetMemberPtr(array, "size"))
        currentCodeBlock.addInstruction(sizePointer)

        return sizePointer.referTo()
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

            symbolTable.addVariable(VariableType.LocalParameter, paramType, paramName, definition)

        }

        // VariableDeclarations
        for (node in iterateNodeRecursively(body)) {
            if (node.type == NodeTypes.NewVariable) {
                val newVariable = node.asNewVariable()

                val name = newVariable.name
                val fieldName = newVariable.name


                //TODO handle name clashes
                var type: Datatype
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


    private fun parseIdentifierAsValue(
        name: String,
    ): ValueExpr {


        val variable = symbolTable.findScopedVariable(name, definition, imports)
            ?: throw FrontendCompilerError("variable $name not found")

        return when (variable.variableType) {
            VariableType.Local -> {
                LocalValueRef(variable.name, variable.datatype.pointerOf())
            }

            VariableType.LocalParameter -> {
                LocalValueRef(variable.name, variable.datatype)
            }

            VariableType.Global -> {
                GlobalValueRef(variable.name, variable.datatype.pointerOf())
            }
        }
    }

    private fun findTypeOfIdentifier(name: String): Datatype {
        val variable = symbolTable.findScopedVariable(name, definition, imports)
            ?: throw FrontendCompilerError("variable $name not found")

        return variable.datatype
    }
}


private fun flattenCodeBlock(definition: FunctionDefinition, codeBlock: CodeBlock): FunctionContent {

    val labels = mutableMapOf<Label, Int>()
    val instructions = mutableListOf<IRinstruction>()


    fun placeCodeBlockRecursive(block: CodeBlock) {

        require(!labels.contains(block.label))
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

    val labelsForIndex = mutableMapOf<Int, MutableList<Label>>()

    for ((label, index) in labels.entries) {
        if (index !in labelsForIndex) {
            labelsForIndex[index] = mutableListOf(label)
        } else {
            labelsForIndex.getValue(index).add(label)
        }
    }

    return FunctionContent(definition,
        instructions.withIndex().map { (index, instr) ->
            instr to (labelsForIndex[index] ?: emptyList())
        }
    )
}


