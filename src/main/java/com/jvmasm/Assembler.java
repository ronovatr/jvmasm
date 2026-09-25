package com.jvmasm;

import com.jvmasm.ast.ClassDecl;
import com.jvmasm.emit.ClassFileEmitter;
import com.jvmasm.lexer.Lexer;
import com.jvmasm.parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Front-door: .jasm source → .class bytes. */
public final class Assembler {
    private final ClassFileEmitter emitter = new ClassFileEmitter();

    public ClassDecl parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parseClass();
    }

    public byte[] assemble(String source) {
        return emitter.emit(parse(source));
    }

    public void assembleFile(Path input, Path output) throws IOException {
        String source = Files.readString(input);
        byte[] bytes = assemble(source);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.write(output, bytes);
    }
}
