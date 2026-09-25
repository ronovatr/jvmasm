package com.jvmasm;

import com.jvmasm.ast.ClassDecl;
import com.jvmasm.emit.ClassFileEmitter;
import com.jvmasm.lexer.Lexer;
import com.jvmasm.parser.Parser;
import com.jvmasm.resolve.StackDepthChecker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Front-door: .jasm source → .class bytes. */
public final class Assembler {
    private final ClassFileEmitter emitter = new ClassFileEmitter();
    private final StackDepthChecker stackChecker = new StackDepthChecker();

    public ClassDecl parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parseClass();
    }

    public byte[] assemble(String source) {
        ClassDecl cls = parse(source);
        for (var method : cls.methods) {
            stackChecker.check(method);
        }
        return emitter.emit(cls);
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

