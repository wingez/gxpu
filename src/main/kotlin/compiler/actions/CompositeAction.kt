package se.wingez.compiler.actions

import se.wingez.compiler.CodeGenerator

class CompositeAction(
    vararg actions: Action
) : Action, Iterable<Action> {
    private val actions = actions.asList()
    override val cost: Int
        get() = actions.sumOf { it.cost }

    override fun compile(generator: CodeGenerator) {
        for (action in actions) {
            action.compile(generator)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CompositeAction)
            return false
        return this.actions == other.actions
    }

    override fun hashCode(): Int {
        return actions.hashCode()
    }

    override fun iterator(): Iterator<Action> {
        return actions.iterator()
    }

    override fun toString(): String {

        return "CompositeAction $actions"
    }

}

fun flatten(topAction: Action): List<Action> {
    val result = mutableListOf<Action>()

    fun visitRecursive(action: Action) {
        if (action is CompositeAction) {
            for (child in action) {
                visitRecursive(child)
            }
        } else {
            result.add(action)
        }
    }
    visitRecursive(topAction)
    return result
}