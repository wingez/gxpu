package compiler.backends.machineCode

import compiler.BuiltInSignatures
import compiler.frontend.*
import requireNotReached


enum class CallType {
    External,
    WeShouldOptimize,
    Return,
    Jump,
}

private class Action(
    val type: CallType,
    val definition: FunctionDefinition?,
) {
    override fun toString(): String {
        return "$type, $definition"
    }
}

private enum class ValueType {
    InSomeRegister,
    StackVariable,
}

private class TempValueState(
    val type: ValueType,
    val declared: Int,
) {
    val usedIn = mutableListOf<Int>()

    var registerWishList = mutableListOf<Register>()

    fun use(atIndex: Int) {
        usedIn.add(atIndex)
    }

    private fun calcLifeSpan(): String {
        if (usedIn.isEmpty()) {
            return "unused"
        }
        return "$declared to ${usedIn.maxOrNull()}"
    }

    override fun toString(): String {
        return "Type: $type, Created: $declared, Uses = ${usedIn.joinToString(", ")}, Lifespan = ${calcLifeSpan()}"
    }

}


fun doInitialPass(
    functionDefinition: FunctionDefinition,
    instructions: List<IRinstruction>
): Map<String, AllocationResult> {

    val actions = mutableListOf<Action>()
    val valueStates = mutableMapOf<String, TempValueState>()



    for ((paramName, paramType) in functionDefinition.parameters) {
        val someRegister = TempValueState(ValueType.InSomeRegister, 0)

        valueStates[paramName] = someRegister

    }


    for (instr in instructions) {

        when (instr) {
            is TempValue -> {
                val value = instr.name

                when (instr.value) {
                    is AllocStack -> {
                        valueStates[value] = TempValueState(ValueType.StackVariable, actions.size)
                    }

                    is Load -> {
                        val toLoad = instr.value.value
                        require(toLoad is LocalValueRef)
                        val state = valueStates.getValue(toLoad.name)

                        when (state.type) {
                            ValueType.StackVariable -> {
                                valueStates[value] = TempValueState(ValueType.StackVariable, actions.size)
                            }

                            else -> TODO()
                        }
                    }

                    is Call -> {
                        val call = instr.value

                        val isExternal = isExternal(call.func)
                        val callType = if (isExternal) CallType.External else CallType.WeShouldOptimize

                        for ((paramIndex, param) in call.params.withIndex()) {
                            if (param !is LocalValueRef) {
                                continue
                            }
                            val state = valueStates.getValue(param.name)
                            if (isExternal)
                                state.use(actions.size)
                            state.registerWishList.add(callRegisterOrder[paramIndex])
                        }
                        actions.add(Action(callType, call.func))
                        valueStates[value] = TempValueState(ValueType.InSomeRegister, actions.size)
                    }

                    else -> TODO()
                }
            }

            is Return -> {
                val value = instr.value
                if (value is LocalValueRef) {
                    val state = valueStates.getValue(value.name)
                    state.use(actions.size)
                    state.registerWishList.add(Register.RAX)
                }
                actions.add(Action(CallType.Return, null))
            }

            is ReturnNothing -> {
                actions.add(Action(CallType.Return, null))
            }

            is Store -> {

            }

            is Jump -> {

            }

            is JumpOnTrue -> {
                val value = instr.condition
                if (value is LocalValueRef) {
                    val state = valueStates.getValue(value.name)
                    state.use(actions.size)
                    state.registerWishList.add(Register.RAX)
                }
                actions.add(Action(CallType.Jump, null))
            }

            is JumpOnFalse -> {
                val value = instr.condition
                if (value is LocalValueRef) {
                    val state = valueStates.getValue(value.name)
                    state.use(actions.size)
                    state.registerWishList.add(Register.RAX)
                }
                actions.add(Action(CallType.Jump, null))
            }

            else -> TODO(instr.toString())
        }
    }

    println("CALLS")
    for (action in actions) {
        println(action)
    }

    println("ValueStates")
    for ((value, state) in valueStates.entries) {
        println("$value: $state")
    }

    return allocate(actions, valueStates, functionDefinition)
}

