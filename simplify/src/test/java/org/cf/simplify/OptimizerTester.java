package org.cf.simplify;

import org.cf.smalivm.TestState;
import org.cf.smalivm.Tester;
import org.cf.smalivm.VirtualMachine;
import org.cf.smalivm.context.ExecutionGraphImpl;
import org.cf.smalivm.type.VirtualMethod;
import org.jf.dexlib2.writer.builder.DexBuilder;

public class OptimizerTester {

    public static ExecutionGraphManipulator getGraphManipulator(String className, String methodSignature, Object... args) {
        TestState initial = new TestState();
        if (args.length > 0) {
            initial.setRegisters(args);
        }

        return getGraphManipulator(className, methodSignature, initial);
    }

    public static ExecutionGraphManipulator getGraphManipulator(String className, String methodDescriptor, TestState initial) {
        // Force reloading of classes since implementations in class definitions may have changed
        VirtualMachine vm = Tester.spawnVM(true);

        return getGraphManipulator(vm, className, methodDescriptor, initial);
    }

    public static ExecutionGraphManipulator getGraphManipulator(VirtualMachine vm, String className,
                                                                String methodDescriptor, TestState initial) {
        ExecutionGraphImpl graph = Tester.execute(vm, className, methodDescriptor, initial);

        String methodSignature = className + "->" + methodDescriptor;
        VirtualMethod method = vm.getClassManager().getMethod(methodSignature);
        DexBuilder dexBuilder = Tester.getDexBuilder();

        return new ExecutionGraphManipulator(graph, method, vm, dexBuilder);
    }

}
