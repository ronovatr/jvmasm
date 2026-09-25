package com.jvmasm.parser;

import com.jvmasm.ast.ClassDecl;
import com.jvmasm.ast.CodeItem;
import com.jvmasm.ast.FieldDecl;
import com.jvmasm.ast.InsnItem;
import com.jvmasm.ast.LabelItem;
import com.jvmasm.ast.MethodDecl;
import com.jvmasm.isa.InstructionDef;
import com.jvmasm.isa.OperandShape;
import com.jvmasm.lexer.Token;
import com.jvmasm.lexer.TokenType;
import com.jvmasm.util.AccessFlags;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase-1 parser: {@code .class}/{@code .super}/{@code .method}/{@code .limit}
 * plus a Phase-1 opcode subset. Additional directives/opcodes are rejected clearly.
 */
public final class Parser {
    private final List<Token> tokens;
    private int i;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public ClassDecl parseClass() {
        ClassDecl cls = new ClassDecl();
        skipNewlines();
        while (!check(TokenType.EOF)) {
            if (check(TokenType.DIRECTIVE)) {
                parseDirective(cls);
            } else {
                throw error("expected directive, got " + peek());
            }
            skipNewlines();
        }
        if (cls.thisClass == null) {
            throw new ParseException("missing .class directive", 1, 1);
        }
        if (cls.superClass == null) {
            cls.superClass = "java/lang/Object";
        }
        return cls;
    }

    private void parseDirective(ClassDecl cls) {
        Token dir = advance();
        switch (dir.text()) {
            case ".class" -> parseClassHeader(cls);
            case ".super" -> cls.superClass = expectIdentOrDesc("superclass name");
            case ".implements" -> cls.interfaces.add(expectIdentOrDesc("interface name"));
            case ".source" -> cls.sourceFile = expect(TokenType.STRING, "source file string").text();
            case ".version" -> {
                cls.majorVersion = parseIntToken(expect(TokenType.INT, "major version"));
                if (check(TokenType.INT)) {
                    cls.minorVersion = parseIntToken(advance());
                }
            }
            case ".field" -> cls.fields.add(parseField());
            case ".method" -> cls.methods.add(parseMethod());
            case ".bootstrap" -> cls.bootstraps.add(parseBootstrap(dir.line()));
            default -> throw error("unsupported directive '" + dir.text() + "'");
        }
        expectEndOfLine();
    }

