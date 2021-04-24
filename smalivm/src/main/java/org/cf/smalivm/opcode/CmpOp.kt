package org.cf.smalivm.opcode

import org.cf.smalivm.configuration.Configuration
import org.cf.smalivm.dex.CommonTypes
import org.cf.smalivm.dex.SmaliClassLoader
import org.cf.smalivm.type.ClassManager
import org.cf.smalivm2.ExecutionNode
import org.cf.smalivm2.UnresolvedChild
import org.cf.smalivm2.Value
import org.cf.util.Utils
import org.jf.dexlib2.builder.MethodLocation
import org.jf.dexlib2.iface.instruction.formats.Instruction23x
import org.slf4j.LoggerFactory

class CmpOp internal constructor(
    location: MethodLocation,
    val destRegister: Int,
    val lhsRegister: Int,
    val rhsRegister: Int
) : Op(location) {

    override val registersReadCount = 2
    override val registersAssignedCount = 1

    override fun execute(node: ExecutionNode): Array<out UnresolvedChild> {
        val lhs = node.state.readRegister(lhsRegister)
        val rhs = node.state.readRegister(rhsRegister)
        val item = if (lhs.isUnknown || rhs.isUnknown) {
            Value.unknown(CommonTypes.INTEGER)
        } else {
            assert(lhs.raw!!.javaClass == rhs.raw!!.javaClass)
            assert(lhs.type == rhs.type)
            val cmp = cmp(lhs.raw as Number, rhs.raw as Number)
            Value.wrap(cmp, CommonTypes.INTEGER)
        }
        node.state.assignRegister(destRegister, item)
        return finishOp()
    }

    override fun toString(): String = "$name r$destRegister, r$lhsRegister, r$rhsRegister"

    private fun cmp(val1: Number, val2: Number): Int {
        val arg1IsNan = val1 is Float && val1.isNaN() || val1 is Double && val1.isNaN()
        val arg2IsNan = val2 is Float && val2.isNaN() || val2 is Double && val2.isNaN()
        return if (arg1IsNan || arg2IsNan) {
            when {
                name.startsWith("cmpg") -> 1
                else -> -1 // cmpl
            }
        } else {
            when {
                name.endsWith("float") -> {
                    val castVal1 = Utils.getFloatValue(val1)
                    val castVal2 = Utils.getFloatValue(val2)
                    // The docs say "b == c" but I don't think they mean identity.
                    java.lang.Float.compare(castVal1, castVal2)
                }
                name.endsWith("double") -> {
                    val castVal1 = Utils.getDoubleValue(val1)
                    val castVal2 = Utils.getDoubleValue(val2)
                    // The docs say "b == c" but I don't think they mean identity.
                    java.lang.Double.compare(castVal1, castVal2)
                }
                else -> {
                    val castVal1 = Utils.getLongValue(val1)
                    val castVal2 = Utils.getLongValue(val2)
                    java.lang.Long.compare(castVal1, castVal2)
                }
            }
        }
    }

    companion object : OpFactory {
        private val log = LoggerFactory.getLogger(CmpOp::class.java.simpleName)

        override fun build(
            location: MethodLocation,
            classManager: ClassManager,
            classLoader: SmaliClassLoader,
            configuration: Configuration
        ): CmpOp {
            val instr = location.instruction as Instruction23x
            val destRegister = instr.registerA
            val lhsRegister = instr.registerB
            val rhsRegister = instr.registerC
            return CmpOp(location, destRegister, lhsRegister, rhsRegister)
        }
    }
}
