package se.wingez.compiler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.*
import se.wingez.byte
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
        mapOf(
            "var1" to StructDataField("var1", 0u, byteType),
            "frame" to StructDataField("frame", 0u, stackFrameType),
            "result" to StructDataField("result", 2u, voidType),
        ),
        emptyList(),
        voidType
    )

    @Test
    fun testFlatten() {
        fun a(value: UByte): Action {
            return ConstantRegister(value)
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
        val builder = ActionBuilder(dummyFrame, dummyFunctions, dummyTypeContainer)
        val node = PrintNode(ConstantNode(5))
        val flattened = builder.buildStatement(node)

        assertEquals(
            flattened,
            CompositeAction(
                PushConstant(5u),
                PopRegister(),
                PrintAction()
            )
        )
        val generator = CodeGenerator()
        flattened.compile(generator)

        val expected = mutableListOf<UByte>()
        expected.addAll(DefaultEmulator.build("LDA  #5"))
        expected.addAll(DefaultEmulator.build("PUSHA"))
        expected.addAll(DefaultEmulator.build("POPA"))
        expected.addAll(DefaultEmulator.build("OUT"))


        assertEquals(
            expected,
            generator.resultingCode,
        )
    }

    @Test
    fun testPrintVariable() {
        val builder = ActionBuilder(dummyFrame, dummyFunctions, dummyTypeContainer)
        val node = PrintNode(Identifier("var1"))
        val flattened = flatten(builder.buildStatement(node))

        assertEquals(
            listOf(
                LoadRegisterFP(),
                AddRegister(0u),
                PushRegister(),
                LoadRegisterStackAddressDeref(0u),
                PopThrow(),
                PushRegister(),
                PopRegister(),
                PrintAction()
            ),
            flattened
        )
    }


    @Test
    fun testAssignConstant() {
        val builder = ActionBuilder(dummyFrame, dummyFunctions, dummyTypeContainer)
        val node = AssignNode(Identifier("var2"), ConstantNode(5))
        assertThrows<CompileError> {
            builder.buildStatement(node)
        }
        val node2 = AssignNode(Identifier("var1"), ConstantNode(4))
        val actions = builder.buildStatement(node2)

        assertEquals(
            listOf(
                LoadRegisterFP(),
                AddRegister(0u),
                PushRegister(),
                ConstantRegister(4u),
                PushRegister(),
                PopRegister(),
                StoreRegisterAtStackAddress(0u),
                PopThrow(),
            ),

            flatten(actions),
        )

        val expected = mutableListOf<UByte>()
        expected.addAll(DefaultEmulator.build("lda FP #0"))
        expected.addAll(DefaultEmulator.build("adda #0"))
        expected.addAll(DefaultEmulator.build("pusha"))
        expected.addAll(DefaultEmulator.build("lda #4"))
        expected.addAll(DefaultEmulator.build("pusha"))
        expected.addAll(DefaultEmulator.build("popa"))
        expected.addAll(DefaultEmulator.build("sta [[sp #0]]"))
        expected.addAll(DefaultEmulator.build("addsp #1"))

        val generator = CodeGenerator()
        actions.compile(generator)

        assertEquals(
            expected,
            generator.resultingCode,
        )
    }

    @Test
    fun testAddition() {
        val builder = ActionBuilder(dummyFrame, dummyFunctions, dummyTypeContainer)
        val node = PrintNode(OperationNode(NodeTypes.Addition, ConstantNode(5), ConstantNode(10)))

        assertEquals(
            listOf(
                ConstantRegister(10u),
                PushRegister(),
                ConstantRegister(5u),
                PushRegister(),
                PopRegister(),
                AdditionAction(),
                PopThrow(),
                PushRegister(),
                PopRegister(),
                PrintAction()
            ),
            flatten(builder.buildStatement(node)),
        )
    }

    @Test
    fun testNotEqual() {
        val builder = ActionBuilder(dummyFrame, dummyFunctions, dummyTypeContainer)
        val node = OperationNode(NodeTypes.NotEquals, ConstantNode(5), ConstantNode(10))

        assertEquals(
            listOf(
                ConstantRegister(10u),
                PushRegister(),
                ConstantRegister(5u),
                PushRegister(),
                PopRegister(),
                SubtractionAction(),
                PopThrow(),
                PushRegister(),
                PopRegister(),
                NotEqualCompare()
            ),
            flatten(
                builder.buildStatement(node)
            )
        )
    }

    @Test
    fun testCall() {
        val emptyFunction = FunctionInfo(
            0u,
            "test",
            mapOf("frame" to StructDataField("struct", 0u, stackFrameType)),
            emptyList(),
            voidType
        )
        val builder = ActionBuilder(dummyFrame, FunctionContainer(listOf(emptyFunction)), dummyTypeContainer)


        //No params no return
        assertIterableEquals(
            listOf(
                AllocSpaceOnStack(voidType.size),
                AllocSpaceOnStack(0u),
                CallAction(emptyFunction),
                RemoveSpaceOnStack(0u)
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
            StructBuilder(dummyTypeContainer).addMember("frame", stackFrameType).addMember("param", byteType)
                .getFields(),
            listOf("param"),
            voidType,
        )
        val builder = ActionBuilder(dummyFrame, FunctionContainer(listOf(functionWithParameter)), dummyTypeContainer)


        //1 parameter no return
        assertIterableEquals(
            listOf(
                AllocSpaceOnStack(voidType.size),
                AllocSpaceOnStack(0u),
                ConstantRegister(5u),
                PushRegister(),
                CallAction(functionWithParameter),
                RemoveSpaceOnStack(functionWithParameter.sizeOfParameters),
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

        val functionWithReturn = FunctionInfo(
            0u,
            "test",
            StructBuilder(dummyTypeContainer).addMember("frame", stackFrameType).addMember("result", byteType)
                .getFields(),
            emptyList(),
            byteType
        )
        val builder = ActionBuilder(dummyFrame, FunctionContainer(listOf(functionWithReturn)), dummyTypeContainer)

        //No params, return byte
        assertIterableEquals(
            listOf(
                AllocSpaceOnStack(byteType.size),
                AllocSpaceOnStack(0u),
                CallAction(functionWithReturn),
                RemoveSpaceOnStack(byte(functionWithReturn.sizeOfVars + functionWithReturn.sizeOfParameters))
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

        val myType = StructBuilder(dummyTypeContainer)
            .addMember("member1", byteType)
            .addMember("member2", byteType)
            .getStruct("myType")
        val function = FunctionInfo(
            0u, "test",
            mapOf("t" to StructDataField("t", 0u, myType)), emptyList(), voidType
        )

        val builder = ActionBuilder(function, dummyFunctions, dummyTypeContainer)

        assertEquals(
            listOf(
                LoadRegisterFP(1),
                AddRegister(0u),
                AddRegister(0u),
                PushRegister(),
                ConstantRegister(5u),
                PushRegister(),
                PopRegister(),
                StoreRegisterAtStackAddress(0u),
                PopThrow()
            ),
            flatten(
                builder.buildStatement(AssignNode(MemberAccess(Identifier("t"), "member1"), ConstantNode(5)))
            )
        )
        assertIterableEquals(
            listOf(
                LoadRegisterFP(1),
                AddRegister(0u),
                AddRegister(1u),
                PushRegister(),
                ConstantRegister(4u),
                PushRegister(),
                PopRegister(),
                StoreRegisterAtStackAddress(0u),
                PopThrow()

            ), flatten(
                builder.buildStatement(AssignNode(MemberAccess(Identifier("t"), "member2"), ConstantNode(4)))
            )
        )
    }

    @Test
    fun testDerefAddressRead() {
        val myType = StructBuilder(dummyTypeContainer).addMember("value", byteType).getStruct("myType")

        val function = FunctionInfo(
            0u, "test",
            StructBuilder(TypeContainer(listOf(myType)))
                .addMember("frame", stackFrameType)
                .addMember("field", Pointer(myType)).getFields(),
            emptyList(),
            voidType
        )

        val builder = ActionBuilder(function, dummyFunctions, dummyTypeContainer)
        assertEquals(
            listOf(
                LoadRegisterFP(),
                AddRegister(2u),
                DerefByteAction(0u),
                AddRegister(0u),
                PushRegister(),
                LoadRegisterStackAddressDeref(0u),
                PopThrow(),
                PushRegister(),
                PopRegister(),
                PrintAction()
            ),
            flatten(
                builder.buildStatement(PrintNode(MemberDeref(Identifier("field"), "value")))
            )
        )
    }

    @Test
    fun testDerefAssign() {


        val myType = StructBuilder(dummyTypeContainer).addMember("value", byteType).getStruct("myType")

        val function = FunctionInfo(
            0u, "test",
            StructBuilder(TypeContainer(listOf(myType)))
                .addMember("frame", stackFrameType)
                .addMember("field", Pointer(myType)).getFields(),
            emptyList(),
            voidType
        )


        val builder = ActionBuilder(function, dummyFunctions, dummyTypeContainer)

        assertEquals(
            listOf(
                LoadRegisterFP(),
                AddRegister(2u),
                DerefByteAction(0u),
                AddRegister(0u),
                PushRegister(),
                ConstantRegister(5u),
                PushRegister(),
                PopRegister(),
                StoreRegisterAtStackAddress(0u),
                PopThrow()
            ),
            flatten(
                builder.buildStatement(
                    AssignNode(
                        MemberDeref(Identifier("field"), "value"),
                        ConstantNode(5)
                    )
                )
            )
        )
    }

    @Test
    fun testCreateArray() {
        val function = FunctionInfo(
            0u, "test",
            StructBuilder(dummyTypeContainer).addMember("arr", Pointer(ArrayType(byteType)))
                .addMember("frame", stackFrameType).getFields(),
            emptyList(),
            voidType
        )

        val builder2 = ActionBuilder(function, dummyFunctions, dummyTypeContainer)
        assertEquals(
            listOf(
                ConstantRegister(5u),
                PushRegister(),
                PopRegister(),
                AllocDynamicSpaceOnStack(),
                PushRegister(),
                PushStackPointer(),
                LoadRegisterFP(),
                AddRegister(0u),
                PushRegister(),
                LoadRegisterStackAddress(1u),
                StoreRegisterAtStackAddress(0u),
                RemoveSpaceOnStack(2u)
            ),
            flatten(
                builder2.buildStatement(
                    AssignNode(
                        Identifier("arr"),
                        CallNode("createArray", listOf(ConstantNode(5))),
                    )
                )
            )
        )


    }

    @Test
    fun testAccessArray() {
        val function = FunctionInfo(
            0u, "test",
            mapOf(
                "arr" to StructDataField("arr", 0u, Pointer(ArrayType(byteType))),
                "frame" to StructDataField("frame", 1u, stackFrameType)
            ),
            emptyList(),
            voidType
        )

        val builder2 = ActionBuilder(function, dummyFunctions, dummyTypeContainer)
        assertEquals(
            listOf(
                LoadRegisterFP(),
                AddRegister(0u),
                DerefByteAction(0u),
                PushRegister(),
                ConstantRegister(5u),
                PushRegister(),
                PopRegister(),
                AddRegister(1u),
                AdditionAction(),
                PopThrow(),
                PushRegister(),
                LoadRegisterStackAddressDeref(0u),
                PopThrow(),
                PushRegister()
            ),
            flatten(
                kotlin.test.assertNotNull(
                    builder2.getActionOnStack(ArrayAccess(Identifier("arr"), ConstantNode(5)), byteType)
                )
            )
        )
    }

    @Test
    fun pushPointer() {
        val function = FunctionInfo(
            0u, "test",
            StructBuilder(dummyTypeContainer)
                .addMember("frame", stackFrameType)
                .addMember("arr", Pointer(ArrayType(byteType))).getFields(),
            emptyList(),
            voidType
        )
        val builder = ActionBuilder(function, dummyFunctions, dummyTypeContainer)

        assertEquals(
            listOf(
                LoadRegisterFP(),
                AddRegister(2u),
                PushRegister(),
                LoadRegisterStackAddressDeref(0u),
                PopThrow(),
                PushRegister()
            ),
            flatten(
                kotlin.test.assertNotNull(builder.getActionOnStack(Identifier("arr"), Pointer(ArrayType(byteType))))
            )
        )
    }
}