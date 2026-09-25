package com.jvmasm.disasm;

import com.jvmasm.isa.InstructionDef;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * .class → .jasm printer. Preserves the exact opcode mnemonic present in the
 * bytecode ({@code iload_0} stays {@code iload_0}, never rewritten to {@code iload 0}).
 */
public final class Disassembler {

    public String disassemble(byte[] classBytes) {
        ClassModel cm = ClassFile.of().parse(classBytes);
        StringBuilder out = new StringBuilder();

        int flags = cm.flags().flagsMask();
        out.append(".class");
        appendFlags(out, flags, true);
        out.append(' ').append(cm.thisClass().asInternalName()).append('\n');
        cm.superclass().ifPresent(sc ->
                out.append(".super ").append(sc.asInternalName()).append('\n'));
        for (var iface : cm.interfaces()) {
            out.append(".implements ").append(iface.asInternalName()).append('\n');
        }
        cm.findAttribute(Attributes.sourceFile()).ifPresent(sf ->
                out.append(".source \"").append(escape(sf.sourceFile().stringValue())).append("\"\n"));
        out.append('\n');

        for (MethodModel method : cm.methods()) {
            out.append(".method");
            appendFlags(out, method.flags().flagsMask(), false);
            out.append(' ').append(method.methodName().stringValue())
                    .append(method.methodType().stringValue()).append('\n');

            Optional<CodeAttribute> codeAttr = method.code()
                    .filter(CodeAttribute.class::isInstance)
                    .map(CodeAttribute.class::cast);
            if (codeAttr.isPresent()) {
                CodeAttribute code = codeAttr.get();
                out.append("    .limit stack ").append(code.maxStack()).append('\n');
                out.append("    .limit locals ").append(code.maxLocals()).append('\n');
                printCode(out, code);
            }
            out.append(".end method\n\n");
        }
        return out.toString();
    }

    private void printCode(StringBuilder out, CodeAttribute code) {
        Map<Label, String> labelNames = new LinkedHashMap<>();
        java.util.concurrent.atomic.AtomicInteger labelCounter = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Function<Label, String> nameLabel =
                l -> "L" + labelCounter.getAndIncrement();
        // Pre-scan for branch targets
        for (CodeElement el : code) {
            if (el instanceof BranchInstruction br) {
                labelNames.computeIfAbsent(br.target(), nameLabel);
            } else if (el instanceof TableSwitchInstruction ts) {
                labelNames.computeIfAbsent(ts.defaultTarget(), nameLabel);
                for (var c : ts.cases()) {
                    labelNames.computeIfAbsent(c.target(), nameLabel);
                }
            } else if (el instanceof LookupSwitchInstruction ls) {
                labelNames.computeIfAbsent(ls.defaultTarget(), nameLabel);
                for (var c : ls.cases()) {
                    labelNames.computeIfAbsent(c.target(), nameLabel);
                }
            } else if (el instanceof Label lab) {
                labelNames.computeIfAbsent(lab, nameLabel);
            }
        }

        for (CodeElement el : code) {
            if (el instanceof Label lab) {
                out.append(labelNames.get(lab)).append(":\n");
                continue;
            }
            if (!(el instanceof Instruction insn)) {
                continue;
            }
            out.append("    ").append(formatInstruction(insn, labelNames)).append('\n');
        }
    }

    private String formatInstruction(Instruction insn, Map<Label, String> labels) {
        Opcode op = insn.opcode();
        String mnem = mnemonicFor(op);

        return switch (insn) {
            case LoadInstruction li -> formatLoadStore(mnem, op, li.slot());
            case StoreInstruction si -> formatLoadStore(mnem, op, si.slot());
            case ConstantInstruction.IntrinsicConstantInstruction ignored -> mnem;
            case ConstantInstruction.ArgumentConstantInstruction ac ->
                    mnem + " " + ac.constantValue();
            case ConstantInstruction.LoadConstantInstruction lc ->
                    mnem + " " + formatConstant(lc.constantValue());
            case BranchInstruction br -> mnem + " " + labels.get(br.target());
            case FieldInstruction fi -> mnem + " " + fi.owner().asInternalName() + "/"
                    + fi.name().stringValue() + " " + fi.type().stringValue();
            case InvokeInstruction ii -> mnem + " " + ii.owner().asInternalName() + "/"
                    + ii.name().stringValue() + ii.type().stringValue();
            case OperatorInstruction ignored -> mnem;
            case StackInstruction ignored -> mnem;
            case ReturnInstruction ignored -> mnem;
            case ConvertInstruction ignored -> mnem;
            case ArrayLoadInstruction ignored -> mnem;
            case ArrayStoreInstruction ignored -> mnem;
            case MonitorInstruction ignored -> mnem;
            case IncrementInstruction ii -> {
                if (op == Opcode.IINC_W) {
                    yield "wide iinc " + ii.slot() + " " + ii.constant();
                }
                yield "iinc " + ii.slot() + " " + ii.constant();
            }
            case NewObjectInstruction n -> "new " + n.className().asInternalName();
            case NewReferenceArrayInstruction n -> "anewarray " + n.componentType().asInternalName();
            case NewPrimitiveArrayInstruction n -> "newarray " + n.typeKind().name().toLowerCase();
            case NewMultiArrayInstruction n ->
                    "multianewarray " + n.arrayType().asInternalName() + " " + n.dimensions();
            case TypeCheckInstruction t -> mnem + " " + t.type().asInternalName();
            case TableSwitchInstruction ts -> formatTableSwitch(ts, labels);
            case LookupSwitchInstruction ls -> formatLookupSwitch(ls, labels);
            case DiscontinuedInstruction.JsrInstruction jsr ->
                    mnemonicFor(jsr.opcode()) + " " + labels.get(jsr.target());
            case DiscontinuedInstruction.RetInstruction ret -> {
                if (ret.opcode() == Opcode.RET_W) {
                    yield "wide ret " + ret.slot();
                }
                yield "ret " + ret.slot();
            }
            default -> mnem + " ; TODO unhandled " + insn.getClass().getSimpleName();
        };
    }

