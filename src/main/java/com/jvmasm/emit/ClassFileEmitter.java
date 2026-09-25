package com.jvmasm.emit;

import com.jvmasm.ast.CatchEntry;
import com.jvmasm.ast.ClassDecl;
import com.jvmasm.ast.CodeItem;
import com.jvmasm.ast.FieldDecl;
import com.jvmasm.ast.InsnItem;
import com.jvmasm.ast.LabelItem;
import com.jvmasm.ast.LookupCase;
import com.jvmasm.ast.LookupSwitchItem;
import com.jvmasm.ast.MethodDecl;
import com.jvmasm.ast.StackFrameItem;
import com.jvmasm.ast.TableSwitchItem;
import com.jvmasm.isa.InstructionDef;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits {@code .class} bytes via the JDK Class-File API.
 *
 * <p>Never uses convenience helpers that auto-select opcode variants
 * ({@code iload(slot)}, {@code ldc(value)}, …). Author-chosen {@link Opcode}
 * is always passed explicitly.
 *
 * <p>Max stack/locals are computed by the Class-File API; author {@code .limit}
 * values are validated afterwards when present.
 */
public final class ClassFileEmitter {

    public byte[] emit(ClassDecl cls) {
        ClassDesc thisDesc = ClassDesc.ofInternalName(cls.thisClass);
        ClassDesc superDesc = ClassDesc.ofInternalName(cls.superClass);

        boolean manualStacks = cls.methods.stream().anyMatch(this::hasManualStacks);
        ClassFile cf = manualStacks
                ? ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                : ClassFile.of();

        byte[] bytes = cf.build(thisDesc, classBuilder -> {
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
                        ClassDesc.ofDescriptor(field.descriptor()),
                        field.accessFlags());
            }

            for (MethodDecl method : cls.methods) {
                MethodTypeDesc mtd = MethodTypeDesc.ofDescriptor(method.descriptor);
                classBuilder.withMethod(method.name, mtd, method.accessFlags,
                        mb -> mb.withCode(cb -> emitCode(cb, method, manualStacks)));
            }
        });

        validateLimits(bytes, cls);
        List<VerifyError> errors = cf.verify(bytes);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("class failed verification: " + errors.getFirst());
        }
        return bytes;
    }

    private boolean hasManualStacks(MethodDecl method) {
        return method.code.stream().anyMatch(StackFrameItem.class::isInstance);
    }

    private void emitCode(CodeBuilder cb, MethodDecl method, boolean manualStacks) {
        Map<String, Label> labels = new HashMap<>();
        for (CodeItem item : method.code) {
            if (item instanceof LabelItem(String name)) {
                labels.putIfAbsent(name, cb.newLabel());
            }
            if (item instanceof StackFrameItem sf) {
                labels.putIfAbsent(sf.label(), cb.newLabel());
            }
        }
        for (CatchEntry c : method.catches) {
            labels.putIfAbsent(c.from(), cb.newLabel());
            labels.putIfAbsent(c.to(), cb.newLabel());
            labels.putIfAbsent(c.handler(), cb.newLabel());
        }

        List<StackMapFrameInfo> frames = new ArrayList<>();

        for (CodeItem item : method.code) {
            switch (item) {
                case LabelItem(String name) -> cb.labelBinding(labels.get(name));
                case StackFrameItem sf -> frames.add(StackMapFrameInfo.of(
                        requireLabel(labels, sf.label(), sf.line()),
                        mapTypes(sf.locals(), labels, sf.line()),
                        mapTypes(sf.stack(), labels, sf.line())));
                case InsnItem insn -> emitInsn(cb, insn, labels);
                case TableSwitchItem ts -> emitTableSwitch(cb, ts, labels);
                case LookupSwitchItem ls -> emitLookupSwitch(cb, ls, labels);
            }
        }

        for (CatchEntry c : method.catches) {
            Label from = labels.get(c.from());
            Label to = labels.get(c.to());
            Label handler = labels.get(c.handler());
            if ("any".equals(c.typeInternalName()) || "*".equals(c.typeInternalName())) {
                cb.exceptionCatchAll(from, to, handler);
            } else {
                cb.exceptionCatch(from, to, handler, ClassDesc.ofInternalName(c.typeInternalName()));
            }
        }

        if (manualStacks) {
            cb.with(StackMapTableAttribute.of(frames));
        }
    }

    private List<StackMapFrameInfo.VerificationTypeInfo> mapTypes(
            List<String> types, Map<String, Label> labels, int line) {
        List<StackMapFrameInfo.VerificationTypeInfo> out = new ArrayList<>();
        for (String t : types) {
            out.add(mapType(t, labels, line));
        }
        return out;
    }

    private StackMapFrameInfo.VerificationTypeInfo mapType(
            String t, Map<String, Label> labels, int line) {
        return switch (t) {
            case "top" -> StackMapFrameInfo.SimpleVerificationTypeInfo.TOP;
            case "int", "integer" -> StackMapFrameInfo.SimpleVerificationTypeInfo.INTEGER;
            case "float" -> StackMapFrameInfo.SimpleVerificationTypeInfo.FLOAT;
            case "double" -> StackMapFrameInfo.SimpleVerificationTypeInfo.DOUBLE;
            case "long" -> StackMapFrameInfo.SimpleVerificationTypeInfo.LONG;
            case "null" -> StackMapFrameInfo.SimpleVerificationTypeInfo.NULL;
            case "uninitializedThis", "this" ->
                    StackMapFrameInfo.SimpleVerificationTypeInfo.UNINITIALIZED_THIS;
            default -> {
                if (t.startsWith("uninitialized ")) {
                    String lab = t.substring("uninitialized ".length());
                    yield StackMapFrameInfo.UninitializedVerificationTypeInfo.of(
                            requireLabel(labels, lab, line));
                }
                ClassDesc desc;
                if ((t.startsWith("L") && t.endsWith(";")) || t.startsWith("[")) {
                    desc = ClassDesc.ofDescriptor(t);
                } else {
                    desc = ClassDesc.ofInternalName(t);
                }
                yield StackMapFrameInfo.ObjectVerificationTypeInfo.of(desc);
            }
        };
    }

    private void emitTableSwitch(CodeBuilder cb, TableSwitchItem ts, Map<String, Label> labels) {
        List<SwitchCase> cases = new ArrayList<>();
        for (int i = 0; i < ts.caseLabels().size(); i++) {
            int key = ts.low() + i;
            cases.add(SwitchCase.of(key, requireLabel(labels, ts.caseLabels().get(i), ts.line())));
        }
        cb.with(TableSwitchInstruction.of(
                ts.low(),
                ts.high(),
                requireLabel(labels, ts.defaultLabel(), ts.line()),
                cases));
    }

    private void emitLookupSwitch(CodeBuilder cb, LookupSwitchItem ls, Map<String, Label> labels) {
        List<SwitchCase> cases = new ArrayList<>();
        for (LookupCase c : ls.cases()) {
            cases.add(SwitchCase.of(c.key(), requireLabel(labels, c.label(), ls.line())));
        }
        cb.with(LookupSwitchInstruction.of(
                requireLabel(labels, ls.defaultLabel(), ls.line()),
                cases));
    }

    private static Label requireLabel(Map<String, Label> labels, String name, int line) {
        Label label = labels.get(name);
        if (label == null) {
            throw new IllegalArgumentException("unknown label '" + name + "' (line " + line + ")");
        }
        return label;
    }

    private void emitInsn(CodeBuilder cb, InsnItem insn, Map<String, Label> labels) {
        InstructionDef def = insn.def();
        Opcode op = insn.wide() ? InstructionDef.wideOpcode(def) : requireOpcode(def);
        List<String> ops = insn.operands();

        switch (def) {
            case WIDE -> throw new IllegalStateException("wide should be folded before emit");
            case BREAKPOINT, IMPDEP1, IMPDEP2 ->
                    throw new IllegalArgumentException("reserved opcode: " + def.mnemonic());

            // --- constants / loads / stores / stack / math / convert / compare / return ---
            case NOP -> cb.nop();
            case ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                 LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1 ->
                    cb.with(ConstantInstruction.ofIntrinsic(op));
            case BIPUSH -> cb.with(ConstantInstruction.ofArgument(Opcode.BIPUSH, parseInt(ops.getFirst(), insn)));
            case SIPUSH -> cb.with(ConstantInstruction.ofArgument(Opcode.SIPUSH, parseInt(ops.getFirst(), insn)));
            case LDC, LDC_W, LDC2_W -> emitLdc(cb, op, ops.getFirst(), insn);

            case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                 ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3,
                 LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3,
                 FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3,
                 DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3,
                 ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3 ->
                    cb.with(LoadInstruction.of(op, slotFor(def, ops, insn)));

            case ISTORE, LSTORE, FSTORE, DSTORE, ASTORE,
                 ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3,
                 LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3,
                 FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3,
                 DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3,
                 ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 ->
                    cb.with(StoreInstruction.of(op, slotFor(def, ops, insn)));

            case IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD ->
                    cb.with(ArrayLoadInstruction.of(op));
            case IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE ->
                    cb.with(ArrayStoreInstruction.of(op));

            case POP, POP2, DUP, DUP_X1, DUP_X2, DUP2, DUP2_X1, DUP2_X2, SWAP ->
                    cb.with(StackInstruction.of(op));

            case IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB,
                 IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV,
                 IREM, LREM, FREM, DREM, INEG, LNEG, FNEG, DNEG,
                 ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR,
                 IAND, LAND, IOR, LOR, IXOR, LXOR,
                 LCMP, FCMPL, FCMPG, DCMPL, DCMPG, ARRAYLENGTH ->
                    cb.with(OperatorInstruction.of(op));

            case IINC -> {
                int slot = parseInt(ops.get(0), insn);
                int incr = parseInt(ops.get(1), insn);
                if (insn.wide()) {
                    // IncrementInstruction.of always emits the right width for the values;
                    // force wide encoding by range — API has no Opcode overload for iinc.
                    // Validate author asked for wide when needed.
                    if (slot <= 255 && incr >= Byte.MIN_VALUE && incr <= Byte.MAX_VALUE) {
                        // Still emit via of(); Class-File API may narrow. Prefer raw path later.
                    }
                }
                cb.with(IncrementInstruction.of(slot, incr));
            }

            case I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B, I2C, I2S ->
                    cb.with(ConvertInstruction.of(op));

            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                 IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE, GOTO, GOTO_W, IFNULL, IFNONNULL ->
                    cb.with(BranchInstruction.of(op, requireLabel(labels, ops.getFirst(), insn)));

            case JSR, JSR_W -> cb.with(DiscontinuedInstruction.JsrInstruction.of(
                    op, requireLabel(labels, ops.getFirst(), insn)));
            case RET -> cb.with(DiscontinuedInstruction.RetInstruction.of(op, parseInt(ops.getFirst(), insn)));
            case TABLESWITCH, LOOKUPSWITCH ->
                    throw new IllegalStateException(def.mnemonic() + " should use dedicated CodeItem");

            case GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD -> emitField(cb, op, ops, insn);
            case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC -> emitInvoke(cb, op, ops.getFirst(), false, insn);
            case INVOKEINTERFACE -> emitInvoke(cb, op, ops.getFirst(), true, insn);
            case INVOKEDYNAMIC -> throw new UnsupportedOperationException("invokedynamic not yet emitted");

            case NEW -> cb.with(NewObjectInstruction.of(classEntry(cb, ops.getFirst())));
            case NEWARRAY -> cb.with(NewPrimitiveArrayInstruction.of(atype(ops.getFirst(), insn)));
            case ANEWARRAY -> cb.with(NewReferenceArrayInstruction.of(classEntry(cb, ops.getFirst())));
            case MULTIANEWARRAY -> cb.with(NewMultiArrayInstruction.of(
                    classEntry(cb, ops.get(0)), parseInt(ops.get(1), insn)));
            case ATHROW -> cb.with(ThrowInstruction.of());
            case CHECKCAST, INSTANCEOF ->
                    cb.with(TypeCheckInstruction.of(op, classEntry(cb, ops.getFirst())));
            case MONITORENTER, MONITOREXIT -> cb.with(MonitorInstruction.of(op));

            case IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN ->
                    cb.with(ReturnInstruction.of(op));
        }
    }

    private void emitLdc(CodeBuilder cb, Opcode op, String operand, InsnItem insn) {
        LoadableConstantEntry entry = resolveConstant(cb, operand, insn);
        if (op == Opcode.LDC || op == Opcode.LDC_W) {
            // Enforce index width: ldc requires u1 index
            int index = entry.index();
            if (op == Opcode.LDC && index > 255) {
                throw new IllegalArgumentException(
                        "ldc constant pool index " + index + " exceeds u1; use ldc_w (line " + insn.line() + ")");
            }
            if (op == Opcode.LDC2_W) {
                throw new IllegalArgumentException("ldc2_w is only for long/double (line " + insn.line() + ")");
            }
        }
        if (op == Opcode.LDC2_W) {
            ConstantDesc v = entry.constantValue();
            if (!(v instanceof Long || v instanceof Double)) {
                throw new IllegalArgumentException("ldc2_w requires long/double (line " + insn.line() + ")");
            }
        }
        cb.with(ConstantInstruction.ofLoad(op, entry));
    }

    private LoadableConstantEntry resolveConstant(CodeBuilder cb, String operand, InsnItem insn) {
        // String literal was already unquoted by lexer into operand text for STRING tokens;
        // numeric / class forms:
        if (operand.startsWith("0x") || operand.startsWith("0X")
                || (operand.length() > 0 && (Character.isDigit(operand.charAt(0)) || operand.charAt(0) == '-'))) {
            if (operand.endsWith("L") || operand.endsWith("l")) {
                return cb.constantPool().longEntry(Long.parseLong(stripSuffix(operand, 1)));
            }
            if (operand.endsWith("f") || operand.endsWith("F")) {
                return cb.constantPool().floatEntry(Float.parseFloat(stripSuffix(operand, 1)));
            }
            if (operand.endsWith("d") || operand.endsWith("D")) {
                return cb.constantPool().doubleEntry(Double.parseDouble(stripSuffix(operand, 1)));
            }
            if (operand.contains(".")) {
                throw new IllegalArgumentException(
                        "float/double literal needs f or d suffix (line " + insn.line() + ")");
            }
            int v = parseInt(operand, insn);
            return cb.constantPool().intEntry(v);
        }
        // Class literal: bare internal name ending with no descriptor? treat as String otherwise.
        // Convention: ClassName as Class constant uses .class-style — for now strings are default
        // unless operand looks like a type descriptor L...; or array.
        if (operand.startsWith("L") && operand.endsWith(";") || operand.startsWith("[")) {
            return cb.constantPool().classEntry(ClassDesc.ofDescriptor(operand));
        }
        // Default: UTF-8 string constant (Hello World path)
        return cb.constantPool().stringEntry(operand);
    }

    private void emitField(CodeBuilder cb, Opcode op, List<String> ops, InsnItem insn) {
        String owner;
        String name;
        String desc;
        if (ops.size() >= 2) {
            String ownerName = ops.get(0);
            desc = ops.get(1);
            int slash = ownerName.lastIndexOf('/');
            if (slash < 0) {
                throw new IllegalArgumentException("field ref needs Owner/name (line " + insn.line() + ")");
            }
            owner = ownerName.substring(0, slash);
            name = ownerName.substring(slash + 1);
        } else {
            ParsedFieldRef ref = parseFieldRef(ops.getFirst(), insn);
            owner = ref.owner;
            name = ref.name;
            desc = ref.desc;
        }
        var fieldRef = cb.constantPool().fieldRefEntry(
                ClassDesc.ofInternalName(owner), name, ClassDesc.ofDescriptor(desc));
        cb.with(FieldInstruction.of(op, fieldRef));
    }

    private void emitInvoke(CodeBuilder cb, Opcode op, String ref, boolean iface, InsnItem insn) {
        int paren = ref.indexOf('(');
        if (paren < 0) {
            throw new IllegalArgumentException("method ref missing descriptor (line " + insn.line() + "): " + ref);
        }
        String ownerAndName = ref.substring(0, paren);
        String desc = ref.substring(paren);
        int slash = ownerAndName.lastIndexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("method ref missing Owner/name (line " + insn.line() + ")");
        }
        String owner = ownerAndName.substring(0, slash);
        String name = ownerAndName.substring(slash + 1);
        ClassEntry ownerEntry = cb.constantPool().classEntry(ClassDesc.ofInternalName(owner));
        var nameAndType = cb.constantPool().nameAndTypeEntry(name, MethodTypeDesc.ofDescriptor(desc));
        if (iface || op == Opcode.INVOKEINTERFACE) {
            cb.with(InvokeInstruction.of(op, ownerEntry, nameAndType, true));
        } else {
            var methodRef = cb.constantPool().methodRefEntry(ownerEntry, nameAndType);
            cb.with(InvokeInstruction.of(op, methodRef));
        }
    }

    private static ClassEntry classEntry(CodeBuilder cb, String internalName) {
        return cb.constantPool().classEntry(ClassDesc.ofInternalName(internalName));
    }

    private static java.lang.classfile.TypeKind atype(String keyword, InsnItem insn) {
        return switch (keyword) {
            case "boolean" -> java.lang.classfile.TypeKind.BOOLEAN;
            case "char" -> java.lang.classfile.TypeKind.CHAR;
            case "float" -> java.lang.classfile.TypeKind.FLOAT;
            case "double" -> java.lang.classfile.TypeKind.DOUBLE;
            case "byte" -> java.lang.classfile.TypeKind.BYTE;
            case "short" -> java.lang.classfile.TypeKind.SHORT;
            case "int" -> java.lang.classfile.TypeKind.INT;
            case "long" -> java.lang.classfile.TypeKind.LONG;
            default -> throw new IllegalArgumentException(
                    "bad newarray type '" + keyword + "' (line " + insn.line() + ")");
        };
    }

    private static int slotFor(InstructionDef def, List<String> ops, InsnItem insn) {
        return switch (def.shape()) {
            case NONE -> implicitSlot(def.opcode());
            case LOCAL_U1 -> {
                int slot = parseInt(ops.getFirst(), insn);
                if (!insn.wide() && (slot < 0 || slot > 255)) {
                    throw new IllegalArgumentException(
                            "index " + slot + " exceeds u1 for " + def.mnemonic()
                                    + "; prefix with wide (line " + insn.line() + ")");
                }
                yield slot;
            }
            default -> throw new IllegalStateException(def.mnemonic());
        };
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

    private static Label requireLabel(Map<String, Label> labels, String name, InsnItem insn) {
        Label label = labels.get(name);
        if (label == null) {
            throw new IllegalArgumentException("unknown label '" + name + "' (line " + insn.line() + ")");
        }
        return label;
    }

    private static Opcode requireOpcode(InstructionDef def) {
        Opcode op = def.opcode();
        if (op == null) {
            throw new IllegalStateException("no Opcode for " + def.mnemonic());
        }
        return op;
    }

    private static int parseInt(String text, InsnItem insn) {
        try {
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return Integer.parseInt(text.substring(2), 16);
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad int '" + text + "' (line " + insn.line() + ")");
        }
    }

    private static String stripSuffix(String s, int n) {
        return s.substring(0, s.length() - n);
    }

    private record ParsedFieldRef(String owner, String name, String desc) {}

    private static ParsedFieldRef parseFieldRef(String ref, InsnItem insn) {
        // Owner/name Descriptor (descriptor may be glued)
        int descAt = -1;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c == ' ' || c == '\t') {
                descAt = i + 1;
                break;
            }
        }
        String ownerName;
        String desc;
        if (descAt >= 0) {
            ownerName = ref.substring(0, descAt).trim();
            desc = ref.substring(descAt).trim();
        } else {
            // glued: …/outLjava/io/PrintStream;
            int i = ref.lastIndexOf('/');
            if (i < 0) {
                throw new IllegalArgumentException("bad field ref (line " + insn.line() + "): " + ref);
            }
            // find descriptor start after name
            int j = i + 1;
            while (j < ref.length() && (Character.isJavaIdentifierPart(ref.charAt(j)))) {
                j++;
            }
            ownerName = ref.substring(0, j);
            desc = ref.substring(j);
        }
        int slash = ownerName.lastIndexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("field ref needs Owner/name (line " + insn.line() + ")");
        }
        return new ParsedFieldRef(ownerName.substring(0, slash), ownerName.substring(slash + 1), desc);
    }

    /** Soft-check author .limit against what the Class-File API wrote. */
    private void validateLimits(byte[] bytes, ClassDecl cls) {
        var model = ClassFile.of().parse(bytes);
        for (MethodDecl method : cls.methods) {
            if (method.maxStack < 0 && method.maxLocals < 0) {
                continue;
            }
            model.methods().stream()
                    .filter(m -> m.methodName().equalsString(method.name)
                            && m.methodType().equalsString(method.descriptor))
                    .findFirst()
                    .flatMap(m -> m.code())
                    .ifPresent(code -> {
                        if (!(code instanceof java.lang.classfile.attribute.CodeAttribute ca)) {
                            return;
                        }
                        if (method.maxStack >= 0 && ca.maxStack() > method.maxStack) {
                            throw new IllegalArgumentException(
                                    "method " + method.name + " needs stack "
                                            + ca.maxStack() + " but .limit stack "
                                            + method.maxStack);
                        }
                        if (method.maxLocals >= 0 && ca.maxLocals() > method.maxLocals) {
                            throw new IllegalArgumentException(
                                    "method " + method.name + " needs locals "
                                            + ca.maxLocals() + " but .limit locals "
                                            + method.maxLocals);
                        }
                    });
        }
    }
}