    /**
     * {@code .bootstrap NAME invokestatic Owner/name(Desc)Ret [args...]}
     * Handle kinds: invokestatic, invokevirtual, invokespecial, invokeinterface,
     * getfield, putfield, getstatic, putstatic, newInvokeSpecial,
     * or Kind enum names (STATIC, VIRTUAL, …).
     */
    private com.jvmasm.ast.BootstrapDecl parseBootstrap(int line) {
        String name = expectIdentOrDesc("bootstrap name");
        String handleKind = expectIdentOrDesc("method handle kind");
        String ownerNameDesc = expectIdentOrDesc("bootstrap method ref");
        int paren = ownerNameDesc.indexOf('(');
        if (paren < 0) {
            throw error("bootstrap method ref needs Owner/name(Desc)Ret");
        }
        String ownerAndName = ownerNameDesc.substring(0, paren);
        String descriptor = ownerNameDesc.substring(paren);
        int slash = ownerAndName.lastIndexOf('/');
        if (slash < 0) {
            throw error("bootstrap method ref needs Owner/name");
        }
        String owner = ownerAndName.substring(0, slash);
        String methodName = ownerAndName.substring(slash + 1);
        List<String> args = new ArrayList<>();
        while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
            if (check(TokenType.STRING) || check(TokenType.INT) || check(TokenType.FLOAT)
                    || check(TokenType.IDENT) || check(TokenType.MNEMONIC)) {
                args.add(advance().text());
            } else {
                throw error("unexpected bootstrap arg: " + peek());
            }
        }
        return new com.jvmasm.ast.BootstrapDecl(
                name, handleKind, owner, methodName, descriptor, List.copyOf(args), line);
    }

    private void parseClassHeader(ClassDecl cls) {
        Set<String> flags = new LinkedHashSet<>();
        while (check(TokenType.IDENT) && isAccessKeyword(peek().text())) {
            flags.add(advance().text());
        }
        cls.accessFlags = AccessFlags.parse(flags, AccessFlags.Kind.CLASS);
        cls.thisClass = expectIdentOrDesc("class name");
    }

    private FieldDecl parseField() {
        Set<String> flags = new LinkedHashSet<>();
        while (check(TokenType.IDENT) && isAccessKeyword(peek().text())) {
            flags.add(advance().text());
        }
        // Jasmin-ish: .field [flags] Descriptor name  OR  .field [flags] name Descriptor
        // Plan example: .field private static I counter
        String a = expectIdentOrDesc("field descriptor or name");
        String b = expectIdentOrDesc("field name or descriptor");
        String descriptor;
        String name;
        if (looksLikeDescriptor(a)) {
            descriptor = a;
            name = b;
        } else {
            name = a;
            descriptor = b;
        }
        String constant = null;
        if (match(TokenType.EQUALS)) {
            Token v = advance();
            constant = v.text();
        }
        return new FieldDecl(AccessFlags.parse(flags, AccessFlags.Kind.FIELD), name, descriptor, constant);
    }

    private MethodDecl parseMethod() {
        MethodDecl m = new MethodDecl();
        Set<String> flags = new LinkedHashSet<>();
        while (check(TokenType.IDENT) && isAccessKeyword(peek().text())) {
            flags.add(advance().text());
        }
        m.accessFlags = AccessFlags.parse(flags, AccessFlags.Kind.METHOD);
        // name(descriptor)Return — often one IDENT token from lexer
        String sig = expectIdentOrDesc("method signature");
        int paren = sig.indexOf('(');
        if (paren < 0) {
            m.name = sig;
            m.descriptor = expectIdentOrDesc("method descriptor");
        } else {
            m.name = sig.substring(0, paren);
            m.descriptor = sig.substring(paren);
        }
        expectEndOfLine();
        skipNewlines();

        while (!check(TokenType.EOF)) {
            if (check(TokenType.DIRECTIVE) && peek().text().equals(".end")) {
                advance();
                Token what = expect(TokenType.IDENT, "method");
                if (!what.text().equals("method")) {
                    throw error("expected '.end method'");
                }
                expectEndOfLine();
                return m;
            }
            if (check(TokenType.DIRECTIVE)) {
                parseMethodDirective(m);
            } else if (check(TokenType.LABEL_DEF)) {
                m.code.add(new LabelItem(advance().text()));
                expectEndOfLine();
            } else if (check(TokenType.MNEMONIC)) {
                m.code.add(parseInstruction());
                expectEndOfLine();
            } else if (check(TokenType.NEWLINE)) {
                advance();
            } else {
                throw error("unexpected token in method body: " + peek());
            }
            skipNewlines();
        }
        throw error("unclosed .method (missing .end method)");
    }

    private void parseMethodDirective(MethodDecl m) {
        Token dir = advance();
        switch (dir.text()) {
            case ".limit" -> {
                Token what = expect(TokenType.IDENT, "stack or locals");
                int n = parseIntToken(expect(TokenType.INT, "limit value"));
                if (what.text().equals("stack")) {
                    m.maxStack = n;
                } else if (what.text().equals("locals")) {
                    m.maxLocals = n;
                } else {
                    throw error("expected .limit stack|locals");
                }
            }
            case ".throws" -> m.thrown.add(expectIdentOrDesc("exception class"));
            case ".catch" -> {
                // .catch Type from L0 to L1 using L2
                String type = expectIdentOrDesc("exception type");
                expectIdentWord("from");
                String from = expectIdentOrDesc("from label");
                expectIdentWord("to");
                String to = expectIdentOrDesc("to label");
                expectIdentWord("using");
                String handler = expectIdentOrDesc("handler label");
                m.catches.add(new com.jvmasm.ast.CatchEntry(type, from, to, handler));
            }
            case ".stack" -> m.code.add(parseStackFrame(dir.line()));
            case ".line" -> {
                int n = parseIntToken(expect(TokenType.INT, "line number"));
                m.code.add(new com.jvmasm.ast.LineItem(n, dir.line()));
            }
            case ".var" -> m.code.add(parseVar(dir.line()));
            default -> throw error("unsupported method directive '" + dir.text() + "'");
        }
        expectEndOfLine();
    }

    /** {@code .var SLOT is NAME Descriptor from L0 to L1} */
    private com.jvmasm.ast.VarItem parseVar(int line) {
        int slot = parseIntToken(expect(TokenType.INT, "local slot"));
        expectIdentWord("is");
        String name = expectIdentOrDesc("variable name");
        String desc = expectIdentOrDesc("variable descriptor");
        expectIdentWord("from");
        String from = expectIdentOrDesc("from label");
        expectIdentWord("to");
        String to = expectIdentOrDesc("to label");
        return new com.jvmasm.ast.VarItem(slot, name, desc, from, to, line);
    }

    /**
     * {@code .stack at LABEL locals t1 t2 stack t3 t4}
     * {@code locals}/{@code stack} sections may be empty.
     */
    private com.jvmasm.ast.StackFrameItem parseStackFrame(int line) {
        expectIdentWord("at");
        String label = expectIdentOrDesc("frame label");
        expectIdentWord("locals");
        List<String> locals = new ArrayList<>();
        while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)
                && !(check(TokenType.IDENT) && peek().text().equals("stack"))) {
            locals.add(expectVerificationType());
        }
        expectIdentWord("stack");
        List<String> stack = new ArrayList<>();
        while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
            stack.add(expectVerificationType());
        }
        return new com.jvmasm.ast.StackFrameItem(label, List.copyOf(locals), List.copyOf(stack), line);
    }

    private String expectVerificationType() {
        if (check(TokenType.IDENT) || check(TokenType.MNEMONIC)) {
            String t = advance().text();
            if (t.equals("uninitialized")) {
                String lab = expectIdentOrDesc("uninitialized label");
                return "uninitialized " + lab;
            }
            return t;
        }
        throw error("expected verification type, got " + peek());
    }

    private void expectIdentWord(String word) {
        Token t = expect(TokenType.IDENT, word);
        if (!t.text().equals(word)) {
            throw error("expected '" + word + "', got '" + t.text() + "'");
        }
    }

    private CodeItem parseInstruction() {
        Token mnem = advance();
        InstructionDef def = InstructionDef.lookup(mnem.text())
                .orElseThrow(() -> error("unknown mnemonic '" + mnem.text() + "'"));
        if (def.isReserved()) {
            throw error("reserved opcode '" + def.mnemonic() + "' is not legal in class files");
        }

        boolean wide = false;
        if (def.isWidePrefix()) {
            wide = true;
            Token next = expect(TokenType.MNEMONIC, "opcode after wide");
            def = InstructionDef.lookup(next.text())
                    .orElseThrow(() -> error("unknown mnemonic after wide: '" + next.text() + "'"));
            if (!def.isWidenable()) {
                throw error("'" + def.mnemonic() + "' cannot follow wide");
            }
        }

        if (def == InstructionDef.TABLESWITCH) {
            return parseTableSwitch(mnem.line());
        }
        if (def == InstructionDef.LOOKUPSWITCH) {
            return parseLookupSwitch(mnem.line());
        }

        List<String> operands = new ArrayList<>();
        OperandShape shape = wide
                ? (def == InstructionDef.IINC ? OperandShape.IINC : OperandShape.LOCAL_U1)
                : def.shape();

        switch (shape) {
            case NONE -> { /* no operands */ }
            case BIPUSH, SIPUSH, LOCAL_U1, BRANCH, BRANCH_W, CLASS_REF, LDC, LDC_W, LDC2_W,
                 METHOD_REF, NEWARRAY -> {
                operands.add(expectOperandText());
            }
            case FIELD_REF -> {
                operands.add(expectOperandText());
                if (check(TokenType.IDENT) || check(TokenType.MNEMONIC) || check(TokenType.STRING)) {
                    operands.add(advance().text());
                }
            }
            case IINC -> {
                operands.add(expectOperandText());
                operands.add(expectOperandText());
            }
            case INVOKEINTERFACE -> {
                operands.add(expectOperandText());
                operands.add(expect(TokenType.INT, "argcount").text());
            }
            case MULTIANEWARRAY -> {
                operands.add(expectOperandText());
                operands.add(expect(TokenType.INT, "dims").text());
            }
            case INVOKEDYNAMIC -> {
                // invokedynamic name()Desc bootstrapName
                // or: invokedynamic name Desc bootstrapName
                operands.add(expectOperandText());
                if (check(TokenType.IDENT) || check(TokenType.MNEMONIC)) {
                    String maybe = peek().text();
                    if (maybe.startsWith("(")) {
                        operands.add(advance().text());
                    }
                }
                operands.add(expectOperandText()); // bootstrap symbolic name
            }
            case WIDE_PREFIX, RESERVED -> throw error("internal: unexpected shape " + shape);
            case TABLESWITCH, LOOKUPSWITCH -> throw error("internal: switch handled above");
        }
        return new InsnItem(def, wide, List.copyOf(operands), mnem.line());
    }

    private CodeItem parseTableSwitch(int line) {
        // tableswitch default D low L high H { C0 C1 ... }
        expectIdentWord("default");
        String defLabel = expectOperandText();
        expectIdentWord("low");
        int low = parseIntToken(expect(TokenType.INT, "low"));
        expectIdentWord("high");
        int high = parseIntToken(expect(TokenType.INT, "high"));
        expect(TokenType.LBRACE, "{");
        List<String> cases = new ArrayList<>();
        while (!check(TokenType.RBRACE)) {
            if (check(TokenType.COMMA)) {
                advance();
                continue;
            }
            cases.add(expectOperandText());
        }
        expect(TokenType.RBRACE, "}");
        int expected = high - low + 1;
        if (cases.size() != expected) {
            throw error("tableswitch expects " + expected + " labels, got " + cases.size());
        }
        return new com.jvmasm.ast.TableSwitchItem(defLabel, low, high, List.copyOf(cases), line);
    }

    private CodeItem parseLookupSwitch(int line) {
        // lookupswitch default D { K -> L, ... }
        expectIdentWord("default");
        String defLabel = expectOperandText();
        expect(TokenType.LBRACE, "{");
        List<com.jvmasm.ast.LookupCase> cases = new ArrayList<>();
        while (!check(TokenType.RBRACE)) {
            if (check(TokenType.COMMA)) {
                advance();
                continue;
            }
            int key = parseIntToken(expect(TokenType.INT, "case key"));
            expect(TokenType.ARROW, "->");
            String label = expectOperandText();
            cases.add(new com.jvmasm.ast.LookupCase(key, label));
        }
        expect(TokenType.RBRACE, "}");
        for (int i = 1; i < cases.size(); i++) {
            if (cases.get(i).key() <= cases.get(i - 1).key()) {
                throw error("lookupswitch keys must be strictly ascending");
            }
        }
        return new com.jvmasm.ast.LookupSwitchItem(defLabel, List.copyOf(cases), line);
    }

    private String expectOperandText() {
        if (check(TokenType.IDENT) || check(TokenType.STRING) || check(TokenType.INT)
                || check(TokenType.FLOAT) || check(TokenType.MNEMONIC)) {
            return advance().text();
        }
        throw error("expected operand, got " + peek());
    }

    private void expectEndOfLine() {
        if (check(TokenType.NEWLINE) || check(TokenType.EOF)) {
            return;
        }
        throw error("expected end of line, got " + peek());
    }

    private void skipNewlines() {
        while (check(TokenType.NEWLINE)) {
            advance();
        }
    }

    private boolean isAccessKeyword(String s) {
        return switch (s) {
            case "public", "private", "protected", "static", "final", "synchronized",
                 "native", "abstract", "interface", "volatile", "transient", "synthetic",
                 "enum", "bridge", "varargs", "strict", "annotation", "super" -> true;
            default -> false;
        };
    }

    private boolean looksLikeDescriptor(String s) {
        return s.startsWith("[") || s.startsWith("L") || s.length() == 1
                && "BCDFIJSZ".indexOf(s.charAt(0)) >= 0;
    }

    private int parseIntToken(Token t) {
        String text = t.text();
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return Integer.parseInt(text.substring(2), 16);
        }
        return Integer.parseInt(text);
    }

    private String expectIdentOrDesc(String what) {
        if (check(TokenType.IDENT) || check(TokenType.MNEMONIC)) {
            return advance().text();
        }
        throw error("expected " + what + ", got " + peek());
    }

    private Token expect(TokenType type, String what) {
        if (check(type)) {
            return advance();
        }
        throw error("expected " + what + " (" + type + "), got " + peek());
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private Token peek() {
        return tokens.get(i);
    }

    private Token advance() {
        return tokens.get(i++);
    }

    private ParseException error(String msg) {
        Token t = peek();
        return new ParseException(msg, t.line(), t.column());
    }
}
