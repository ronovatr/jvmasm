package com.jvmasm.lexer;

import com.jvmasm.isa.InstructionDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Line-oriented lexer. Comments run from {@code ;} to end of line.
 * Mnemonics are recognized case-sensitively against {@link InstructionDef}.
 */
public final class Lexer {
    private final String source;
    private int pos;
    private int line = 1;
    private int column = 1;

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipSpacesAndComments();
            if (pos >= source.length()) {
                tokens.add(new Token(TokenType.EOF, "", line, column));
                return tokens;
            }
            char c = peek();
            if (c == '\n' || c == '\r') {
                consumeNewline(tokens);
                continue;
            }
            int startLine = line;
            int startCol = column;
            if (c == '.') {
                tokens.add(directive(startLine, startCol));
            } else if (c == '"') {
                tokens.add(stringLiteral(startLine, startCol));
            } else if (c == '#') {
                advance();
                tokens.add(new Token(TokenType.HASH, "#", startLine, startCol));
            } else if (c == '{') {
                advance();
                tokens.add(new Token(TokenType.LBRACE, "{", startLine, startCol));
            } else if (c == '}') {
                advance();
                tokens.add(new Token(TokenType.RBRACE, "}", startLine, startCol));
            } else if (c == '=') {
                advance();
                tokens.add(new Token(TokenType.EQUALS, "=", startLine, startCol));
            } else if (c == ',') {
                advance();
                tokens.add(new Token(TokenType.COMMA, ",", startLine, startCol));
            } else if (c == '-' && peek(1) == '>') {
                advance();
                advance();
                tokens.add(new Token(TokenType.ARROW, "->", startLine, startCol));
            } else if (c == ':' ) {
                advance();
                tokens.add(new Token(TokenType.COLON, ":", startLine, startCol));
            } else if (isDigit(c) || (c == '-' && isDigit(peek(1))) || (c == '0' && (peek(1) == 'x' || peek(1) == 'X'))) {
                tokens.add(number(startLine, startCol));
            } else if (isIdentStart(c) || c == '[' || c == '(' || c == '/') {
                tokens.add(identOrMnemonicOrLabel(startLine, startCol));
            } else {
                throw new LexException("unexpected character '" + c + "'", startLine, startCol);
            }
        }
    }

    private void skipSpacesAndComments() {
        while (pos < source.length()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\f') {
                advance();
            } else if (c == ';') {
                while (pos < source.length() && peek() != '\n' && peek() != '\r') {
                    advance();
                }
            } else {
                return;
            }
        }
    }

    private void consumeNewline(List<Token> tokens) {
        int startLine = line;
        int startCol = column;
        if (peek() == '\r') {
            advance();
            if (pos < source.length() && peek() == '\n') {
                advance();
            }
        } else {
            advance();
        }
        tokens.add(new Token(TokenType.NEWLINE, "\\n", startLine, startCol));
        line++;
        column = 1;
    }

    private Token directive(int startLine, int startCol) {
        advance(); // '.'
        StringBuilder sb = new StringBuilder(".");
        while (pos < source.length() && isIdentPart(peek())) {
            sb.append(advance());
        }
        return new Token(TokenType.DIRECTIVE, sb.toString(), startLine, startCol);
    }

    private Token stringLiteral(int startLine, int startCol) {
        advance(); // opening "
        StringBuilder sb = new StringBuilder();
        while (pos < source.length()) {
            char c = advance();
            if (c == '"') {
                return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
            }
            if (c == '\\') {
                if (pos >= source.length()) {
                    throw new LexException("unterminated string escape", startLine, startCol);
                }
                char e = advance();
                sb.append(switch (e) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> throw new LexException("unknown escape '\\" + e + "'", line, column);
                });
            } else if (c == '\n' || c == '\r') {
                throw new LexException("unterminated string", startLine, startCol);
            } else {
                sb.append(c);
            }
        }
        throw new LexException("unterminated string", startLine, startCol);
    }

    private Token number(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        if (peek() == '-') {
            sb.append(advance());
        }
        if (peek() == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            sb.append(advance());
            sb.append(advance());
            if (!isHex(peek())) {
                throw new LexException("incomplete hex literal", startLine, startCol);
            }
            while (pos < source.length() && isHex(peek())) {
                sb.append(advance());
            }
            return new Token(TokenType.INT, sb.toString(), startLine, startCol);
        }
        while (pos < source.length() && isDigit(peek())) {
            sb.append(advance());
        }
        if (pos < source.length() && peek() == '.') {
            // float
            sb.append(advance());
            while (pos < source.length() && isDigit(peek())) {
                sb.append(advance());
            }
            if (pos < source.length() && (peek() == 'f' || peek() == 'F' || peek() == 'd' || peek() == 'D')) {
                sb.append(advance());
                return new Token(TokenType.FLOAT, sb.toString(), startLine, startCol);
            }
            throw new LexException("float literal requires f or d suffix", startLine, startCol);
        }
        if (pos < source.length() && (peek() == 'f' || peek() == 'F' || peek() == 'd' || peek() == 'D' || peek() == 'l' || peek() == 'L')) {
            char suf = peek();
            if (suf == 'f' || suf == 'F' || suf == 'd' || suf == 'D') {
                sb.append(advance());
                return new Token(TokenType.FLOAT, sb.toString(), startLine, startCol);
            }
            sb.append(advance()); // long suffix kept in text for later phases
        }
        return new Token(TokenType.INT, sb.toString(), startLine, startCol);
    }

    /**
     * Reads an identifier-like token. Descriptors and Owner/name forms are left as
     * opaque IDENT text (including {@code /}, {@code (}, {@code [}, {@code ;}).
     * A trailing {@code :} with no further same-line content makes a LABEL_DEF.
     */
    private Token identOrMnemonicOrLabel(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length()) {
            char c = peek();
            if (isIdentPart(c) || c == '/' || c == '[' || c == ']' || c == '(' || c == ')'
                    || c == ';' || c == '<' || c == '>' || c == '$') {
                sb.append(advance());
            } else {
                break;
            }
        }
        String text = sb.toString();

        // label definition: name: at end of token stream on this "word"
        if (pos < source.length() && peek() == ':') {
            // Look ahead: if only whitespace/comment until newline, it's a label def
            int look = pos + 1;
            while (look < source.length()) {
                char c = source.charAt(look);
                if (c == ' ' || c == '\t') {
                    look++;
                    continue;
                }
                if (c == ';' || c == '\n' || c == '\r') {
                    advance(); // consume ':'
                    return new Token(TokenType.LABEL_DEF, text, startLine, startCol);
                }
                break;
            }
            if (look >= source.length()) {
                advance();
                return new Token(TokenType.LABEL_DEF, text, startLine, startCol);
            }
        }

        if (InstructionDef.isMnemonic(text)) {
            return new Token(TokenType.MNEMONIC, text, startLine, startCol);
        }
        return new Token(TokenType.IDENT, text, startLine, startCol);
    }

    private char peek() {
        return peek(0);
    }

    private char peek(int ahead) {
        int i = pos + ahead;
        return i < source.length() ? source.charAt(i) : '\0';
    }

    private char advance() {
        char c = source.charAt(pos++);
        column++;
        return c;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHex(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
