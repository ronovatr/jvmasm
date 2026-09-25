package com.jvmasm;

/** Options controlling assemble-time policy. */
public record AssembleOptions(
        /** When true, major≥50 methods with branches/handlers require explicit `.stack` frames. */
        boolean strictStack,
        /** When true (default), run Class-File API verifier after emit. */
        boolean verify
) {
    public static AssembleOptions defaults() {
        return new AssembleOptions(false, true);
    }

    public AssembleOptions withStrictStack(boolean v) {
        return new AssembleOptions(v, verify);
    }

    public AssembleOptions withVerify(boolean v) {
        return new AssembleOptions(strictStack, v);
    }
}
