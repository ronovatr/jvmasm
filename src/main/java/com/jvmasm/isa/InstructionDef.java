package com.jvmasm.isa;

import java.lang.classfile.Opcode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Single source of truth for every JVM mnemonic this assembler accepts.
 * Mnemonic strings are spelled exactly as in JVMS chapter 6 (lowercase).
 *
 * <p>Each row maps 1:1 to a {@link Opcode} used by the Class-File API emitter.
 * Wide forms are <em>not</em> separate mnemonics here — authors write
 * {@code wide iload N}, and the emitter selects {@code ILOAD_W} etc.
 */
public enum InstructionDef {

    // --- Constants ---
    NOP("nop", Opcode.NOP, OperandShape.NONE),
    ACONST_NULL("aconst_null", Opcode.ACONST_NULL, OperandShape.NONE),
    ICONST_M1("iconst_m1", Opcode.ICONST_M1, OperandShape.NONE),
    ICONST_0("iconst_0", Opcode.ICONST_0, OperandShape.NONE),
    ICONST_1("iconst_1", Opcode.ICONST_1, OperandShape.NONE),
    ICONST_2("iconst_2", Opcode.ICONST_2, OperandShape.NONE),
    ICONST_3("iconst_3", Opcode.ICONST_3, OperandShape.NONE),
    ICONST_4("iconst_4", Opcode.ICONST_4, OperandShape.NONE),
    ICONST_5("iconst_5", Opcode.ICONST_5, OperandShape.NONE),
    LCONST_0("lconst_0", Opcode.LCONST_0, OperandShape.NONE),
    LCONST_1("lconst_1", Opcode.LCONST_1, OperandShape.NONE),
    FCONST_0("fconst_0", Opcode.FCONST_0, OperandShape.NONE),
    FCONST_1("fconst_1", Opcode.FCONST_1, OperandShape.NONE),
    FCONST_2("fconst_2", Opcode.FCONST_2, OperandShape.NONE),
    DCONST_0("dconst_0", Opcode.DCONST_0, OperandShape.NONE),
    DCONST_1("dconst_1", Opcode.DCONST_1, OperandShape.NONE),
    BIPUSH("bipush", Opcode.BIPUSH, OperandShape.BIPUSH),
    SIPUSH("sipush", Opcode.SIPUSH, OperandShape.SIPUSH),
    LDC("ldc", Opcode.LDC, OperandShape.LDC),
    LDC_W("ldc_w", Opcode.LDC_W, OperandShape.LDC_W),
    LDC2_W("ldc2_w", Opcode.LDC2_W, OperandShape.LDC2_W),

    // --- Loads ---
    ILOAD("iload", Opcode.ILOAD, OperandShape.LOCAL_U1),
    ILOAD_0("iload_0", Opcode.ILOAD_0, OperandShape.NONE),
    ILOAD_1("iload_1", Opcode.ILOAD_1, OperandShape.NONE),
    ILOAD_2("iload_2", Opcode.ILOAD_2, OperandShape.NONE),
    ILOAD_3("iload_3", Opcode.ILOAD_3, OperandShape.NONE),
    LLOAD("lload", Opcode.LLOAD, OperandShape.LOCAL_U1),
    LLOAD_0("lload_0", Opcode.LLOAD_0, OperandShape.NONE),
    LLOAD_1("lload_1", Opcode.LLOAD_1, OperandShape.NONE),
    LLOAD_2("lload_2", Opcode.LLOAD_2, OperandShape.NONE),
    LLOAD_3("lload_3", Opcode.LLOAD_3, OperandShape.NONE),
    FLOAD("fload", Opcode.FLOAD, OperandShape.LOCAL_U1),
    FLOAD_0("fload_0", Opcode.FLOAD_0, OperandShape.NONE),
    FLOAD_1("fload_1", Opcode.FLOAD_1, OperandShape.NONE),
    FLOAD_2("fload_2", Opcode.FLOAD_2, OperandShape.NONE),
    FLOAD_3("fload_3", Opcode.FLOAD_3, OperandShape.NONE),
    DLOAD("dload", Opcode.DLOAD, OperandShape.LOCAL_U1),
    DLOAD_0("dload_0", Opcode.DLOAD_0, OperandShape.NONE),
    DLOAD_1("dload_1", Opcode.DLOAD_1, OperandShape.NONE),
    DLOAD_2("dload_2", Opcode.DLOAD_2, OperandShape.NONE),
    DLOAD_3("dload_3", Opcode.DLOAD_3, OperandShape.NONE),
    ALOAD("aload", Opcode.ALOAD, OperandShape.LOCAL_U1),
    ALOAD_0("aload_0", Opcode.ALOAD_0, OperandShape.NONE),
    ALOAD_1("aload_1", Opcode.ALOAD_1, OperandShape.NONE),
    ALOAD_2("aload_2", Opcode.ALOAD_2, OperandShape.NONE),
    ALOAD_3("aload_3", Opcode.ALOAD_3, OperandShape.NONE),
    IALOAD("iaload", Opcode.IALOAD, OperandShape.NONE),
    LALOAD("laload", Opcode.LALOAD, OperandShape.NONE),
    FALOAD("faload", Opcode.FALOAD, OperandShape.NONE),
    DALOAD("daload", Opcode.DALOAD, OperandShape.NONE),
    AALOAD("aaload", Opcode.AALOAD, OperandShape.NONE),
    BALOAD("baload", Opcode.BALOAD, OperandShape.NONE),
    CALOAD("caload", Opcode.CALOAD, OperandShape.NONE),
    SALOAD("saload", Opcode.SALOAD, OperandShape.NONE),

