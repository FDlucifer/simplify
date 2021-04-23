package org.cf.smalivm


import com.google.common.primitives.Ints
import org.cf.smalivm.dex.SmaliParser
import org.cf.smalivm.opcode.ArrayLengthOpTest
import org.cf.smalivm.type.ClassManager
import org.cf.smalivm.type.ClassManagerFactory
import org.cf.smalivm.type.VirtualMethod
import org.cf.smalivm2.ExecutionGraph2
import org.cf.smalivm2.ExecutionState
import org.cf.smalivm2.Value
import org.cf.smalivm2.VirtualMachine2
import org.cf.util.ClassNameUtils
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.writer.builder.DexBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import java.io.IOException
import java.util.*


object Tester {
    const val TEST_CLASS_PATH = "src/test/resources/smali"

    @JvmStatic
    val dexBuilder = DexBuilder(Opcodes.forApi(SmaliParser.DEX_API_LEVEL))

    @JvmStatic
    var classManager: ClassManager = ClassManagerFactory().build(TEST_CLASS_PATH, dexBuilder)

    @JvmStatic
    fun execute(className: String, methodDescriptor: String, vm: VirtualMachine2 = spawnVM()): ExecutionGraph2 {
        return vm.execute(className, methodDescriptor)
    }

    @JvmStatic
    fun execute(className: String, methodDescriptor: String, initial: TestState, vm: VirtualMachine2 = spawnVM()): ExecutionGraph2 {
        val method = classManager.getMethod(className, methodDescriptor)
        val state = buildInitialExecutionState(vm, method, initial)
        return vm.execute(method, state)
    }

    @JvmStatic
    fun test(className: String, methodSignature: String, expectedState: TestState) {
        test(className, methodSignature, TestState(), expectedState)
    }

    @JvmStatic
    fun test(className: String, methodSignature: String, initial: TestState, expected: TestState) {
        val graph = execute(className, methodSignature, initial)
        testState(graph, expected)
    }

    @JvmStatic
    fun testState(graph: ExecutionGraph2, expected: TestState) {
        Assertions.assertNotNull(graph, "Graph is null; method execution failed?")

        for ((register, value) in expected.registers) {
            val actual = graph.getTerminatingRegisterConsensus(register)
            testValueEquals(value, actual!!)
        }

        for ((className, fieldDescriptorToItem) in expected.fields) {
            val virtualClass = graph.classManager.getVirtualClass(className)
            for ((fieldDescriptor, value) in fieldDescriptorToItem) {
                val fieldName = fieldDescriptor.split(":").toTypedArray()[0]
                val field = virtualClass.getField(fieldName)!!
                val actual = graph.getTerminatingFieldConsensus(field)
                testValueEquals(value, actual)
            }
        }
    }

    @JvmStatic
    fun testVisitation(className: String, methodSignature: String, expectedAddresses: IntArray) {
        testVisitation(className, methodSignature, TestState(), expectedAddresses)
    }

    @JvmStatic
    fun testVisitation(className: String, methodSignature: String, initialState: TestState, expectedAddresses: IntArray) {
        val graph = execute(className, methodSignature, initialState)
        testVisitation(graph, expectedAddresses)
    }

    @JvmStatic
    fun testVisitation(graph: ExecutionGraph2, expectedAddresses: IntArray) {
        val addresses: IntArray = graph.getAddresses()
        val visitedAddresses: MutableList<Int> = LinkedList<Int>()
        for (address in addresses) {
            if (graph.wasAddressReached(address)) {
                visitedAddresses.add(address)
            }
        }
        val actualAddresses = Ints.toArray(visitedAddresses)
        Arrays.sort(expectedAddresses)
        Arrays.sort(actualAddresses)
        Assertions.assertArrayEquals(expectedAddresses, actualAddresses)
    }