enum class AllocationResultType {
    Unused,
    InRegister,
    OnStack,
}

data class AllocationResult(val type: AllocationResultType, val register: Register?)


private fun allocate(
    actions: List<Action>,
    toAllocate: Map<String, TempValueState>,
    func: FunctionDefinition
): MutableMap<String, AllocationResult> {

    val allocations = mutableMapOf<String, AllocationResult>()

    val unallocated = toAllocate.keys.filter { toAllocate.getValue(it).type == ValueType.InSomeRegister }.toMutableSet()


    // Filter out unused
    for (name in unallocated) {
        val state = toAllocate.getValue(name)
        val externalUses = state.usedIn.filter { actions[it].type == CallType.External }
        if (externalUses.isEmpty()) {
            allocations[name] = AllocationResult(AllocationResultType.Unused, null)
        }
    }

    for (allocated in allocations.keys) {
        if (allocated in unallocated) {
            unallocated.remove(allocated)
        }
    }

    // Try to allocate parameters
    for (name in unallocated) {
        val state = toAllocate.getValue(name)

        val externalUses = state.usedIn.filter { actions[it].type == CallType.External }
        if (externalUses.size != 1) {
            continue
        }
        val usedAt = externalUses.first()
        if (actions[usedAt].type != CallType.External && actions[usedAt].type != CallType.Return) {
            continue
        }
        // Check all actions between
        var survives = true
        for (actionIndex in state.declared until usedAt) {
            if (actions[actionIndex].type == CallType.External) {
                survives = false
            }
        }
        if (!survives) {
            continue
        }
        // Can safely be stored in param register

        val register = state.registerWishList.first()

        allocations[name] = AllocationResult(AllocationResultType.InRegister, register)
    }

    for (allocated in allocations.keys) {
        if (allocated in unallocated) {
            unallocated.remove(allocated)
        }
    }

    // Try keep parameters in local registers as long as possible
    for (paramName in func.parameters.map { it.first }) {
        if (paramName !in unallocated) {
            continue
        }

        val state = toAllocate.getValue(paramName)
        require(state.declared == 0)


        val externalUses = state.usedIn.filter { actions[it].type == CallType.External }
        if (externalUses.size != 1) {
            continue
        }
        val usedAt = externalUses.first()


        var survives = true
        for (actionIndex in 0 until usedAt) {
            if (actions[actionIndex].type == CallType.External) {
                survives = false
            }
        }
        if (!survives) {
            continue
        }
        val register = state.registerWishList.first()

        allocations[paramName] = AllocationResult(AllocationResultType.InRegister, register)

    }

    for (allocated in allocations.keys) {
        if (allocated in unallocated) {
            unallocated.remove(allocated)
        }
    }

    // Alloc remaining in temporary registers
    // TODO: lifecycle analysis
    val usedTempRegisters = mutableListOf<Register>()
    require(unallocated.size <= temporaryRegisters.size)
    for ((name, register) in unallocated.zip(temporaryRegisters)) {
        usedTempRegisters.add(register)
        allocations[name] = AllocationResult(AllocationResultType.InRegister, register)
    }

    for (allocated in allocations.keys) {
        if (allocated in unallocated) {
            unallocated.remove(allocated)
        }
    }

    println("ALLOCATIONS:")
    for ((name, alloc) in allocations) {
        println("$name: $alloc")
    }

    println("REMAINING:")
    for (name in unallocated) {
        println(name)
    }

    return allocations
}

fun isExternal(functionDefinition: FunctionDefinition): Boolean {

    if (functionDefinition == BuiltInSignatures.print) {
        return true
    }

    return functionDefinition !in BuiltInSignatures.functions
}


