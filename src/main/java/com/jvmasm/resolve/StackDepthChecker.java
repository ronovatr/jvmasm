package com.jvmasm.resolve;

import com.jvmasm.ast.CodeItem;
import com.jvmasm.ast.InsnItem;
import com.jvmasm.ast.LabelItem;
import com.jvmasm.ast.LookupSwitchItem;
import com.jvmasm.ast.MethodDecl;
import com.jvmasm.ast.TableSwitchItem;
import com.jvmasm.isa.InstructionDef;

/**
 * Lightweight assemble-time stack-depth tracker (verifier-lite).
 * Tracks a single linear depth; branch merges are approximated (Phase 3 full CFG later).
 */
public final class StackDepthChecker {

    public void check(MethodDecl method) {
        if (method.maxStack < 0) {
            return; // nothing to validate against
        }
        int depth = 0;
        int maxSeen = 0;
        for (CodeItem item : method.code) {
            switch (item) {
                case LabelItem ignored -> { /* reset not modeled yet */ }
                case TableSwitchItem ignored -> depth -= 1; // consumes int key
                case LookupSwitchItem ignored -> depth -= 1;
                case InsnItem insn -> depth = apply(depth, insn);
            }
            if (depth < 0) {
                throw new IllegalArgumentException(
                        "operand stack underflow in " + method.name + " near line "
                                + lineOf(item));
            }
            maxSeen = Math.max(maxSeen, depth);
            if (maxSeen > method.maxStack) {
                throw new IllegalArgumentException(
                        "operand stack depth " + maxSeen + " exceeds .limit stack "
                                + method.maxStack + " in " + method.name);
            }
        }
    }

    private static int apply(int depth, InsnItem insn) {
        InstructionDef def = insn.def();
        int delta = switch (def) {
            case NOP, IINC -> 0;
            case ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                 FCONST_0, FCONST_1, FCONST_2, BIPUSH, SIPUSH, LDC, LDC_W,
                 ILOAD, ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3,
                 FLOAD, FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3,
                 ALOAD, ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3 -> +1;
            case LCONST_0, LCONST_1, DCONST_0, DCONST_1, LDC2_W,
                 LLOAD, LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3,
                 DLOAD, DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3 -> +2;
            case POP, ISTORE, ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3,
                 FSTORE, FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3,
                 ASTORE, ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3,
                 IRETURN, FRETURN, ARETURN, TABLESWITCH, LOOKUPSWITCH,
                 IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> -1;
            case POP2, LSTORE, LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3,
                 DSTORE, DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3,
                 LRETURN, DRETURN -> -2;
            case DUP -> +1;
            case DUP2 -> +2;
            case SWAP -> 0;
            case IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB,
                 IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV,
                 IREM, LREM, FREM, DREM,
                 IAND, LAND, IOR, LOR, IXOR, LXOR,
                 ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR -> {
                // category-2 ops consume 4 and push 2, etc. Approximate:
                yield isCat2Math(def) ? -2 : -1;
            }
            case INEG, LNEG, FNEG, DNEG, ARRAYLENGTH -> 0;
            case GETSTATIC -> fieldPush(insn);
            case PUTSTATIC -> -fieldPush(insn);
            case GETFIELD -> fieldPush(insn); // pops ref, pushes value — net approx 0 for cat1
            case PUTFIELD -> -(1 + fieldPush(insn));
            case INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE -> invokeDelta(insn, true);
            case INVOKESTATIC -> invokeDelta(insn, false);
            case NEW, ANEWARRAY, NEWARRAY -> +1;
            case ATHROW -> -1;
            case RETURN -> 0;
            case GOTO, GOTO_W, JSR, JSR_W -> 0;
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE -> -2;
            default -> 0; // conservative: don't fail on unknown
        };
        return depth + delta;
    }

    private static boolean isCat2Math(InstructionDef def) {
        return switch (def) {
            case LADD, DADD, LSUB, DSUB, LMUL, DMUL, LDIV, DDIV, LREM, DREM,
                 LAND, LOR, LXOR, LSHL, LSHR, LUSHR -> true;
            default -> false;
        };
    }

    private static int fieldPush(InsnItem insn) {
        String desc = insn.operands().size() >= 2
                ? insn.operands().get(1)
                : guessDesc(insn.operands().getFirst());
        return (desc.startsWith("J") || desc.startsWith("D")) ? 2 : 1;
    }

    private static String guessDesc(String ref) {
        int i = ref.lastIndexOf(' ');
        return i >= 0 ? ref.substring(i + 1) : "I";
    }

    private static int invokeDelta(InsnItem insn, boolean hasReceiver) {
        String ref = insn.operands().getFirst();
        int paren = ref.indexOf('(');
        if (paren < 0) {
            return 0;
        }
        String desc = ref.substring(paren);
        int args = argSlots(desc);
        int ret = returnSlots(desc);
        int recv = hasReceiver ? 1 : 0;
        return ret - args - recv;
    }

    private static int argSlots(String methodDesc) {
        int slots = 0;
        int i = 1; // skip '('
        while (i < methodDesc.length() && methodDesc.charAt(i) != ')') {
            char c = methodDesc.charAt(i);
            if (c == 'J' || c == 'D') {
                slots += 2;
                i++;
            } else if (c == 'L') {
                slots += 1;
                i = methodDesc.indexOf(';', i) + 1;
            } else if (c == '[') {
                slots += 1;
                while (methodDesc.charAt(i) == '[') i++;
                if (methodDesc.charAt(i) == 'L') {
                    i = methodDesc.indexOf(';', i) + 1;
                } else {
                    i++;
                }
            } else {
                slots += 1;
                i++;
            }
        }
        return slots;
    }

    private static int returnSlots(String methodDesc) {
        char c = methodDesc.charAt(methodDesc.indexOf(')') + 1);
        return switch (c) {
            case 'V' -> 0;
            case 'J', 'D' -> 2;
            default -> 1;
        };
    }

    private static int lineOf(CodeItem item) {
        return switch (item) {
            case InsnItem i -> i.line();
            case TableSwitchItem t -> t.line();
            case LookupSwitchItem l -> l.line();
            case LabelItem ignored -> -1;
        };
    }
}
