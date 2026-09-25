package com.jvmasm.ast;

import java.util.List;

/**
 * Class-level bootstrap method declaration.
 * {@code .bootstrap NAME invokestatic Owner/name(Desc)Ret [args...]}
 */
public record BootstrapDecl(
        String name,
        String handleKind,
        String owner,
        String methodName,
        String descriptor,
        List<String> args,
        int line
) {}
