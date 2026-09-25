package com.jvmasm.ast;

/** Exception table entry: {@code .catch Type from L0 to L1 using L2}. */
public record CatchEntry(String typeInternalName, String from, String to, String handler) {}
