package com.jvmasm.lexer;

public record Token(TokenType type, String text, int line, int column) {

    @Override
    public String toString() {
        return type + "(" + text + ")@" + line + ":" + column;
    }

}
