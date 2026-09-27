package com.jvmasm.cli;

import com.jvmasm.AssembleOptions;
import com.jvmasm.Assembler;
import com.jvmasm.disasm.Disassembler;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI entry point.
 *
 * <pre>
 *   jvmasm assemble HelloWorld.jasm -o HelloWorld.class
 *   jvmasm assemble Branch.jasm --strict-stack -o Branch.class
 *   jvmasm disassemble HelloWorld.class -o HelloWorld.jasm
 * </pre>
 */
public final class JvmAsm {

    static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        switch (args[0]) {
            case "assemble" -> assemble(args);
            case "disassemble" -> disassemble(args);
            case "help", "-h", "--help" -> usage();
            default -> {
                System.err.println("unknown command: " + args[0]);
                usage();
                System.exit(2);
            }
        }
    }

    private static void assemble(String[] args) throws Exception {
        Path input = null;
        Path output = null;
        boolean strictStack = false;
        boolean verify = true;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-o", "--output" -> {
                    if (i + 1 >= args.length) {
                        die("missing path after " + args[i]);
                    }
                    output = Path.of(args[++i]);
                }
                case "--strict-stack" -> strictStack = true;
                case "--no-verify" -> verify = false;
                default -> {
                    if (args[i].startsWith("-")) {
                        die("unknown option: " + args[i]);
                    }
                    if (input != null) {
                        die("unexpected argument: " + args[i]);
                    }
                    input = Path.of(args[i]);
                }
            }
        }
        if (input == null) {
            die("assemble requires an input .jasm file");
        }
        if (output == null) {
            String name = input.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot >= 0 ? name.substring(0, dot) : name;
            output = input.toAbsolutePath().getParent().resolve(base + ".class");
        }
        AssembleOptions opts = AssembleOptions.defaults()
                .withStrictStack(strictStack)
                .withVerify(verify);
        new Assembler(opts).assembleFile(input, output);
        System.out.println("wrote " + output.toAbsolutePath());
    }

    private static void disassemble(String[] args) throws Exception {
        Path input = null;
        Path output = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "-o", "--output" -> {
                    if (i + 1 >= args.length) {
                        die("missing path after " + args[i]);
                    }
                    output = Path.of(args[++i]);
                }
                default -> {
                    if (args[i].startsWith("-")) {
                        die("unknown option: " + args[i]);
                    }
                    if (input != null) {
                        die("unexpected argument: " + args[i]);
                    }
                    input = Path.of(args[i]);
                }
            }
        }
        if (input == null) {
            die("disassemble requires an input .class file");
        }
        String text = new Disassembler().disassemble(Files.readAllBytes(input));
        if (output == null) {
            System.out.print(text);
        } else {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.writeString(output, text);
            System.out.println("wrote " + output.toAbsolutePath());
        }
    }

    private static void usage() {
        System.out.println("""
                jvmasm — historical-mnemonic JVM bytecode assembler
                Usage:
                  jvmasm assemble <file.jasm> [-o <file.class>] [--strict-stack] [--no-verify]
                  jvmasm disassemble <file.class> [-o <file.jasm>]
                """);
    }

    private static void die(String msg) {
        System.err.println(msg);
        System.exit(2);
    }

}
