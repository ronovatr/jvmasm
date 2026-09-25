package com.jvmasm.resolve;

import com.jvmasm.ast.CodeItem;
import com.jvmasm.ast.InsnItem;
import com.jvmasm.ast.LabelItem;
import com.jvmasm.ast.LookupSwitchItem;
import com.jvmasm.ast.MethodDecl;
import com.jvmasm.ast.StackFrameItem;
import com.jvmasm.ast.TableSwitchItem;
import com.jvmasm.isa.InstructionDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Basic-block CFG stack-depth checker (verifier-lite).
 *
 * <p>Builds blocks at labels / after branches, propagates stack depth along
 * edges, and errors on underflow, {@code .limit stack} overflow, or mismatched
 * merge depths at a label.
 */
public final class StackDepthChecker {

    public void check(MethodDecl method) {
        if (method.code.isEmpty()) {
            return;
        }

        List<Block> blocks = buildBlocks(method);
        Map<String, Integer> labelDepth = new HashMap<>();
        Map<String, Block> byLabel = new HashMap<>();
        for (Block b : blocks) {
            if (b.label != null) {
                byLabel.put(b.label, b);
            }
        }

        // Entry block starts at depth 0
        blocks.getFirst().entryDepth = 0;
        Set<Block> work = new HashSet<>();
        work.add(blocks.getFirst());

        while (!work.isEmpty()) {
            Block b = work.iterator().next();
            work.remove(b);
            if (b.entryDepth == null) {
                continue;
            }
            int depth = b.entryDepth;
            int maxSeen = depth;
            boolean fallThrough = true;

            for (CodeItem item : b.items) {
                switch (item) {
                    case StackFrameItem ignored -> { }
                    case TableSwitchItem ts -> {
                        depth -= 1;
                        ensureNonNegative(depth, method, item);
                        enqueue(work, byLabel, labelDepth, ts.defaultLabel(), depth, method);
                        for (String lab : ts.caseLabels()) {
                            enqueue(work, byLabel, labelDepth, lab, depth, method);
                        }
                        fallThrough = false;
                    }
                    case LookupSwitchItem ls -> {
                        depth -= 1;
                        ensureNonNegative(depth, method, item);
                        enqueue(work, byLabel, labelDepth, ls.defaultLabel(), depth, method);
                        for (var c : ls.cases()) {
                            enqueue(work, byLabel, labelDepth, c.label(), depth, method);
                        }
                        fallThrough = false;
                    }
                    case InsnItem insn -> {
                        depth = apply(depth, insn);
                        ensureNonNegative(depth, method, insn);
                        maxSeen = Math.max(maxSeen, depth);
                        if (method.maxStack >= 0 && maxSeen > method.maxStack) {
                            throw new IllegalArgumentException(
                                    "operand stack depth " + maxSeen + " exceeds .limit stack "
                                            + method.maxStack + " in " + method.name);
                        }
                        if (isBranch(insn.def())) {
                            enqueue(work, byLabel, labelDepth, insn.operands().getFirst(), depth, method);
                            if (isUnconditional(insn.def())) {
                                fallThrough = false;
                            }
                        } else if (isReturnOrThrow(insn.def())) {
                            fallThrough = false;
                        }
                    }
                    case LabelItem ignored -> { }
                }
            }

            if (fallThrough && b.fallThrough != null) {
                Integer prev = b.fallThrough.entryDepth;
                if (prev == null) {
                    b.fallThrough.entryDepth = depth;
                    work.add(b.fallThrough);
                } else if (!prev.equals(depth)) {
                    throw new IllegalArgumentException(
                            "stack depth merge mismatch into block "
                                    + (b.fallThrough.label != null ? b.fallThrough.label : "?")
                                    + ": " + prev + " vs " + depth + " in " + method.name);
                }
            }
        }
    }

    private static void enqueue(
            Set<Block> work,
            Map<String, Block> byLabel,
            Map<String, Integer> labelDepth,
            String label,
            int depth,
            MethodDecl method) {
        Block target = byLabel.get(label);
        if (target == null) {
            throw new IllegalArgumentException("unknown label '" + label + "' in " + method.name);
        }
        Integer prev = target.entryDepth;
        if (prev == null) {
            target.entryDepth = depth;
            labelDepth.put(label, depth);
            work.add(target);
        } else if (!prev.equals(depth)) {
            throw new IllegalArgumentException(
                    "stack depth merge mismatch at " + label + ": " + prev + " vs " + depth
                            + " in " + method.name);
        }
    }

