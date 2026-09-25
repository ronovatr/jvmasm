package com.jvmasm.ast;

public record FieldDecl(int accessFlags, String name, String descriptor, String constantValue) {}