    private fun buildInitialExecutionState(vm: VirtualMachine2, method: VirtualMethod, initial: TestState): ExecutionState {
        val state = vm.spawnEntrypointState(method)
        for ((register, value) in initial.registers) {
            state.pokeRegister(register, value)
        }
        for ((className, fieldDescriptorToItem) in initial.fields) {
            val virtualClass = vm.classManager.getVirtualClass(className)
            for ((fieldNameAndType, item) in fieldDescriptorToItem) {
                val fieldName = fieldNameAndType.split(":").toTypedArray()[0]
                val field = virtualClass.getField(fieldName)!!
                state.pokeField(field, item)
            }
            state.setClassInitialized(virtualClass, SideEffect.Level.NONE)
        }
        return state
    }


//    @JvmStatic
//    fun setRegisterMock(mState: MethodState, register: Int, value: Any?, type: String) {
//        val item: HeapItem = Mockito.mock(HeapItem::class.java)
//        Mockito.`when`(item.getValue()).thenReturn(value)
//        if (CommonTypes.INTEGER == type && value is Number) {
//            Mockito.`when`(item.asInteger()).thenReturn(value as Int?)
//        } else if (value is UnknownValue) {
//            Mockito.`when`(item.isUnknown()).thenReturn(true)
//        }
//        Mockito.`when`(item.getComponentBase()).thenReturn(ClassNameUtils.getComponentBase(type))
//        Mockito.`when`(item.getType()).thenReturn(type)
//        Mockito.`when`(mState.readRegister(ArgumentMatchers.eq(register))).thenReturn(item)
//    }

    /**
     * Create a new [VirtualMachineImpl] for testing. Since this is heavily used, it tries to avoid the main cost of creating a [ ] by reusing the same [ClassManagerImpl] by default.
     * If `reloadClasses` is true, a new [ClassManagerImpl] is created
     * and all classes are loaded again. This is necessary if method implementations are modified. For example, Simplify optimization strategy tests
     * modify method implementation and in order for each test to have the true method implementations, many of those tests set `reloadClasses`
     * to `true`.
     *
     * @param reloadClasses if true, rebuild [ClassManagerImpl], otherwise reuse existing
     * @return [VirtualMachineImpl] for tests
     */
    fun spawnVM(reloadClasses: Boolean = false): VirtualMachine2 {
        if (reloadClasses) {
            try {
                classManager = ClassManagerFactory().build(TEST_CLASS_PATH, dexBuilder)
            } catch (e: IOException) {
                throw RuntimeException("Exception building class manager for $TEST_CLASS_PATH", e)
            }
        }
        return VirtualMachine2.build(classManager)
    }


//    fun verifyExceptionHandling(expectedExceptions: Set<Throwable?>?, node: ExecutionNode?, mState: MethodState?) {
//        Mockito.verify<Any>(node).setExceptions(ArgumentMatchers.eq(expectedExceptions))
//        Mockito.verify<Any>(node).clearChildren()
//        Mockito.verify<Any>(node, Mockito.times(0)).setChildLocations(ArgumentMatchers.any(Array<MethodLocation>::class.java))
//        Mockito.verify<Any>(mState, Mockito.times(0))
//            .assignRegister(ArgumentMatchers.any(Int::class.java), ArgumentMatchers.any(HeapItem::class.java))
//    }
//
//    fun verifyExceptionHandling(exceptionClass: Class<out Throwable?>?, node: ExecutionNode?, mState: MethodState?) {
//        verifyExceptionHandling(exceptionClass, null, node, mState)
//    }
//
//    @JvmStatic
//    fun verifyExceptionHandling(exceptionClass: Class<out Throwable?>?, message: String?, node: ExecutionNode?, mState: MethodState?) {
//        val argument: ArgumentCaptor<Throwable> = ArgumentCaptor.forClass<Throwable, Throwable>(Throwable::class.java)
//        Mockito.verify<Any>(node).setException(argument.capture())
//        Assertions.assertEquals(exceptionClass, argument.getValue().javaClass)
//        Assertions.assertEquals(message, argument.getValue().message)
//        Mockito.verify<Any>(node).clearChildren()
//        Mockito.verify<Any>(node, Mockito.times(0)).setChildLocations(ArgumentMatchers.any(Array<MethodLocation>::class.java))
//        Mockito.verify<Any>(mState, Mockito.times(0))
//            .assignRegister(ArgumentMatchers.any(Int::class.java), ArgumentMatchers.any(HeapItem::class.java))
//    }

