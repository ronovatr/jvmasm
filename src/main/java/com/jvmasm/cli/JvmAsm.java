package com.jvmasm.cli;

import com.jvmasm.Assembler;

import java.nio.file.Path;

/**
 * CLI entry point.
 *
 * <pre>
 *   jvmasm assemble HelloWorld.jasm -o HelloWorld.class
 * </pre>
 */
public final class JvmAsm {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        switch (args[0]) {
            case "assemble" -> assemble(args);
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
            die("assemble requires an input .jasm file");
        }
        if (output == null) {
            String name = input.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot >= 0 ? name.substring(0, dot) : name;
            output = input.toAbsolutePath().getParent().resolve(base + ".class");
        }
        new Assembler().assembleFile(input, output);
        System.out.println("wrote " + output.toAbsolutePath());
    }

    private static void usage() {
        System.out.println("""
                jvmasm — historical-mnemonic JVM bytecode assembler
                Usage:
                  jvmasm assemble <file.jasm> [-o <file.class>]
                """);
    }

    private static void die(String msg) {
        System.err.println(msg);
        System.exit(2);
    }
}
