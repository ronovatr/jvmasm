package com.jvmasm.resolve;

import com.jvmasm.ast.CatchEntry;
import com.jvmasm.ast.ClassDecl;
import com.jvmasm.ast.CodeItem;
import com.jvmasm.ast.InsnItem;
import com.jvmasm.ast.LabelItem;
import com.jvmasm.ast.LookupSwitchItem;
import com.jvmasm.ast.MethodDecl;
import com.jvmasm.ast.StackFrameItem;
import com.jvmasm.ast.TableSwitchItem;
import com.jvmasm.isa.InstructionDef;
import com.jvmasm.isa.OperandShape;

import java.util.HashSet;
import java.util.Set;

/**
 * When strict stack mode is on (and major ≥ 50), every branch target and
 * exception handler must have an explicit {@code .stack} frame.
 */
public final class StrictStackValidator {

    public void check(ClassDecl cls) {
        if (cls.majorVersion < 50) {
            return;
        }
        for (MethodDecl method : cls.methods) {
            checkMethod(method);
        }
    }

    private void checkMethod(MethodDecl method) {
        Set<String> frames = new HashSet<>();
        Set<String> targets = new HashSet<>();

        for (CodeItem item : method.code) {
            switch (item) {
                case StackFrameItem sf -> frames.add(sf.label());
                case InsnItem insn -> {
                    if (insn.def().shape() == OperandShape.BRANCH
                            || insn.def().shape() == OperandShape.BRANCH_W) {
                        targets.add(insn.operands().getFirst());
                    }
                    if (insn.def() == InstructionDef.JSR || insn.def() == InstructionDef.JSR_W) {
                        targets.add(insn.operands().getFirst());
                    }
                }
                case TableSwitchItem ts -> {
                    targets.add(ts.defaultLabel());
                    targets.addAll(ts.caseLabels());
                }
                case LookupSwitchItem ls -> {
                    targets.add(ls.defaultLabel());
                    for (var c : ls.cases()) {
                        targets.add(c.label());
                    }
                }
                case LabelItem ignored -> { }
                default -> { }
            }
        }
        for (CatchEntry c : method.catches) {
            targets.add(c.handler());
        }

        for (String t : targets) {
            if (!frames.contains(t)) {
                throw new IllegalArgumentException(
                        "strict stack: missing .stack for branch/handler target '"
                                + t + "' in method " + method.name);
            }
        }
    }
}
