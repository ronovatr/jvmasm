package com.jvmasm.ast;

/** {@code .line N} — LineNumberTable entry at the current code position. */
public record LineItem(int number, int sourceLine) implements CodeItem {}