    private static String formatLoadStore(String mnem, Opcode op, int slot) {
        // Shorthand opcodes encode the slot — print bare mnemonic.
        if (op.sizeIfFixed() == 1) {
            return mnem;
        }
        if (op == Opcode.ILOAD_W || op == Opcode.LLOAD_W || op == Opcode.FLOAD_W
                || op == Opcode.DLOAD_W || op == Opcode.ALOAD_W
                || op == Opcode.ISTORE_W || op == Opcode.LSTORE_W || op == Opcode.FSTORE_W
                || op == Opcode.DSTORE_W || op == Opcode.ASTORE_W) {
            String base = mnem.endsWith("_w") ? mnem.substring(0, mnem.length() - 2) : mnem;
            // Class-File API uses ILOAD_W; our language writes "wide iload"
            String historic = base.contains("_") ? base : base;
            // Map ILOAD_W -> wide iload
            String bare = op.name().toLowerCase().replace("_w", "");
            return "wide " + bare + " " + slot;
        }
        return mnem + " " + slot;
    }

    private static String formatConstant(java.lang.constant.ConstantDesc v) {
        if (v instanceof String s) {
            return "\"" + escape(s) + "\"";
        }
        return String.valueOf(v);
    }

    private static String formatTableSwitch(TableSwitchInstruction ts, Map<Label, String> labels) {
        StringBuilder sb = new StringBuilder("tableswitch default ");
        sb.append(labels.get(ts.defaultTarget()));
        sb.append(" low ").append(ts.lowValue());
        sb.append(" high ").append(ts.highValue());
        sb.append(" { ");
        boolean first = true;
        for (var c : ts.cases()) {
            if (!first) sb.append(' ');
            sb.append(labels.get(c.target()));
            first = false;
        }
        sb.append(" }");
        return sb.toString();
    }

    private static String formatLookupSwitch(LookupSwitchInstruction ls, Map<Label, String> labels) {
        StringBuilder sb = new StringBuilder("lookupswitch default ");
        sb.append(labels.get(ls.defaultTarget())).append(" { ");
        boolean first = true;
        for (var c : ls.cases()) {
            if (!first) sb.append(", ");
            sb.append(c.caseValue()).append(" -> ").append(labels.get(c.target()));
            first = false;
        }
        sb.append(" }");
        return sb.toString();
    }

    private static String mnemonicFor(Opcode op) {
        // Wide pseudo-opcodes are printed via formatLoadStore as "wide …"
        String name = op.name().toLowerCase();
        if (InstructionDef.lookup(name).isPresent()) {
            return name;
        }
        return name;
    }

    private static void appendFlags(StringBuilder out, int flags, boolean isClass) {
        if ((flags & ClassFile.ACC_PUBLIC) != 0) out.append(" public");
        if ((flags & ClassFile.ACC_PRIVATE) != 0) out.append(" private");
        if ((flags & ClassFile.ACC_PROTECTED) != 0) out.append(" protected");
        if ((flags & ClassFile.ACC_STATIC) != 0) out.append(" static");
        if ((flags & ClassFile.ACC_FINAL) != 0) out.append(" final");
        if ((flags & ClassFile.ACC_INTERFACE) != 0) out.append(" interface");
        if ((flags & ClassFile.ACC_ABSTRACT) != 0) out.append(" abstract");
        if ((flags & ClassFile.ACC_SYNTHETIC) != 0) out.append(" synthetic");
        if ((flags & ClassFile.ACC_ENUM) != 0) out.append(" enum");
        if (!isClass) {
            if ((flags & ClassFile.ACC_SYNCHRONIZED) != 0) out.append(" synchronized");
            if ((flags & ClassFile.ACC_BRIDGE) != 0) out.append(" bridge");
            if ((flags & ClassFile.ACC_VARARGS) != 0) out.append(" varargs");
            if ((flags & ClassFile.ACC_NATIVE) != 0) out.append(" native");
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
    }
}
