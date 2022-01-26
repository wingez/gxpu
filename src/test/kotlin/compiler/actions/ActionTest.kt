package se.wingez.compiler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.wingez.ast.*
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
        0, "dummyFrame",
        mapOf(
            "var1" to StructDataField("var1", 0, byteType),
            "frame" to StructDataField("frame", 0, stackFrameType),
            "result" to StructDataField("result", 2, voidType),
        ),
        emptyList(),
        voidType
    )

    @Test
    fun testFlatten() {
        fun a(value: Int): Action {
            return ConstantRegister(value)
        }

        assertIterableEquals(
            listOf(
                a(1),
                a(2),
                a(3),
            ),
            flatten(
                CompositeAction(
                    CompositeAction(a(1), a(2)),
                    a(3)
                )
            )
        )
    }


    @Test
    fun testPrintConstant() {
        val builder = ActionBuilder(dummyFrame, dummyFunctions, dummyTypeContainer)
        val node = AstNode.fromPrint(constant(5))
        val flattened = builder.buildStatement(node)

        assertEquals(
            flattened,
            CompositeAction(
                PushConstant(5),
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
        val node = AstNode.fromPrint(identifier("var1"))
        val flattened = flatten(builder.buildStatement(node))

        assertEquals(
            listOf(
                LoadRegisterFP(),
                AddRegister(0),
                PushRegister(),
                LoadRegisterStackAddressDeref(0),
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
        val node = AstNode.fromAssign(identifier("var2"), constant(5))
        assertThrows<CompileError> {
            builder.buildStatement(node)
        }
        val node2 = AstNode.fromAssign(identifier("var1"), constant(4))
        val actions = builder.buildStatement(node2)

        assertEquals(
            listOf(
                LoadRegisterFP(),
                AddRegister(0),
                PushRegister(),
                ConstantRegister(4),
                PushRegister(),
                PopRegister(),
                StoreRegisterAtStackAddress(0),
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
        val node = AstNode.fromPrint(AstNode.fromOperation(NodeTypes.Addition, constant(5), constant(10)))

        assertEquals(
            listOf(
                ConstantRegister(10),
                PushRegister(),
                ConstantRegister(5),
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
        val node = AstNode.fromOperation(NodeTypes.NotEquals, constant(5), constant(10))

        assertEquals(
            listOf(
                ConstantRegister(10),
                PushRegister(),
                ConstantRegister(5),
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
            0,
            "test",
            mapOf("frame" to StructDataField("struct", 0, stackFrameType)),
            emptyList(),
            voidType
        )
        val builder = ActionBuilder(dummyFrame, FunctionContainer(listOf(emptyFunction)), dummyTypeContainer)


        //No params no return
        assertIterableEquals(
            listOf(
                AllocSpaceOnStack(voidType.size),
                AllocSpaceOnStack(0),
                CallAction(emptyFunction),
                RemoveSpaceOnStack(0)
            ), flatten(
                builder.getActionOnStack(
                    call("test", emptyList()),
                    voidType
                ) ?: throw AssertionError("Not found")
            )
        )
    }

    @Test
    fun testCallParameter() {
        val functionWithParameter = FunctionInfo(
            0,
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
                AllocSpaceOnStack(0),
                ConstantRegister(5),
                PushRegister(),
                CallAction(functionWithParameter),
                RemoveSpaceOnStack(functionWithParameter.sizeOfParameters),
            ), flatten(
                builder.getActionOnStack(
                    call("test", listOf(constant(5))),
                    voidType
                ) ?: throw AssertionError("Not found")
            )
        )
    }

    @Test
    fun testCallReturnType() {

        val functionWithReturn = FunctionInfo(
            0,
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
                AllocSpaceOnStack(0),
                CallAction(functionWithReturn),
                RemoveSpaceOnStack(functionWithReturn.sizeOfVars + functionWithReturn.sizeOfParameters)
            ), flatten(
                builder.getActionOnStack(
                    call("test", emptyList()),
                    byteType
                ) ?: throw AssertionError("Not found")
            )
        )
        //Test wrong return type
        assertNull(
            builder.getActionOnStack(
                call("test", emptyList()),
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
            0, "test",
            mapOf("t" to StructDataField("t", 0, myType)), emptyList(), voidType
        )

        val builder = ActionBuilder(function, dummyFunctions, dummyTypeContainer)

        assertEquals(
            listOf(
                LoadRegisterFP(1),
                AddRegister(0),
                AddRegister(0),
                PushRegister(),
                ConstantRegister(5),
                PushRegister(),
                PopRegister(),
                StoreRegisterAtStackAddress(0),
                PopThrow()
            ),
            flatten(
                builder.buildStatement(AstNode.fromAssign(memberAccess(identifier("t"), "member1"), constant(5)))
            )
        )
        assertIterableEquals(
            listOf(
                LoadRegisterFP(1),
                AddRegister(0),
                AddRegister(1),
                PushRegister(),
                ConstantRegister(4),
                PushRegister(),
                PopRegister(),
                StoreRegisterAtStackAddress(0),
                PopThrow()

            ), flatten(
                builder.buildStatement(AstNode.fromAssign(memberAccess(identifier("t"), "member2"), constant(4)))
            )
        )
    }

    @Test
    fun testDerefAddressRead() {
        val myType = StructBuilder(dummyTypeContainer).addMember("value", byteType).getStruct("myType")

        val function = FunctionInfo(
            0, "test",
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
                AddRegister(2),
                DerefByteAction(0),
                AddRegister(0),
                PushRegister(),
                LoadRegisterStackAddressDeref(0),
                PopThrow(),
                PushRegister(),
                PopRegister(),
                PrintAction()
            ),
            flatten(
                builder.buildStatement(AstNode.fromPrint(memberDeref(identifier("field"), "value")))
            )
        )
    }

    @Test
    fun testDerefAssign() {


        val myType = StructBuilder(dummyTypeContainer).addMember("value", byteType).getStruct("myType")

        val function = FunctionInfo(
            0, "test",
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
                AddRegister(2),
                DerefByteAction(0),
                AddRegister(0),
                PushRegister(),
                ConstantRegister(5),
                PushRegister(),
                PopRegister(),
                StoreRegisterAtStackAddress(0),
                PopThrow()
            ),
            flatten(
                builder.buildStatement(
                    AstNode.fromAssign(
                        memberDeref(identifier("field"), "value"),
                        constant(5)
                    )
                )
            )
        )
    }

    @Test
    fun testCreateArray() {
        val function = FunctionInfo(
            0, "test",
            StructBuilder(dummyTypeContainer).addMember("arr", Pointer(ArrayType(byteType)))
                .addMember("frame", stackFrameType).getFields(),
            emptyList(),
            voidType
        )

        val builder2 = ActionBuilder(function, dummyFunctions, dummyTypeContainer)
        assertEquals(
            listOf(
                ConstantRegister(5),
                PushRegister(),
                PopRegister(),
                AllocDynamicSpaceOnStack(),
                PushRegister(),
                PushStackPointer(),
                LoadRegisterFP(),
                AddRegister(0),
                PushRegister(),
                LoadRegisterStackAddress(1),
                StoreRegisterAtStackAddress(0),
                RemoveSpaceOnStack(2)
            ),
            flatten(
                builder2.buildStatement(
                    AstNode.fromAssign(
                        identifier("arr"),
                        call("createArray", listOf(constant(5))),
                    )
                )
            )
        )


    }

    @Test
    fun testAccessArray() {
        val function = FunctionInfo(
            0, "test",
            mapOf(
                "arr" to StructDataField("arr", 0, Pointer(ArrayType(byteType))),
                "frame" to StructDataField("frame", 1, stackFrameType)
            ),
            emptyList(),
            voidType
        )

        val builder2 = ActionBuilder(function, dummyFunctions, dummyTypeContainer)
        assertEquals(
            listOf(
                LoadRegisterFP(),
                AddRegister(0),
                DerefByteAction(0),
                PushRegister(),
                ConstantRegister(5),
                PushRegister(),
                PopRegister(),
                AddRegister(1),
                AdditionAction(),
                PopThrow(),
                PushRegister(),
                LoadRegisterStackAddressDeref(0),
                PopThrow(),
                PushRegister()
            ),
            flatten(
                kotlin.test.assertNotNull(
                    builder2.getActionOnStack(AstNode.fromArrayAccess(identifier("arr"), constant(5)), byteType)
                )
            )
        )
    }

    @Test
    fun pushPointer() {
        val function = FunctionInfo(
            0, "test",
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
                AddRegister(2),
                PushRegister(),
                LoadRegisterStackAddressDeref(0),
                PopThrow(),
                PushRegister()
            ),
            flatten(
                kotlin.test.assertNotNull(builder.getActionOnStack(identifier("arr"), Pointer(ArrayType(byteType))))
            )
        )
    }
}