package se.wingez.compiler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.*
import se.wingez.bytes
import se.wingez.compiler.actions.*
import se.wingez.emulator.DefaultEmulator

class FunctionContainer(
    private val functions: List<FunctionInfo>
) : FunctionProvider {
    override fun getFunction(name: String): FunctionInfo {
        return functions.find { it.name == name } ?: throw AssertionError()
    }
}

val dummyFunctions = FunctionContainer(emptyList())

class ActionTest {


    private val dummyFrame = FunctionInfo(
        0u, "dummyFrame",
        mapOf("var1" to StructDataField("var1", 0u, byteType)),
        emptyList(),
        voidType, 2u, 1u, 0u
    )

    @Test
    fun testFlatten() {
        fun a(value: UByte): Action {
            return LoadRegister(value)
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
        val builder = ActionBuilder(dummyFrame, dummyFunctions)
        val node = PrintNode(ConstantNode(5))
        val flattened = builder.buildStatement(node)

        assertEquals(
            flattened,
            CompositeAction(
                LoadRegister(5u),
                Print.PrintAction()
            )
        )
        val generator = CodeGenerator()
        flattened.compile(generator)
        assertIterableEquals(
            generator.resultingCode,
            listOf(DefaultEmulator.lda_constant.id, 5u, DefaultEmulator.print.id)
        )
    }

    @Test
    fun testPrintVariable() {
        val builder = ActionBuilder(dummyFrame, dummyFunctions)
        val node = PrintNode(Identifier("var1"))
        val flattened = builder.buildStatement(node)

        assertEquals(
            flattened,
            CompositeAction(
                FieldByteToRegister.FieldByteToRegisterAction(dummyFrame.getField("var1")),
                Print.PrintAction()
            )
        )
    }


    @Test
    fun testAssignConstant() {
        val builder = ActionBuilder(dummyFrame, dummyFunctions)
        val node = AssignNode(Identifier("var2"), ConstantNode(5))
        assertThrows<CompileError> {
            builder.buildStatement(node)
        }
        val node2 = AssignNode(Identifier("var1"), ConstantNode(4))
        val flattened = builder.buildStatement(node2)

        assertEquals(
            flattened,
            CompositeAction(
                LoadRegister(4u),
                AssignFrameByte.AssignFrameRegister(dummyFrame.getField("var1"))
            )
        )
        val generator = CodeGenerator()
        flattened.compile(generator)
        assertIterableEquals(
            generator.resultingCode,
            bytes(DefaultEmulator.lda_constant.id.toInt(), 4, DefaultEmulator.sta_fp_offset.id.toInt(), 0)
        )
    }

    @Test
    fun testAddition() {
        val builder = ActionBuilder(dummyFrame, dummyFunctions)
        val node = PrintNode(SingleOperationNode(Operation.Addition, ConstantNode(5), ConstantNode(10)))

        assertEquals(
            builder.buildStatement(node),
            CompositeAction(
                CompositeAction(
                    PushByte(10u),
                    LoadRegister(5u),
                    AdditionProvider.AdditionAction(),
                ),
                Print.PrintAction()
            )
        )
    }

    @Test
    fun testNotEqual() {
        val builder = ActionBuilder(dummyFrame, dummyFunctions)
        val node = SingleOperationNode(Operation.NotEquals, ConstantNode(5), ConstantNode(10))

        assertIterableEquals(
            listOf(
                PushByte(10u),
                LoadRegister(5u),
                SubtractionProvider.SubtractionAction(),
                NotEqualProvider.NotEqualCompare()
            ),
            flatten(
                builder.getActionInRegister(node, compareType)
                    ?: throw AssertionError("No action found")
            )
        )
    }

    @Test
    fun testConditionMustBeComparison() {
        val builder = ActionBuilder(dummyFrame, dummyFunctions)
        val node = ConstantNode(5)
        assertNull(builder.getActionInRegister(node, compareType))
        val node2 = SingleOperationNode(Operation.Addition, ConstantNode(5), ConstantNode(10))
        assertNull(builder.getActionInRegister(node2, compareType))
    }

    @Test
    fun testCall() {
        val emptyFunction = FunctionInfo(0u, "test", emptyMap(), emptyList(), voidType, 0u, 2u, 0u)
        val builder = ActionBuilder(dummyFrame, FunctionContainer(listOf(emptyFunction)))


        //No params no return
        assertIterableEquals(
            listOf(
                CallProvider.PlaceReturnValueOnStack(voidType),
                CallProvider.CallAction(emptyFunction),
                CallProvider.PopArguments(emptyFunction),
            ), flatten(
                builder.getActionOnStack(
                    CallNode("test", emptyList()),
                    voidType
                ) ?: throw AssertionError("Not found")
            )
        )
    }

    @Test
    fun testCallParameter() {
        val functionWithParameter = FunctionInfo(
            0u,
            "test",
            emptyMap(),
            listOf(StructDataField("param", 0u, byteType)),
            voidType,
            0u,
            2u,
            0u
        )
        val builder = ActionBuilder(dummyFrame, FunctionContainer(listOf(functionWithParameter)))


        //1 parameter no return
        assertIterableEquals(
            listOf(
                CallProvider.PlaceReturnValueOnStack(voidType),
                PushByte(5u),
                CallProvider.CallAction(functionWithParameter),
                CallProvider.PopArguments(functionWithParameter),
            ), flatten(
                builder.getActionOnStack(
                    CallNode("test", listOf(ConstantNode(5))),
                    voidType
                ) ?: throw AssertionError("Not found")
            )
        )
    }

    @Test
    fun testCallReturnType() {

        val functionWithReturn = FunctionInfo(0u, "test", emptyMap(), emptyList(), byteType, 0u, 2u, 0u)
        val builder = ActionBuilder(dummyFrame, FunctionContainer(listOf(functionWithReturn)))

        //No params, return byte
        assertIterableEquals(
            listOf(
                CallProvider.PlaceReturnValueOnStack(byteType),
                CallProvider.CallAction(functionWithReturn),
                CallProvider.PopArguments(functionWithReturn),
            ), flatten(
                builder.getActionOnStack(
                    CallNode("test", emptyList()),
                    byteType
                ) ?: throw AssertionError("Not found")
            )
        )
        //Test wrong return type
        assertNull(
            builder.getActionOnStack(
                CallNode("test", emptyList()),
                voidType
            )
        )
    }

    @Test
    fun testAssignStruct() {

        val myType = StructType(
            "myType", mapOf(
                "member1" to StructDataField("member1", 0u, byteType),
                "member2" to StructDataField("member2", 1u, byteType),
            )
        )
        val function = FunctionInfo(
            0u, "test",
            mapOf("t" to StructDataField("t", 0u, myType)), emptyList(), voidType, 0u, 2u, 2u
        )

        val builder = ActionBuilder(function, dummyFunctions)

        assertIterableEquals(
            listOf(
                LoadRegister(5u),
                AssignFrameByte.AssignFrameRegister(StructDataField("t.member1", 0u, byteType))
            ),
            flatten(
                builder.buildStatement(AssignNode(MemberAccess(Identifier("t"), "member1"), ConstantNode(5)))
            )
        )
        assertIterableEquals(
            listOf(
                LoadRegister(4u),
                AssignFrameByte.AssignFrameRegister(StructDataField("t.member2", 1u, byteType)),

                ), flatten(
                builder.buildStatement(AssignNode(MemberAccess(Identifier("t"), "member2"), ConstantNode(4)))
            )
        )
    }

    @Test
    fun testDerefAddress() {
        val myType = StructBuilder(dummyTypeContainer).addMember("value", byteType).getStruct("myType")

        val function = FunctionInfo(
            0u, "test",
            StructBuilder(TypeContainer(listOf(myType))).addMember("field", Pointer(myType)).getFields(),
            emptyList(),
            voidType, 0u, 0u, 0u
        )

        val builder = ActionBuilder(function, dummyFunctions)
        assertIterableEquals(
            listOf(
                FieldByteToRegister.FieldByteToRegisterAction(StructDataField("field", 0u, Pointer(myType))),
                DerefByte.DerefByteAction(StructDataField("value", 0u, byteType)),
                Print.PrintAction()
            ),
            flatten(
                builder.buildStatement(PrintNode(MemberDeref(Identifier("field"), "value")))
            )
        )


    }
}