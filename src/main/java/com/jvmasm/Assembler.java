package com.jvmasm;

import com.jvmasm.ast.ClassDecl;
import com.jvmasm.emit.ClassFileEmitter;
import com.jvmasm.lexer.Lexer;
import com.jvmasm.parser.Parser;
import com.jvmasm.resolve.StackDepthChecker;
import com.jvmasm.resolve.StrictStackValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Front-door: .jasm source → .class bytes. */
public final class Assembler {
    private final AssembleOptions options;
    private final ClassFileEmitter emitter;
    private final StackDepthChecker stackChecker = new StackDepthChecker();
    private final StrictStackValidator strictStack = new StrictStackValidator();

    public Assembler() {
        this(AssembleOptions.defaults());
    }

    public Assembler(AssembleOptions options) {
        this.options = options;
        this.emitter = new ClassFileEmitter(options);
    }

    public ClassDecl parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parseClass();
    }

    public byte[] assemble(String source) {
        ClassDecl cls = parse(source);
        for (var method : cls.methods) {
            stackChecker.check(method);
        }
        if (options.strictStack()) {
            strictStack.check(cls);
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