    private static void ensureNonNegative(int depth, MethodDecl method, CodeItem item) {
        if (depth < 0) {
            throw new IllegalArgumentException(
                    "operand stack underflow in " + method.name + " near line " + lineOf(item));
        }
    }

    private static List<Block> buildBlocks(MethodDecl method) {
        List<Block> blocks = new ArrayList<>();
        Block current = new Block(null);
        blocks.add(current);

        for (CodeItem item : method.code) {
            if (item instanceof LabelItem(String name)) {
                if (!current.items.isEmpty() || current.label != null) {
                    Block next = new Block(name);
                    current.fallThrough = next;
                    blocks.add(next);
                    current = next;
                } else {
                    current.label = name;
                }
                continue;
            }
            current.items.add(item);
            if (item instanceof InsnItem insn && (isUnconditional(insn.def()) || isReturnOrThrow(insn.def()))) {
                Block next = new Block(null);
                // no fall-through edge
                blocks.add(next);
                current = next;
            } else if (item instanceof TableSwitchItem || item instanceof LookupSwitchItem) {
                Block next = new Block(null);
                blocks.add(next);
                current = next;
            }
        }
        // Drop trailing empty unlabeled blocks
        blocks.removeIf(b -> b.items.isEmpty() && b.label == null && b != blocks.getFirst());
        // Relink fall-through for remaining
        Map<Block, Integer> index = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            index.put(blocks.get(i), i);
        }
        return blocks;
    }

    private static boolean isBranch(InstructionDef def) {
        return switch (def.shape()) {
            case BRANCH, BRANCH_W -> true;
            default -> false;
        };
    }

    private static boolean isUnconditional(InstructionDef def) {
        return switch (def) {
            case GOTO, GOTO_W, JSR, JSR_W -> true;
            default -> false;
        };
    }

    private static boolean isReturnOrThrow(InstructionDef def) {
        return switch (def) {
            case RETURN, IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, ATHROW -> true;
            default -> false;
        };
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
                 IRETURN, FRETURN, ARETURN,
                 IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> -1;
            case POP2, LSTORE, LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3,
                 DSTORE, DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3,
                 LRETURN, DRETURN -> -2;
            case DUP -> +1;
            case DUP2 -> +2;
            case SWAP -> 0;
            case IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR,
                 FADD, FSUB, FMUL, FDIV, FREM -> -1;
            case LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR,
                 DADD, DSUB, DMUL, DDIV, DREM -> -2;
            case LSHL, LSHR, LUSHR -> -1;
            case INEG, LNEG, FNEG, DNEG, ARRAYLENGTH -> 0;
            case GETSTATIC -> fieldPush(insn);
            case PUTSTATIC -> -fieldPush(insn);
            case GETFIELD -> fieldPush(insn) - 1;
            case PUTFIELD -> -(1 + fieldPush(insn));
            case INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE -> invokeDelta(insn, true);
            case INVOKESTATIC -> invokeDelta(insn, false);
            case NEW, ANEWARRAY, NEWARRAY -> +1;
            case ATHROW -> -1;
            case RETURN -> 0;
            case GOTO, GOTO_W, JSR, JSR_W -> 0;
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE -> -2;
            default -> 0;
        };
        return depth + delta;
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
        return returnSlots(desc) - argSlots(desc) - (hasReceiver ? 1 : 0);
    }

    private static int argSlots(String methodDesc) {
        int slots = 0;
        int i = 1;
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
                while (methodDesc.charAt(i) == '[') {
                    i++;
                }
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
            case StackFrameItem s -> s.line();
            case LabelItem ignored -> -1;
        };
    }

    private static final class Block {
        String label;
        final List<CodeItem> items = new ArrayList<>();
        Block fallThrough;
        Integer entryDepth;

        Block(String label) {
            this.label = label;
        }
    }
}
