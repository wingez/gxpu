package se.wingez.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.*
import se.wingez.bytes
import se.wingez.compiler.actions.AdditionProvider
import se.wingez.emulator.DefaultEmulator
import se.wingez.instructions.Instruction

class ActionTest {

    class DummyGenerator(
        val result: MutableList<UByte> = mutableListOf()
    ) : CodeGenerator {
        override fun generate(code: List<UByte>) {
            result.addAll(code)
        }

        override fun generateAt(code: List<UByte>, at: Int) {
            throw NotImplementedError()
        }

        override fun makeSpaceFor(instruction: Instruction): GenerateLater {
            throw NotImplementedError()
        }

        override val currentSize: Int
            get() = throw NotImplementedError()

    }

    private val dummyFrame = FrameLayout(
        3u, "dummyFrame",
        mapOf("var1" to StructDataField("var1", 0u, byteType)),
        0u, 2u, 1u, 0u
    )

    @Test
    fun testPrintConstant() {
        val node = PrintNode(ConstantNode(5))
        val flattened = flatten(node, dummyFrame)

        assertEquals(
            flattened,
            CompositeAction(
                PutConstantInRegister.PutByteInRegisterAction(5u),
                PrintFromRegister.PrintAction()
            )
        )
        val generator = DummyGenerator()
        flattened.compile(generator)
        assertIterableEquals(
            generator.result,
            listOf(DefaultEmulator.lda.id, 5u, DefaultEmulator.print.id)
        )
    }

    @Test
    fun testPrintVariable() {
        val node = PrintNode(MemberAccess("var1"))
        val flattened = flatten(node, dummyFrame)

        assertEquals(
            flattened,
            CompositeAction(
                FieldByteToRegister.FieldByteToRegisterAction(dummyFrame, "var1"),
                PrintFromRegister.PrintAction()
            )
        )
    }


    @Test
    fun testAssignConstant() {
        val node = AssignNode(AssignTarget(MemberAccess("var2")), ConstantNode(5))
        assertThrows<CompileError> {
            flatten(node, dummyFrame)
        }
        val node2 = AssignNode(AssignTarget(MemberAccess("var1")), ConstantNode(4))
        val flattened = flatten(node2, dummyFrame)

        assertEquals(
            flattened,
            CompositeAction(
                PutConstantInRegister.PutByteInRegisterAction(4u),
                AssignFrameByte.AssignFrameRegister(dummyFrame, "var1")
            )
        )
        val generator = DummyGenerator()
        flattened.compile(generator)
        assertIterableEquals(
            generator.result,
            bytes(DefaultEmulator.lda.id.toInt(), 4, DefaultEmulator.sta_fp_offset.id.toInt(), 0)
        )
    }

    @Test
    fun testAddition() {
        val node = PrintNode(SingleOperationNode(Operation.Addition, ConstantNode(5), ConstantNode(10)))

        assertEquals(
            flatten(node, dummyFrame),
            CompositeAction(
                CompositeAction(
                    PutByteOnStack.PutByteOnStackAction(10u),
                    PutByteOnStack.PutByteOnStackAction(5u),
                    AdditionProvider.AdditionAction(),
                ),
                PrintFromRegister.PrintAction()
            )
        )
    }
}