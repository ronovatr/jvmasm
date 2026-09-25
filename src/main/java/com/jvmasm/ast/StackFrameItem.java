package com.jvmasm.ast;

import java.util.List;

/**
 * Manual stack map frame at a label.
 * Syntax: {@code .stack at L locals <types> stack <types>}
 *
 * <p>Types: {@code top int float long double null uninitializedThis},
 * {@code uninitialized L}, or a class/array descriptor / internal name.
 */
public record StackFrameItem(
        String label,
        List<String> locals,
        List<String> stack,
        int line
) implements CodeItem {}
