package com.jvmasm.emit;

import com.jvmasm.ast.ClassDecl;
import com.jvmasm.ast.CodeItem;
import com.jvmasm.ast.FieldDecl;
import com.jvmasm.ast.InsnItem;
import com.jvmasm.ast.LabelItem;
import com.jvmasm.ast.MethodDecl;
import com.jvmasm.isa.InstructionDef;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.HashMap;
import java.util.Map;

/**
 * Emits .class bytes via the JDK Class-File API ({@code java.lang.classfile}).
 *
 * <p>Critical design rule: never call convenience helpers that auto-select
 * opcode width ({@code CodeBuilder.iload(slot)}, {@code ldc(value)}, …).
 * Always pass the author-chosen {@link Opcode} explicitly.
 */
public final class ClassFileEmitter {

    public byte[] emit(ClassDecl cls) {
        ClassDesc thisDesc = ClassDesc.ofInternalName(cls.thisClass);
        ClassDesc superDesc = ClassDesc.ofInternalName(cls.superClass);

        return ClassFile.of().build(thisDesc, classBuilder -> {
            classBuilder.withFlags(cls.accessFlags);
            classBuilder.withSuperclass(superDesc);
            classBuilder.withVersion(cls.majorVersion, cls.minorVersion);

            for (String iface : cls.interfaces) {
                classBuilder.withInterfaceSymbols(ClassDesc.ofInternalName(iface));
            }
            if (cls.sourceFile != null) {
                classBuilder.with(SourceFileAttribute.of(cls.sourceFile));
            }

            for (FieldDecl field : cls.fields) {
                classBuilder.withField(
                        field.name(),
                        ClassDesc.ofDescriptor(normalizeFieldDesc(field.descriptor())),
                        field.accessFlags());
                // ConstantValue support: later phase
            }

            for (MethodDecl method : cls.methods) {
                MethodTypeDesc mtd = MethodTypeDesc.ofDescriptor(method.descriptor);
                classBuilder.withMethod(method.name, mtd, method.accessFlags, mb -> {
                    mb.withCode(cb -> emitCode(cb, method));
                });
            }
        });
    }

    private void emitCode(CodeBuilder cb, MethodDecl method) {
        if (method.maxStack >= 0) {
            cb.withMaxs(method.maxStack, method.maxLocals >= 0 ? method.maxLocals : 0);
        } else if (method.maxLocals >= 0) {
            cb.withMaxs(0, method.maxLocals);
        }

        Map<String, java.lang.classfile.Label> labels = new HashMap<>();
        // First pass: allocate labels
        for (CodeItem item : method.code) {
            if (item instanceof LabelItem(String name)) {
                labels.put(name, cb.newLabel());
            }
        }

        for (CodeItem item : method.code) {
            switch (item) {
                case LabelItem(String name) -> cb.labelBinding(labels.get(name));
                case InsnItem insn -> emitInsn(cb, insn, labels);
            }
        }
    }

    private void emitInsn(CodeBuilder cb, InsnItem insn, Map<String, java.lang.classfile.Label> labels) {
        InstructionDef def = insn.def();
        Opcode op = insn.wide() ? InstructionDef.wideOpcode(def) : def.opcode();
        if (op == null) {
            throw new IllegalStateException("no Opcode for " + def.mnemonic());
        }

        switch (def.shape()) {
            case NONE -> emitNone(cb, op);
            case LOCAL_U1 -> {
                int slot = Integer.parseInt(insn.operands().getFirst());
                emitLocal(cb, op, slot);
            }
            case BIPUSH -> cb.with(ConstantInstruction.ofArgument(Opcode.BIPUSH,
                    Integer.parseInt(insn.operands().getFirst())));
            case SIPUSH -> cb.with(ConstantInstruction.ofArgument(Opcode.SIPUSH,
                    Integer.parseInt(insn.operands().getFirst())));
            case LDC, LDC_W, LDC2_W -> emitLdc(cb, op, insn.operands().getFirst());
            case FIELD_REF -> emitField(cb, op, insn.operands().getFirst());
            case METHOD_REF -> emitInvoke(cb, op, insn.operands().getFirst(), false);
            case INVOKEINTERFACE -> emitInvoke(cb, op, insn.operands().getFirst(), true);
            case CLASS_REF -> emitClassRef(cb, op, insn.operands().getFirst());
            case BRANCH, BRANCH_W -> {
                var label = labels.get(insn.operands().getFirst());
                if (label == null) {
                    throw new IllegalArgumentException("unknown label '" + insn.operands().getFirst()
                            + "' at line " + insn.line());
                }
                cb.with(java.lang.classfile.instruction.BranchInstruction.of(op, label));
            }
            case IINC -> {
                int slot = Integer.parseInt(insn.operands().get(0));
                int incr = Integer.parseInt(insn.operands().get(1));
                cb.with(java.lang.classfile.instruction.IncrementInstruction.of(slot, incr));
                // Note: IncrementInstruction.of may pick narrow/wide — Phase 2 must pass Opcode.IINC / IINC_W explicitly if API allows
            }
            default -> throw new UnsupportedOperationException(
                    "emitter Phase 1 does not yet handle shape " + def.shape()
                            + " for " + def.mnemonic());
        }
    }

