package se.wingez.compiler.actions.optimizers

import se.wingez.compiler.actions.Action
import se.wingez.compiler.actions.PopRegister
import se.wingez.compiler.actions.PushRegister

fun applyAllOptimizations(actions: MutableList<Action>) {
    val optimizations = listOf<(MutableList<Action>) -> Boolean>(::removePushPop)

    while (true) {
        if (!optimizations.any { it.invoke(actions) }) {
            return
        }
    }
}

fun removePushPop(actions: MutableList<Action>): Boolean {

    for (index in 0 until actions.size - 1) {
        if (actions[index] == PushRegister() && actions[index + 1] == PopRegister()) {
            actions.removeAt(index + 1)
            actions.removeAt(index)
            return true
        }
    }
    return false
}

