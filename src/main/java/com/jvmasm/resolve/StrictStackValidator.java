package com.jvmasm.resolve;

import com.jvmasm.ast.*;
import com.jvmasm.isa.InstructionDef;
import com.jvmasm.isa.OperandShape;

import java.util.HashSet;
import java.util.Set;

/**
 * When strict stack mode is on (and major ≥ 50), every branch target and
 * exception handler must have an explicit {@code .stack} frame.
 */
public final class StrictStackValidator {

    public void check(ClassDecl classDecl) {
        if (classDecl.majorVersion < 50) {
            return;
        }
        for (MethodDecl method : classDecl.methods) {
            checkMethod(method);
        }
    }

    private void checkMethod(MethodDecl method) {
        Set<String> frames = new HashSet<>();
        Set<String> targets = new HashSet<>();

        for (CodeItem item : method.code) {
            switch (item) {
                case StackFrameItem stackFrame -> frames.add(stackFrame.label());
                case InsnItem insn -> {
                    if (insn.def().shape() == OperandShape.BRANCH || insn.def().shape() == OperandShape.BRANCH_W) {
                        targets.add(insn.operands().getFirst());
                    }
                    if (insn.def() == InstructionDef.JSR || insn.def() == InstructionDef.JSR_W) {
                        targets.add(insn.operands().getFirst());
                    }
                }
                case TableSwitchItem tableSwitch -> {
                    targets.add(tableSwitch.defaultLabel());
                    targets.addAll(tableSwitch.caseLabels());
                }
                case LookupSwitchItem lookupSwitch -> {
                    targets.add(lookupSwitch.defaultLabel());
                    for (LookupCase lookupCase : lookupSwitch.cases()) {
                        targets.add(lookupCase.label());
                    }
                }
				default -> { }
            }
        }
        for (CatchEntry entry : method.catches) {
            targets.add(entry.handler());
        }

        for (String target : targets) {
            if (!frames.contains(target)) {
                throw new IllegalArgumentException("strict stack: missing .stack for branch/handler target '" + target + "' in method " + method.name);
            }
        }
    }

}
