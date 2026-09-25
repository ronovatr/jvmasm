package com.jvmasm.lexer;

public enum TokenType {
    DIRECTIVE,   // .class, .super, .method, …
    MNEMONIC,    // exact JVMS mnemonic
    IDENT,       // labels, names, descriptors pieces
    INT,         // decimal or 0x hex
    FLOAT,       // with f/d suffix
    STRING,      // "…"
    LABEL_DEF,   // name:  (colon consumed; text is the name)
    COLON,
    LBRACE,
    RBRACE,
    ARROW,       // ->
    EQUALS,
    HASH,        // #
    COMMA,
    NEWLINE,
    EOF
}
