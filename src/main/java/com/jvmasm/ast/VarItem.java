package com.jvmasm.ast;

/**
 * Debug-only local variable table entry (never an operand alias).
 * {@code .var 0 is name Descriptor from L0 to L1}
 */
public record VarItem(
        int slot,
        String name,
        String descriptor,
        String fromLabel,
        String toLabel,
        int line
) implements CodeItem {}
