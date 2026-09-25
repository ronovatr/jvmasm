package com.jvmasm.ast;

import com.jvmasm.isa.InstructionDef;

import java.util.List;

/**
 * One instruction. {@code wide} is folded in when the author wrote
 * {@code wide <mnemonic> …}; emission uses the corresponding {@code *_W} Opcode.
 */
public record InsnItem(
        InstructionDef def,
        boolean wide,
        List<String> operands,
        int line
) implements CodeItem {}
