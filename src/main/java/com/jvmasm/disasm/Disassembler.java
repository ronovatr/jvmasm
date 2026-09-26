package com.jvmasm.disasm;

import com.jvmasm.isa.InstructionDef;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

        java.util.Map<Integer, String> bsmNames = new java.util.LinkedHashMap<>();
        cm.findAttribute(Attributes.bootstrapMethods()).ifPresent(bsms -> {
            int i = 0;
            for (var entry : bsms.bootstrapMethods()) {
                String name = "B" + i++;
                bsmNames.put(entry.bsmIndex(), name);
                var mh = entry.bootstrapMethod().asSymbol();
                out.append(".bootstrap ").append(name).append(' ');
                out.append(switch (mh.kind()) {
                    case STATIC -> "invokestatic";
                    case VIRTUAL -> "invokevirtual";
                    case SPECIAL -> "invokespecial";
                    case INTERFACE_VIRTUAL -> "invokeinterface";
                    case INTERFACE_STATIC -> "interface_static";
                    case INTERFACE_SPECIAL -> "interface_special";
                    case CONSTRUCTOR -> "newInvokeSpecial";
                    case GETTER -> "getfield";
                    case SETTER -> "putfield";
                    case STATIC_GETTER -> "getstatic";
                    case STATIC_SETTER -> "putstatic";
                });
                out.append(' ').append(internalName(mh.owner())).append('/').append(mh.methodName());
                out.append(mh.lookupDescriptor());
                for (var arg : entry.arguments()) {
                    out.append(' ').append(formatConstant(arg.constantValue()));
                }
                out.append('\n');
            }
            if (!bsms.bootstrapMethods().isEmpty()) {
                out.append('\n');
            }
        });

        for (FieldModel field : cm.fields()) {
            out.append(".field");
            appendFlags(out, field.flags().flagsMask(), false);
            out.append(' ').append(field.fieldType().stringValue())
                    .append(' ').append(field.fieldName().stringValue());
            field.findAttribute(Attributes.constantValue()).ifPresent(cv ->
                    out.append(" = ").append(formatConstant(cv.constant().constantValue())));
            out.append('\n');
        }
        if (!cm.fields().isEmpty()) {
            out.append('\n');
        }

        for (MethodModel method : cm.methods()) {
            out.append(".method");
            appendFlags(out, method.flags().flagsMask(), false);
            out.append(' ').append(method.methodName().stringValue())
                    .append(method.methodType().stringValue()).append('\n');

            method.findAttribute(Attributes.exceptions()).ifPresent(ex -> {
                for (var e : ex.exceptions()) {
                    out.append("    .throws ").append(e.asInternalName()).append('\n');
                }
            });

            Optional<CodeAttribute> codeAttr = method.code()
                    .filter(CodeAttribute.class::isInstance)
                    .map(CodeAttribute.class::cast);
            if (codeAttr.isPresent()) {
                CodeAttribute code = codeAttr.get();
                out.append("    .limit stack ").append(code.maxStack()).append('\n');
                out.append("    .limit locals ").append(code.maxLocals()).append('\n');
                printCode(out, code, bsmNames);
            }
            out.append(".end method\n\n");
        }
        return out.toString();
    }

    private void printCode(StringBuilder out, CodeAttribute code, Map<Integer, String> bsmNames) {
        Map<Label, String> labelNames = new LinkedHashMap<>();
        java.util.concurrent.atomic.AtomicInteger labelCounter =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Function<Label, String> nameLabel =
                l -> "L" + labelCounter.getAndIncrement();

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
        for (ExceptionCatch ec : code.exceptionHandlers()) {
            labelNames.computeIfAbsent(ec.tryStart(), nameLabel);
            labelNames.computeIfAbsent(ec.tryEnd(), nameLabel);
            labelNames.computeIfAbsent(ec.handler(), nameLabel);
        }
        code.findAttribute(Attributes.stackMapTable()).ifPresent(smt -> {
            for (StackMapFrameInfo frame : smt.entries()) {
                labelNames.computeIfAbsent(frame.target(), nameLabel);
            }
        });

        for (ExceptionCatch ec : code.exceptionHandlers()) {
            out.append("    .catch ");
            ec.catchType().ifPresentOrElse(
                    t -> out.append(t.asInternalName()),
                    () -> out.append("any"));
            out.append(" from ").append(labelNames.get(ec.tryStart()))
                    .append(" to ").append(labelNames.get(ec.tryEnd()))
                    .append(" using ").append(labelNames.get(ec.handler()))
                    .append('\n');
        }

        Map<Label, StackMapFrameInfo> framesByLabel = new LinkedHashMap<>();
        code.findAttribute(Attributes.stackMapTable()).ifPresent(smt -> {
            for (StackMapFrameInfo frame : smt.entries()) {
                framesByLabel.put(frame.target(), frame);
            }
        });

        for (CodeElement el : code) {
            if (el instanceof Label lab) {
                out.append(labelNames.get(lab)).append(":\n");
                StackMapFrameInfo frame = framesByLabel.get(lab);
                if (frame != null) {
                    out.append("    .stack at ").append(labelNames.get(lab))
                            .append(" locals");
                    for (var t : frame.locals()) {
                        out.append(' ').append(formatVerificationType(t, labelNames));
                    }
                    out.append(" stack");
                    for (var t : frame.stack()) {
                        out.append(' ').append(formatVerificationType(t, labelNames));
                    }
                    out.append('\n');
                }
                continue;
            }
            if (el instanceof LineNumber ln) {
                out.append("    .line ").append(ln.line()).append('\n');
                continue;
            }
            if (!(el instanceof Instruction insn)) {
                continue;
            }
            out.append("    ").append(formatInstruction(insn, labelNames, bsmNames)).append('\n');
        }
    }

    private static String internalName(java.lang.constant.ClassDesc cd) {
        if (cd.isClassOrInterface()) {
            return cd.packageName().isEmpty()
                    ? cd.displayName()
                    : cd.packageName().replace('.', '/') + "/" + cd.displayName();
        }
        return cd.descriptorString();
    }

    private static String formatVerificationType(
            StackMapFrameInfo.VerificationTypeInfo t, Map<Label, String> labels) {
        return switch (t) {
            case StackMapFrameInfo.SimpleVerificationTypeInfo s -> switch (s) {
                case TOP -> "top";
                case INTEGER -> "int";
                case FLOAT -> "float";
                case DOUBLE -> "double";
                case LONG -> "long";
                case NULL -> "null";
                case UNINITIALIZED_THIS -> "uninitializedThis";
            };
            case StackMapFrameInfo.ObjectVerificationTypeInfo o ->
                    o.className().asInternalName();
            case StackMapFrameInfo.UninitializedVerificationTypeInfo u ->
                    "uninitialized " + labels.get(u.newTarget());
            default -> t.toString();
        };
    }

    private String formatInstruction(
            Instruction insn, Map<Label, String> labels, Map<Integer, String> bsmNames) {
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
            case NewPrimitiveArrayInstruction n -> "newarray " + n.typeKind().name().toLowerCase(Locale.ROOT);
            case NewMultiArrayInstruction n ->
                    "multianewarray " + n.arrayType().asInternalName() + " " + n.dimensions();
            case TypeCheckInstruction t -> mnem + " " + t.type().asInternalName();
            case TableSwitchInstruction ts -> formatTableSwitch(ts, labels);
            case LookupSwitchInstruction ls -> formatLookupSwitch(ls, labels);
            case InvokeDynamicInstruction idi -> {
                int idx = idi.invokedynamic().bootstrap().bsmIndex();
                String bsm = bsmNames.getOrDefault(idx, "B" + idx);
                yield "invokedynamic " + idi.name().stringValue() + idi.type().stringValue()
                        + " " + bsm;
            }
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
        if (op.sizeIfFixed() == 1) {
            return mnem;
        }
        if (op == Opcode.ILOAD_W || op == Opcode.LLOAD_W || op == Opcode.FLOAD_W
                || op == Opcode.DLOAD_W || op == Opcode.ALOAD_W
                || op == Opcode.ISTORE_W || op == Opcode.LSTORE_W || op == Opcode.FSTORE_W
                || op == Opcode.DSTORE_W || op == Opcode.ASTORE_W) {
            String bare = op.name().toLowerCase(Locale.ROOT).replace("_w", "");
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
            if (!first) {
                sb.append(' ');
            }
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
            if (!first) {
                sb.append(", ");
            }
            sb.append(c.caseValue()).append(" -> ").append(labels.get(c.target()));
            first = false;
        }
        sb.append(" }");
        return sb.toString();
    }

    private static String mnemonicFor(Opcode op) {
        String name = op.name().toLowerCase(Locale.ROOT);
        if (InstructionDef.lookup(name).isPresent()) {
            return name;
        }
        return name;
    }

    private static void appendFlags(StringBuilder out, int flags, boolean isClass) {
        if ((flags & ClassFile.ACC_PUBLIC) != 0) {
            out.append(" public");
        }
        if ((flags & ClassFile.ACC_PRIVATE) != 0) {
            out.append(" private");
        }
        if ((flags & ClassFile.ACC_PROTECTED) != 0) {
            out.append(" protected");
        }
        if ((flags & ClassFile.ACC_STATIC) != 0) {
            out.append(" static");
        }
        if ((flags & ClassFile.ACC_FINAL) != 0) {
            out.append(" final");
        }
        if ((flags & ClassFile.ACC_INTERFACE) != 0) {
            out.append(" interface");
        }
        if ((flags & ClassFile.ACC_ABSTRACT) != 0) {
            out.append(" abstract");
        }
        if ((flags & ClassFile.ACC_SYNTHETIC) != 0) {
            out.append(" synthetic");
        }
        if ((flags & ClassFile.ACC_ENUM) != 0) {
            out.append(" enum");
        }
        if (!isClass) {
            if ((flags & ClassFile.ACC_SYNCHRONIZED) != 0) {
                out.append(" synchronized");
            }
            if ((flags & ClassFile.ACC_BRIDGE) != 0) {
                out.append(" bridge");
            }
            if ((flags & ClassFile.ACC_VARARGS) != 0) {
                out.append(" varargs");
            }
            if ((flags & ClassFile.ACC_NATIVE) != 0) {
                out.append(" native");
            }
            if ((flags & ClassFile.ACC_VOLATILE) != 0) {
                out.append(" volatile");
            }
            if ((flags & ClassFile.ACC_TRANSIENT) != 0) {
                out.append(" transient");
            }
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
    }
}
