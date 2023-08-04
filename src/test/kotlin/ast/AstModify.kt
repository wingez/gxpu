package ast

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalStateException
import kotlin.test.assertEquals

private fun leaf(int: Int): AstNode {
    return AstNode(NodeTypes.Return, int, emptyList(), SourceInfo.notApplicable)
}

private fun node(int: Int, nodes: List<AstNode>): AstNode {
    return AstNode(NodeTypes.Break, int, nodes, SourceInfo.notApplicable)
}


class AstModify {

    @Test
    fun testModifyDoNothing() {

        val tree = leaf(1)

        assertEquals(tree, iterateAndModify(tree) { keep() })
    }

    @Test
    fun testModifyReplace() {
        val tree = leaf(2)

        assertEquals(leaf(1), iterateAndModify(tree) { replaceWith(leaf(1)) })
    }

    @Test
    fun testModifyNested() {

        val tree = node(
            2, listOf(
                leaf(3),
                leaf(4),
            )
        )

        val result = iterateAndModify(tree) {

            if (it.data == 3) {
                replaceWith(leaf(5))
            } else {
                keep()
            }
        }

        assertEquals(node(2, listOf(leaf(5), leaf(4))), result)

    }

    @Test
    fun testDelete(){

        val tree = node(
            2, listOf(
                leaf(3),
                leaf(4),
            )
        )

        val result = iterateAndModify(tree) {
            if (it.data == 3) {
                delete()
            } else {
                keep()
            }
        }

        assertEquals(node(2, listOf(leaf(4))), result)

        // Should not be possible do remove the root node
        assertThrows<IllegalStateException> { iterateAndModify(tree){ delete()}  }
    }

    @Test
    fun replaceMultiple(){
        val tree = node(
            2, listOf(
                leaf(3),
                leaf(4),
            )
        )


        val result = iterateAndModify(tree) {
            replaceWith(it.copy(data = it.data as Int +1))
        }

        val expected =node(
            3, listOf(
                leaf(4),
                leaf(5),
            )
        )
        assertEquals(expected, result)
    }
}