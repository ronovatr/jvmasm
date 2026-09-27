package com.jvmasm.lexer;

public class LexException extends RuntimeException {
    private final int line;
    private final int column;

    public LexException(String message, int line, int column) {
        super(message + " at " + line + ":" + column);
        this.line = line;
        this.column = column;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

}