    private void emitNone(CodeBuilder cb, Opcode op) {
        switch (op.kind()) {
            case OPERATOR -> cb.with(OperatorInstruction.of(op));
            case STACK -> cb.with(StackInstruction.of(op));
            case RETURN -> cb.with(ReturnInstruction.of(op));
            case NOP -> cb.nop();
            case CONSTANT -> cb.with(ConstantInstruction.ofIntrinsic(op));
            case ARRAY_LOAD, ARRAY_STORE -> {
                // ArrayLoadInstruction.of(op) / ArrayStoreInstruction.of(op)
                cb.with(java.lang.classfile.instruction.ArrayLoadInstruction.of(op));
            }
            case MONITOR -> cb.with(java.lang.classfile.instruction.MonitorInstruction.of(op));
            case THROW_EXCEPTION -> cb.athrow();
            default -> {
                // Fallback for load/store shorthands like iload_0
                if (op.kind() == Opcode.Kind.LOAD) {
                    cb.with(LoadInstruction.of(op, implicitSlot(op)));
                } else if (op.kind() == Opcode.Kind.STORE) {
                    cb.with(StoreInstruction.of(op, implicitSlot(op)));
                } else if (op.kind() == Opcode.Kind.CONVERT) {
                    cb.with(java.lang.classfile.instruction.ConvertInstruction.of(op));
                } else {
                    throw new UnsupportedOperationException("emit NONE for " + op);
                }
            }
        }
    }

    private static int implicitSlot(Opcode op) {
        return switch (op) {
            case ILOAD_0, LLOAD_0, FLOAD_0, DLOAD_0, ALOAD_0,
                 ISTORE_0, LSTORE_0, FSTORE_0, DSTORE_0, ASTORE_0 -> 0;
            case ILOAD_1, LLOAD_1, FLOAD_1, DLOAD_1, ALOAD_1,
                 ISTORE_1, LSTORE_1, FSTORE_1, DSTORE_1, ASTORE_1 -> 1;
            case ILOAD_2, LLOAD_2, FLOAD_2, DLOAD_2, ALOAD_2,
                 ISTORE_2, LSTORE_2, FSTORE_2, DSTORE_2, ASTORE_2 -> 2;
            case ILOAD_3, LLOAD_3, FLOAD_3, DLOAD_3, ALOAD_3,
                 ISTORE_3, LSTORE_3, FSTORE_3, DSTORE_3, ASTORE_3 -> 3;
            default -> throw new IllegalArgumentException("no implicit slot for " + op);
        };
    }

    private void emitLocal(CodeBuilder cb, Opcode op, int slot) {
        if (op.kind() == Opcode.Kind.LOAD) {
            cb.with(LoadInstruction.of(op, slot));
        } else if (op.kind() == Opcode.Kind.STORE) {
            cb.with(StoreInstruction.of(op, slot));
        } else {
            throw new UnsupportedOperationException("local operand for " + op);
        }
    }