    // --- Stores ---
    ISTORE("istore", Opcode.ISTORE, OperandShape.LOCAL_U1),
    ISTORE_0("istore_0", Opcode.ISTORE_0, OperandShape.NONE),
    ISTORE_1("istore_1", Opcode.ISTORE_1, OperandShape.NONE),
    ISTORE_2("istore_2", Opcode.ISTORE_2, OperandShape.NONE),
    ISTORE_3("istore_3", Opcode.ISTORE_3, OperandShape.NONE),
    LSTORE("lstore", Opcode.LSTORE, OperandShape.LOCAL_U1),
    LSTORE_0("lstore_0", Opcode.LSTORE_0, OperandShape.NONE),
    LSTORE_1("lstore_1", Opcode.LSTORE_1, OperandShape.NONE),
    LSTORE_2("lstore_2", Opcode.LSTORE_2, OperandShape.NONE),
    LSTORE_3("lstore_3", Opcode.LSTORE_3, OperandShape.NONE),
    FSTORE("fstore", Opcode.FSTORE, OperandShape.LOCAL_U1),
    FSTORE_0("fstore_0", Opcode.FSTORE_0, OperandShape.NONE),
    FSTORE_1("fstore_1", Opcode.FSTORE_1, OperandShape.NONE),
    FSTORE_2("fstore_2", Opcode.FSTORE_2, OperandShape.NONE),
    FSTORE_3("fstore_3", Opcode.FSTORE_3, OperandShape.NONE),
    DSTORE("dstore", Opcode.DSTORE, OperandShape.LOCAL_U1),
    DSTORE_0("dstore_0", Opcode.DSTORE_0, OperandShape.NONE),
    DSTORE_1("dstore_1", Opcode.DSTORE_1, OperandShape.NONE),
    DSTORE_2("dstore_2", Opcode.DSTORE_2, OperandShape.NONE),
    DSTORE_3("dstore_3", Opcode.DSTORE_3, OperandShape.NONE),
    ASTORE("astore", Opcode.ASTORE, OperandShape.LOCAL_U1),
    ASTORE_0("astore_0", Opcode.ASTORE_0, OperandShape.NONE),
    ASTORE_1("astore_1", Opcode.ASTORE_1, OperandShape.NONE),
    ASTORE_2("astore_2", Opcode.ASTORE_2, OperandShape.NONE),
    ASTORE_3("astore_3", Opcode.ASTORE_3, OperandShape.NONE),
    IASTORE("iastore", Opcode.IASTORE, OperandShape.NONE),
    LASTORE("lastore", Opcode.LASTORE, OperandShape.NONE),
    FASTORE("fastore", Opcode.FASTORE, OperandShape.NONE),
    DASTORE("dastore", Opcode.DASTORE, OperandShape.NONE),
    AASTORE("aastore", Opcode.AASTORE, OperandShape.NONE),
    BASTORE("bastore", Opcode.BASTORE, OperandShape.NONE),
    CASTORE("castore", Opcode.CASTORE, OperandShape.NONE),
    SASTORE("sastore", Opcode.SASTORE, OperandShape.NONE),