    fun testSimpleException(
        className: String,
        methodDescriptor: String,
        exceptionClass: Class<*>,
        initial: TestState,
        nextAddress: Int,
        exceptionMessage: String? = null
    ) {
        val graph = execute(className, methodDescriptor, initial)
        val value = graph.getTerminatingRegisterConsensus(0)!!
        assertEquals(exceptionClass, value.raw!!.javaClass)
        assertEquals(ClassNameUtils.toInternal(exceptionClass), value.type)
        if (exceptionMessage != null) {
            assertEquals(exceptionMessage, (value.raw as Throwable).message)
        }
        assertFalse(graph.wasAddressReached(nextAddress), "Should not reach next address of non-exceptional execution path")
        val node = graph.getNodePile(0)[0]
        assertEquals(0, node.state.registersAssigned.size)
    }

    private fun testValueEquals(expected: Value, consensus: Value) {
        val expectedValue = expected.raw
        val consensusValue = consensus.raw
        if (expectedValue != null) {
            assertNotNull(consensusValue, "No consensus for value")
        }
        if (expectedValue == null) {
            assertEquals(expected, consensus)
        } else if (expected.isUnknown) {
            // Normally unknown doesn't equal anything, including itself, but tests are more relaxed.
            assertEquals(expected.toString(), consensus.toString())
        } else if (expectedValue.javaClass.isArray) {
            assertEquals(expected.type, consensus.type)
            assertEquals(expectedValue.javaClass, consensusValue!!.javaClass)
            if (expectedValue is Array<*> && consensusValue is Array<*>) {
                assertArrayEquals(expectedValue as Array<Any?>?, consensusValue as Array<Any?>?)
            } else if (expectedValue is ByteArray && consensusValue is ByteArray) {
                assertArrayEquals(expectedValue as ByteArray?, consensusValue as ByteArray?)
            } else if (expectedValue is ShortArray && consensusValue is ShortArray) {
                assertArrayEquals(expectedValue as ShortArray?, consensusValue as ShortArray?)
            } else if (expectedValue is IntArray && consensusValue is IntArray) {
                assertArrayEquals(expectedValue as IntArray?, consensusValue as IntArray?)
            } else if (expectedValue is LongArray && consensusValue is LongArray) {
                assertArrayEquals(expectedValue as LongArray?, consensusValue as LongArray?)
            } else if (expectedValue is CharArray && consensusValue is CharArray) {
                assertArrayEquals(expectedValue as CharArray?, consensusValue as CharArray?)
            } else if (expectedValue is FloatArray && consensusValue is FloatArray) {
                assertArrayEquals(expectedValue as FloatArray?, consensusValue as FloatArray?, 0.001f)
            } else if (expectedValue is DoubleArray && consensusValue is DoubleArray) {
                assertArrayEquals(expectedValue as DoubleArray?, consensusValue as DoubleArray?, 0.001)
            } else if (expectedValue is BooleanArray && consensusValue is BooleanArray) {
                assertArrayEquals(expectedValue as BooleanArray?, consensusValue as BooleanArray?)
            } else {
                assertEquals(expectedValue, consensusValue)
            }
        } else if (expectedValue is StringBuilder) {
            assertEquals(expectedValue.toString(), consensusValue.toString())
        } else {
            assertEquals(expectedValue, consensusValue)
            assertEquals(expected.type, consensus.type)
        }
    }
}