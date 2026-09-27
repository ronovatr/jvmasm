package com.jvmasm;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamplesIT {

    private static final List<String[]> EXAMPLES = List.of(
            new String[]{"examples/HelloWorld.jasm", "HelloWorld", "Hello, world"},
            new String[]{"examples/AddDemo.jasm", "AddDemo", "5"},
            new String[]{"examples/BranchDemo.jasm", "BranchDemo", "yes"},
            new String[]{"examples/StackBranchDemo.jasm", "StackBranchDemo", "yes"},
            new String[]{"examples/CatchDemo.jasm", "CatchDemo", "caught"},
            new String[]{"examples/ConstFieldDemo.jasm", "ConstFieldDemo", "42"},
            new String[]{"examples/IndyDemo.jasm", "IndyDemo", "Hello, indy"},
            new String[]{"examples/SwitchDemo.jasm", "SwitchDemo", "one"},
            new String[]{"examples/LookupDemo.jasm", "LookupDemo", "ten"},
            new String[]{"examples/ArrayDemo.jasm", "ArrayDemo", "7"},
            new String[]{"examples/OpcodeDemo.jasm", "OpcodeDemo", "9"},
            new String[]{"examples/ThrowsDemo.jasm", "ThrowsDemo", "io"},
            new String[]{"examples/WideDemo.jasm", "WideDemo", "301"}
    );

    @Test
    void allExamplesRun() throws Exception {
        for (String[] ex : EXAMPLES) {
            assertEquals(ex[2], runMain(ex[0], ex[1]), ex[0]);
        }
    }

    @Test
    void iload_0IsPreservedExactly() throws Exception {
        byte[] bytes = new Assembler().assemble(Files.readString(Path.of("examples/OpcodeDemo.jasm")));
        var cm = ClassFile.of().parse(bytes);
        var identity = cm.methods().stream()
                .filter(m -> m.methodName().equalsString("identity"))
                .findFirst()
                .orElseThrow();
        boolean found = identity.code().orElseThrow().elementStream()
                .anyMatch(el -> el instanceof LoadInstruction li && li.opcode() == Opcode.ILOAD_0);
        assertTrue(found, "identity method must contain exact iload_0 opcode");
    }

    @Test
    void strictStackRejectsMissingFrames() {
        String src = """
                .class public Bad
                .super java/lang/Object
                .method public static main([Ljava/lang/String;)V
                    .limit stack 1
                    .limit locals 1
                    goto L
                L:
                    return
                .end method
                """;
        Assembler asm = new Assembler(AssembleOptions.defaults().withStrictStack(true));
        assertThrows(IllegalArgumentException.class, () -> asm.assemble(src));
    }

    @Test
    void jsrRejectedOnModernVersion() {
        String src = """
                .class public Bad
                .super java/lang/Object
                .version 65
                .method public static main([Ljava/lang/String;)V
                    .limit stack 1
                    .limit locals 1
                    jsr L
                    return
                L:
                    astore_0
                    ret 0
                .end method
                """;
        assertThrows(IllegalArgumentException.class, () -> new Assembler().assemble(src));
    }

    private static String runMain(String jasmPath, String className) throws Exception {
        byte[] bytes = new Assembler().assemble(Files.readString(Path.of(jasmPath)));
		ByteClassLoader loader = new ByteClassLoader(className, bytes);
        Class<?> cls = loader.loadClass(className);
        Method main = cls.getMethod("main", String[].class);
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		PrintStream prev = System.out;
        System.setOut(new PrintStream(buf));
        try {
            main.invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(prev);
        }
        return buf.toString().trim();
    }

    static final class ByteClassLoader extends ClassLoader {
        private final String name;
        private final byte[] bytes;

        ByteClassLoader(String name, byte[] bytes) {
            super(null);
            this.name = name;
            this.bytes = bytes;
        }

        @Override
        protected Class<?> findClass(String n) throws ClassNotFoundException {
            if (!n.equals(name)) {
                throw new ClassNotFoundException(n);
            }
            return defineClass(n, bytes, 0, bytes.length);
        }
    }

}