    // --- Stack ---
    POP("pop", Opcode.POP, OperandShape.NONE),
    POP2("pop2", Opcode.POP2, OperandShape.NONE),
    DUP("dup", Opcode.DUP, OperandShape.NONE),
    DUP_X1("dup_x1", Opcode.DUP_X1, OperandShape.NONE),
    DUP_X2("dup_x2", Opcode.DUP_X2, OperandShape.NONE),
    DUP2("dup2", Opcode.DUP2, OperandShape.NONE),
    DUP2_X1("dup2_x1", Opcode.DUP2_X1, OperandShape.NONE),
    DUP2_X2("dup2_x2", Opcode.DUP2_X2, OperandShape.NONE),
    SWAP("swap", Opcode.SWAP, OperandShape.NONE),

    // --- Math ---
    IADD("iadd", Opcode.IADD, OperandShape.NONE),
    LADD("ladd", Opcode.LADD, OperandShape.NONE),
    FADD("fadd", Opcode.FADD, OperandShape.NONE),
    DADD("dadd", Opcode.DADD, OperandShape.NONE),
    ISUB("isub", Opcode.ISUB, OperandShape.NONE),
    LSUB("lsub", Opcode.LSUB, OperandShape.NONE),
    FSUB("fsub", Opcode.FSUB, OperandShape.NONE),
    DSUB("dsub", Opcode.DSUB, OperandShape.NONE),
    IMUL("imul", Opcode.IMUL, OperandShape.NONE),
    LMUL("lmul", Opcode.LMUL, OperandShape.NONE),
    FMUL("fmul", Opcode.FMUL, OperandShape.NONE),
    DMUL("dmul", Opcode.DMUL, OperandShape.NONE),
    IDIV("idiv", Opcode.IDIV, OperandShape.NONE),
    LDIV("ldiv", Opcode.LDIV, OperandShape.NONE),
    FDIV("fdiv", Opcode.FDIV, OperandShape.NONE),
    DDIV("ddiv", Opcode.DDIV, OperandShape.NONE),
    IREM("irem", Opcode.IREM, OperandShape.NONE),
    LREM("lrem", Opcode.LREM, OperandShape.NONE),
    FREM("frem", Opcode.FREM, OperandShape.NONE),
    DREM("drem", Opcode.DREM, OperandShape.NONE),
    INEG("ineg", Opcode.INEG, OperandShape.NONE),
    LNEG("lneg", Opcode.LNEG, OperandShape.NONE),
    FNEG("fneg", Opcode.FNEG, OperandShape.NONE),
    DNEG("dneg", Opcode.DNEG, OperandShape.NONE),
    ISHL("ishl", Opcode.ISHL, OperandShape.NONE),
    LSHL("lshl", Opcode.LSHL, OperandShape.NONE),
    ISHR("ishr", Opcode.ISHR, OperandShape.NONE),
    LSHR("lshr", Opcode.LSHR, OperandShape.NONE),
    IUSHR("iushr", Opcode.IUSHR, OperandShape.NONE),
    LUSHR("lushr", Opcode.LUSHR, OperandShape.NONE),
    IAND("iand", Opcode.IAND, OperandShape.NONE),
    LAND("land", Opcode.LAND, OperandShape.NONE),
    IOR("ior", Opcode.IOR, OperandShape.NONE),
    LOR("lor", Opcode.LOR, OperandShape.NONE),
    IXOR("ixor", Opcode.IXOR, OperandShape.NONE),
    LXOR("lxor", Opcode.LXOR, OperandShape.NONE),
    IINC("iinc", Opcode.IINC, OperandShape.IINC),

    // --- Conversions ---
    I2L("i2l", Opcode.I2L, OperandShape.NONE),
    I2F("i2f", Opcode.I2F, OperandShape.NONE),
    I2D("i2d", Opcode.I2D, OperandShape.NONE),
    L2I("l2i", Opcode.L2I, OperandShape.NONE),
    L2F("l2f", Opcode.L2F, OperandShape.NONE),
    L2D("l2d", Opcode.L2D, OperandShape.NONE),
    F2I("f2i", Opcode.F2I, OperandShape.NONE),
    F2L("f2l", Opcode.F2L, OperandShape.NONE),
    F2D("f2d", Opcode.F2D, OperandShape.NONE),
    D2I("d2i", Opcode.D2I, OperandShape.NONE),
    D2L("d2l", Opcode.D2L, OperandShape.NONE),
    D2F("d2f", Opcode.D2F, OperandShape.NONE),
    I2B("i2b", Opcode.I2B, OperandShape.NONE),
    I2C("i2c", Opcode.I2C, OperandShape.NONE),
    I2S("i2s", Opcode.I2S, OperandShape.NONE),