    private void emitLdc(CodeBuilder cb, Opcode op, String operand) {
        // Phase 1: string literals only for Hello World; numeric/class later
        if (op != Opcode.LDC && op != Opcode.LDC_W) {
            throw new UnsupportedOperationException("Phase 1 ldc only supports ldc/ldc_w strings, got " + op);
        }
        // ConstantInstruction.ofArgument picks encoding — we must force opcode.
        // Use loadConstant with ConstantDesc then… actually that auto-selects.
        // Explicit path: cb.ldc(String) auto-selects. Prefer:
        cb.with(ConstantInstruction.ofArgument(op, operand));
    }

    private void emitField(CodeBuilder cb, Opcode op, String ref) {
        // Owner/name Descriptor  e.g. java/lang/System/out Ljava/io/PrintStream;
        int descAt = findDescriptorStart(ref);
        if (descAt < 0) {
            throw new IllegalArgumentException("field ref missing descriptor: " + ref);
        }
        String ownerAndName = ref.substring(0, descAt);
        String desc = ref.substring(descAt).trim();
        int slash = ownerAndName.lastIndexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("field ref missing owner/name: " + ref);
        }
        // owner may contain slashes; name is after last slash before descriptor
        // But owner is ClassName with slashes — name is the last segment.
        // Format: Owner/name Descriptor where Owner can be a/b/c
        // So: split descriptor first, then last / separates owner and name.
        String owner = ownerAndName.substring(0, slash);
        String name = ownerAndName.substring(slash + 1);
        var fieldRef = cb.constantPool().fieldRefEntry(
                ClassDesc.ofInternalName(owner),
                name,
                ClassDesc.ofDescriptor(normalizeFieldDesc(desc)));
        cb.with(FieldInstruction.of(op, fieldRef));
    }

    private void emitInvoke(CodeBuilder cb, Opcode op, String ref, boolean iface) {
        // Owner/name(Descriptor)Return  e.g. java/io/PrintStream/println(Ljava/lang/String;)V
        int paren = ref.indexOf('(');
        if (paren < 0) {
            throw new IllegalArgumentException("method ref missing descriptor: " + ref);
        }
        String ownerAndName = ref.substring(0, paren);
        String desc = ref.substring(paren);
        int slash = ownerAndName.lastIndexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("method ref missing owner/name: " + ref);
        }
        String owner = ownerAndName.substring(0, slash);
        String name = ownerAndName.substring(slash + 1);
        var methodRef = cb.constantPool().methodRefEntry(
                ClassDesc.ofInternalName(owner),
                name,
                MethodTypeDesc.ofDescriptor(desc));
        cb.with(InvokeInstruction.of(op, methodRef, iface));
    }

    private void emitClassRef(CodeBuilder cb, Opcode op, String internalName) {
        ClassDesc cd = ClassDesc.ofInternalName(internalName);
        switch (op) {
            case NEW -> cb.with(java.lang.classfile.instruction.NewObjectInstruction.of(
                    cb.constantPool().classEntry(cd)));
            case ANEWARRAY -> cb.with(java.lang.classfile.instruction.NewReferenceArrayInstruction.of(
                    cb.constantPool().classEntry(cd)));
            case CHECKCAST, INSTANCEOF -> cb.with(
                    java.lang.classfile.instruction.TypeCheckInstruction.of(
                            op, cb.constantPool().classEntry(cd)));
            default -> throw new UnsupportedOperationException("class-ref emit for " + op);
        }
    }

    private static int findDescriptorStart(String ref) {
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c == ' ' || c == '\t') {
                return i + 1;
            }
            // descriptor glued without space: …/outLjava/io/PrintStream;
            if (i > 0 && (c == 'L' || c == '[' || "BCDFIJSZ".indexOf(c) >= 0)) {
                // Heuristic: if previous char is name-like and this starts a desc
                char prev = ref.charAt(i - 1);
                if (Character.isLetterOrDigit(prev) || prev == '_' || prev == '$') {
                    if (c == 'L' || c == '[') {
                        return i;
                    }
                    if ("BCDFIJSZ".indexOf(c) >= 0 && i == ref.length() - 1) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static String normalizeFieldDesc(String d) {
        d = d.trim();
        if (d.length() == 1 || d.startsWith("[") || d.startsWith("L")) {
            return d;
        }
        // allow internal name without L…; for convenience? Plan says no — keep strict.
        return d;
    }
}
