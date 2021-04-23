package org.cf.smalivm

import org.cf.smalivm2.Value

class TestState {
    private val classNameToFieldDescriptorToItem: MutableMap<String, MutableMap<String, Value>> = HashMap()
    private val registerToItem: MutableMap<Int, Value> = HashMap()

    fun getField(className: String, fieldName: String): Value? {
        return classNameToFieldDescriptorToItem[className]!![fieldName]
    }

    val fields: Map<String, MutableMap<String, Value>>
        get() = classNameToFieldDescriptorToItem

    fun getRegister(register: Int): Value? {
        return registerToItem[register]
    }

    val registers: Map<Int, Value>
        get() = registerToItem

    fun setRegisters(vararg params: Any?) {
        // register, value, type
        var i = 0
        while (i < params.size) {
            val register = params[i] as Int
            val type = params[i + 2] as String
            val item = Value.wrap(params[i + 1], type)
            setRegister(register, item)
            i += 3
        }
    }

    fun setRegister(register: Int, value: Any?, type: String?) {
        val item = Value.wrap(value, type!!)
        setRegister(register, item)
    }

    fun setField(className: String, fieldDescriptor: String, item: Value) {
        if (!classNameToFieldDescriptorToItem.containsKey(className)) {
            classNameToFieldDescriptorToItem[className] = HashMap()
        }
        classNameToFieldDescriptorToItem[className]!![fieldDescriptor] = item
    }

    fun setFields(className: String, vararg params: Any) {
        var i = 0
        while (i < params.size) {
            val fieldDescriptor = params[i] as String
            val type = fieldDescriptor.split(":").toTypedArray()[1]
            val item = Value.wrap(params[i + 1], type)
            setField(className, fieldDescriptor, item)
            i += 2
        }
    }

    fun setRegister(register: Int, item: Value) {
        registerToItem[register] = item
    }
}