    // --- Comparisons ---
    LCMP("lcmp", Opcode.LCMP, OperandShape.NONE),
    FCMPL("fcmpl", Opcode.FCMPL, OperandShape.NONE),
    FCMPG("fcmpg", Opcode.FCMPG, OperandShape.NONE),
    DCMPL("dcmpl", Opcode.DCMPL, OperandShape.NONE),
    DCMPG("dcmpg", Opcode.DCMPG, OperandShape.NONE),

    // --- Control transfer ---
    IFEQ("ifeq", Opcode.IFEQ, OperandShape.BRANCH),
    IFNE("ifne", Opcode.IFNE, OperandShape.BRANCH),
    IFLT("iflt", Opcode.IFLT, OperandShape.BRANCH),
    IFGE("ifge", Opcode.IFGE, OperandShape.BRANCH),
    IFGT("ifgt", Opcode.IFGT, OperandShape.BRANCH),
    IFLE("ifle", Opcode.IFLE, OperandShape.BRANCH),
    IF_ICMPEQ("if_icmpeq", Opcode.IF_ICMPEQ, OperandShape.BRANCH),
    IF_ICMPNE("if_icmpne", Opcode.IF_ICMPNE, OperandShape.BRANCH),
    IF_ICMPLT("if_icmplt", Opcode.IF_ICMPLT, OperandShape.BRANCH),
    IF_ICMPGE("if_icmpge", Opcode.IF_ICMPGE, OperandShape.BRANCH),
    IF_ICMPGT("if_icmpgt", Opcode.IF_ICMPGT, OperandShape.BRANCH),
    IF_ICMPLE("if_icmple", Opcode.IF_ICMPLE, OperandShape.BRANCH),
    IF_ACMPEQ("if_acmpeq", Opcode.IF_ACMPEQ, OperandShape.BRANCH),
    IF_ACMPNE("if_acmpne", Opcode.IF_ACMPNE, OperandShape.BRANCH),
    GOTO("goto", Opcode.GOTO, OperandShape.BRANCH),
    GOTO_W("goto_w", Opcode.GOTO_W, OperandShape.BRANCH_W),
    JSR("jsr", Opcode.JSR, OperandShape.BRANCH),
    JSR_W("jsr_w", Opcode.JSR_W, OperandShape.BRANCH_W),
    RET("ret", Opcode.RET, OperandShape.LOCAL_U1),
    TABLESWITCH("tableswitch", Opcode.TABLESWITCH, OperandShape.TABLESWITCH),
    LOOKUPSWITCH("lookupswitch", Opcode.LOOKUPSWITCH, OperandShape.LOOKUPSWITCH),
    IFNULL("ifnull", Opcode.IFNULL, OperandShape.BRANCH),
    IFNONNULL("ifnonnull", Opcode.IFNONNULL, OperandShape.BRANCH),

    // --- References ---
    GETSTATIC("getstatic", Opcode.GETSTATIC, OperandShape.FIELD_REF),
    PUTSTATIC("putstatic", Opcode.PUTSTATIC, OperandShape.FIELD_REF),
    GETFIELD("getfield", Opcode.GETFIELD, OperandShape.FIELD_REF),
    PUTFIELD("putfield", Opcode.PUTFIELD, OperandShape.FIELD_REF),
    INVOKEVIRTUAL("invokevirtual", Opcode.INVOKEVIRTUAL, OperandShape.METHOD_REF),
    INVOKESPECIAL("invokespecial", Opcode.INVOKESPECIAL, OperandShape.METHOD_REF),
    INVOKESTATIC("invokestatic", Opcode.INVOKESTATIC, OperandShape.METHOD_REF),
    INVOKEINTERFACE("invokeinterface", Opcode.INVOKEINTERFACE, OperandShape.INVOKEINTERFACE),
    INVOKEDYNAMIC("invokedynamic", Opcode.INVOKEDYNAMIC, OperandShape.INVOKEDYNAMIC),
    NEW("new", Opcode.NEW, OperandShape.CLASS_REF),
    NEWARRAY("newarray", Opcode.NEWARRAY, OperandShape.NEWARRAY),
    ANEWARRAY("anewarray", Opcode.ANEWARRAY, OperandShape.CLASS_REF),
    ARRAYLENGTH("arraylength", Opcode.ARRAYLENGTH, OperandShape.NONE),
    ATHROW("athrow", Opcode.ATHROW, OperandShape.NONE),
    CHECKCAST("checkcast", Opcode.CHECKCAST, OperandShape.CLASS_REF),
    INSTANCEOF("instanceof", Opcode.INSTANCEOF, OperandShape.CLASS_REF),
    MONITORENTER("monitorenter", Opcode.MONITORENTER, OperandShape.NONE),
    MONITOREXIT("monitorexit", Opcode.MONITOREXIT, OperandShape.NONE),
    MULTIANEWARRAY("multianewarray", Opcode.MULTIANEWARRAY, OperandShape.MULTIANEWARRAY),

