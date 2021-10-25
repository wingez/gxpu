package se.wingez.compiler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.*
import se.wingez.bytes
import se.wingez.compiler.actions.*
import se.wingez.emulator.DefaultEmulator

class FunctionContainer(
    val functions: List<AssemblyFunction>
) : FunctionProvider {
    override fun getFunction(name: String): AssemblyFunction {
        return functions.find { it.name == name } ?: throw AssertionError()
    }
}

val dummyFunctions = FunctionContainer(emptyList())

class ActionTest {


    private val dummyFrame = FrameLayout(
        3u, "dummyFrame",
        mapOf("var1" to StructDataField("var1", 0u, byteType)),
        emptyList(),
        voidType, 2u, 1u, 0u
    )

    @Test
    fun testFlatten() {
        fun a(value: UByte): Action {
            return PutConstantInRegister.PutByteInRegisterAction(value)
        }

        assertIterableEquals(
            listOf(
                a(1u),
                a(2u),
                a(3u),
            ),
            flatten(
                CompositeAction(
                    CompositeAction(a(1u), a(2u)),
                    a(3u)
                )
            )

        )


    }


    @Test
    fun testPrintConstant() {
        val node = PrintNode(ConstantNode(5))
        val flattened = buildStatement(node, dummyFrame, dummyFunctions)

        assertEquals(
            flattened,
            CompositeAction(
                PutConstantInRegister.PutByteInRegisterAction(5u),
                PrintFromRegister.PrintAction()
            )
        )
        val generator = CodeGenerator()
        flattened.compile(generator)
        assertIterableEquals(
            generator.resultingCode,
            listOf(DefaultEmulator.lda.id, 5u, DefaultEmulator.print.id)
        )
    }

    @Test
    fun testPrintVariable() {
        val node = PrintNode(MemberAccess("var1"))
        val flattened = buildStatement(node, dummyFrame, dummyFunctions)

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
        val node = AssignNode(MemberAccess("var2"), ConstantNode(5))
        assertThrows<CompileError> {
            buildStatement(node, dummyFrame, dummyFunctions)
        }
        val node2 = AssignNode(MemberAccess("var1"), ConstantNode(4))
        val flattened = buildStatement(node2, dummyFrame, dummyFunctions)

        assertEquals(
            flattened,
            CompositeAction(
                PutConstantInRegister.PutByteInRegisterAction(4u),
                AssignFrameByte.AssignFrameRegister(dummyFrame, "var1")
            )
        )
        val generator = CodeGenerator()
        flattened.compile(generator)
        assertIterableEquals(
            generator.resultingCode,
            bytes(DefaultEmulator.lda.id.toInt(), 4, DefaultEmulator.sta_fp_offset.id.toInt(), 0)
        )
    }

    @Test
    fun testAddition() {
        val node = PrintNode(SingleOperationNode(Operation.Addition, ConstantNode(5), ConstantNode(10)))

        assertEquals(
            buildStatement(node, dummyFrame, dummyFunctions),
            CompositeAction(
                CompositeAction(
                    PutByteOnStack.PutByteOnStackAction(10u),
                    PutConstantInRegister.PutByteInRegisterAction(5u),
                    AdditionProvider.AdditionAction(),
                ),
                PrintFromRegister.PrintAction()
            )
        )
    }

    @Test
    fun testNotEqual() {
        val node = SingleOperationNode(Operation.NotEquals, ConstantNode(5), ConstantNode(10))

        assertIterableEquals(
            listOf(
                PutByteOnStack.PutByteOnStackAction(10u),
                PutConstantInRegister.PutByteInRegisterAction(5u),
                SubtractionProvider.SubtractionAction(),
                NotEqualProvider.NotEqualCompare()
            ),
            flatten(
                getActionInRegister(node, compareType, dummyFrame, dummyFunctions)
                    ?: throw AssertionError("No action found")
            )
        )
    }

    @Test
    fun testConditionMustBeComparison() {
        val node = ConstantNode(5)
        assertNull(getActionInRegister(node, compareType, dummyFrame, dummyFunctions))
        val node2 = SingleOperationNode(Operation.Addition, ConstantNode(5), ConstantNode(10))
        assertNull(getActionInRegister(node2, compareType, dummyFrame, dummyFunctions))
    }

    @Test
    fun testCall() {
        val generator = CodeGenerator()

        val emptyFunction = AssemblyFunction(
            generator,
            FrameLayout(2u, "test", emptyMap(), emptyList(), voidType, 0u, 2u, 0u),
            0u,
            dummyFunctions,
        )


        //No params no return
        assertIterableEquals(
            listOf(
                CallProvider.PlaceReturnValueOnStack(voidType),
                CallProvider.CallAction(emptyFunction),
                CallProvider.PopArguments(emptyFunction),
            ), flatten(
                getActionOnStack(
                    CallNode("test", emptyList()),
                    voidType, emptyFunction.frameLayout,
                    FunctionContainer(listOf(emptyFunction))
                ) ?: throw AssertionError("Not found")
            )
        )

        val functionWithParameter = AssemblyFunction(
            generator,
            FrameLayout(3u, "test", emptyMap(), listOf(StructDataField("param", 0u, byteType)), voidType, 0u, 2u, 0u),
            0u,
            dummyFunctions,
        )

        //No params no return
        assertIterableEquals(
            listOf(
                CallProvider.PlaceReturnValueOnStack(voidType),
                PutByteOnStack.PutByteOnStackAction(5u),
                CallProvider.CallAction(functionWithParameter),
                CallProvider.PopArguments(functionWithParameter),
            ), flatten(
                getActionOnStack(
                    CallNode("test", listOf(ConstantNode(5))),
                    voidType, functionWithParameter.frameLayout,
                    FunctionContainer(listOf(functionWithParameter))
                ) ?: throw AssertionError("Not found")
            )
        )


        val functionWithReturn = AssemblyFunction(
            generator,
            FrameLayout(3u, "test", emptyMap(), emptyList(), byteType, 0u, 2u, 0u),
            0u,
            dummyFunctions,
        )

        //No params no return
        assertIterableEquals(
            listOf(
                CallProvider.PlaceReturnValueOnStack(byteType),
                CallProvider.CallAction(functionWithReturn),
                CallProvider.PopArguments(functionWithReturn),
            ), flatten(
                getActionOnStack(
                    CallNode("test", emptyList()),
                    byteType, functionWithReturn.frameLayout,
                    FunctionContainer(listOf(functionWithReturn))
                ) ?: throw AssertionError("Not found")
            )
        )
        //Test wrong return type
        assertNull(
            getActionOnStack(
                CallNode("test", emptyList()),
                voidType, functionWithReturn.frameLayout,
                FunctionContainer(listOf(functionWithReturn))
            )
        )
    }
}