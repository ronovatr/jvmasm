package com.jvmasm.isa;

/**
 * Describes how operands are written in .jasm source for a given mnemonic.
 * Shapes match JVMS chapter 6 encodings, not Class-File API convenience overloads.
 */
public enum OperandShape {
    /** No operand: {@code nop}, {@code iadd}, {@code return}, {@code iload_0}, … */
    NONE,

    /** Signed byte immediate: {@code bipush N} */
    BIPUSH,

    /** Signed short immediate: {@code sipush N} */
    SIPUSH,

    /** Constant-pool index or literal; 1-byte index form: {@code ldc …} */
    LDC,

    /** Constant-pool index or literal; 2-byte index form: {@code ldc_w …} */
    LDC_W,

    /** Category-2 constant (long/double): {@code ldc2_w …} */
    LDC2_W,

    /** Local slot as u1 (0–255): {@code iload N}, {@code ret N} */
    LOCAL_U1,

    /** {@code iinc index increment} — narrow form (u1, s1) */
    IINC,

    /** Branch target label: {@code goto L}, {@code ifeq L}, … */
    BRANCH,

    /** Wide branch: {@code goto_w L}, {@code jsr_w L} */
    BRANCH_W,

    /** {@code tableswitch} multi-line form */
    TABLESWITCH,

    /** {@code lookupswitch} multi-line form */
    LOOKUPSWITCH,

    /** {@code Owner/name Descriptor}: getstatic / getfield / put* */
    FIELD_REF,

    /** {@code Owner/name(Descriptor)Return}: invoke* (virtual/special/static) */
    METHOD_REF,

    /** invokeinterface also takes an explicit argcount byte */
    INVOKEINTERFACE,

    /** invokedynamic — stretch / later phase */
    INVOKEDYNAMIC,

    /** Internal class name: {@code new}, {@code checkcast}, {@code anewarray}, … */
    CLASS_REF,

    /** {@code newarray TYPE} — boolean/char/float/… atype keyword */
    NEWARRAY,

    /** {@code multianewarray ClassName dims} */
    MULTIANEWARRAY,

    /**
     * The {@code wide} prefix token. Must be followed by one of the eleven
     * widenable opcodes; not a complete instruction by itself.
     */
    WIDE_PREFIX,

    /** Reserved / illegal in ordinary class files */
    RESERVED
}