    // --- Returns ---
    IRETURN("ireturn", Opcode.IRETURN, OperandShape.NONE),
    LRETURN("lreturn", Opcode.LRETURN, OperandShape.NONE),
    FRETURN("freturn", Opcode.FRETURN, OperandShape.NONE),
    DRETURN("dreturn", Opcode.DRETURN, OperandShape.NONE),
    ARETURN("areturn", Opcode.ARETURN, OperandShape.NONE),
    RETURN("return", Opcode.RETURN, OperandShape.NONE),

    // --- wide prefix (author writes "wide" then a widenable mnemonic) ---
    WIDE("wide", null, OperandShape.WIDE_PREFIX),

    // --- Reserved (rejected at parse/assemble time) ---
    BREAKPOINT("breakpoint", null, OperandShape.RESERVED),
    IMPDEP1("impdep1", null, OperandShape.RESERVED),
    IMPDEP2("impdep2", null, OperandShape.RESERVED);

    private static final Map<String, InstructionDef> BY_MNEMONIC = new HashMap<>();

    static {
        for (InstructionDef def : values()) {
            BY_MNEMONIC.put(def.mnemonic, def);
        }
    }

    private final String mnemonic;
    private final Opcode opcode;
    private final OperandShape shape;

    InstructionDef(String mnemonic, Opcode opcode, OperandShape shape) {
        this.mnemonic = mnemonic;
        this.opcode = opcode;
        this.shape = shape;
    }

    public String mnemonic() {
        return mnemonic;
    }

    /** Class-File API opcode, or {@code null} for {@code wide}/reserved. */
    public Opcode opcode() {
        return opcode;
    }

    public OperandShape shape() {
        return shape;
    }

    public boolean isReserved() {
        return shape == OperandShape.RESERVED;
    }

    public boolean isWidePrefix() {
        return shape == OperandShape.WIDE_PREFIX;
    }

    public static Optional<InstructionDef> lookup(String mnemonic) {
        return Optional.ofNullable(BY_MNEMONIC.get(mnemonic));
    }

    public static boolean isMnemonic(String text) {
        return BY_MNEMONIC.containsKey(text);
    }

    /**
     * Maps a widenable base mnemonic to the Class-File API {@code *_W} pseudo-opcode.
     * Example: {@code iload} → {@link Opcode#ILOAD_W}.
     */
    public static Opcode wideOpcode(InstructionDef base) {
        return switch (base) {
            case ILOAD -> Opcode.ILOAD_W;
            case LLOAD -> Opcode.LLOAD_W;
            case FLOAD -> Opcode.FLOAD_W;
            case DLOAD -> Opcode.DLOAD_W;
            case ALOAD -> Opcode.ALOAD_W;
            case ISTORE -> Opcode.ISTORE_W;
            case LSTORE -> Opcode.LSTORE_W;
            case FSTORE -> Opcode.FSTORE_W;
            case DSTORE -> Opcode.DSTORE_W;
            case ASTORE -> Opcode.ASTORE_W;
            case RET -> Opcode.RET_W;
            case IINC -> Opcode.IINC_W;
            default -> throw new IllegalArgumentException(
                    "opcode '" + base.mnemonic + "' is not a legal target of wide");
        };
    }

    /** True if this mnemonic may follow a {@code wide} prefix. */
    public boolean isWidenable() {
        return switch (this) {
            case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                 ISTORE, LSTORE, FSTORE, DSTORE, ASTORE,
                 RET, IINC -> true;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return mnemonic;
    }

    /** Sanity: enum name uppercased should match Opcode name when present. */
    public void assertOpcodeNameAligned() {
        if (opcode == null) {
            return;
        }
        String expected = mnemonic.toUpperCase(Locale.ROOT);
        if (!opcode.name().equals(expected)) {
            throw new AssertionError("mnemonic/opcode mismatch: " + mnemonic + " vs " + opcode.name());
        }
    }
}
