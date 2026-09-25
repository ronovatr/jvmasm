package com.jvmasm;

import com.jvmasm.disasm.Disassembler;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.LoadInstruction;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundTripIT {

    @Test
    void helloWorldRoundTripStillRuns() throws Exception {
        assertRoundTripRuns("examples/HelloWorld.jasm", "HelloWorld", "Hello, world");
    }

    @Test
    void indyRoundTripPreservesBootstrap() throws Exception {
        String source = Files.readString(Path.of("examples/IndyDemo.jasm"));
        byte[] original = new Assembler().assemble(source);
        String disassembled = new Disassembler().disassemble(original);
        assertTrue(disassembled.contains(".bootstrap B0"));
        assertTrue(disassembled.contains("invokedynamic"));
        assertRoundTripFromBytes(original, "IndyDemo", "Hello, indy");
    }

    @Test
    void throwsRoundTripPreservesThrows() throws Exception {
        String source = Files.readString(Path.of("examples/ThrowsDemo.jasm"));
        byte[] original = new Assembler().assemble(source);
        String disassembled = new Disassembler().disassemble(original);
        assertTrue(disassembled.contains(".throws java/io/IOException"));
        byte[] again = new Assembler().assemble(disassembled);
        assertEquals("io", runMain(again, "ThrowsDemo"));
    }

    @Test
    void opcodeRoundTripKeepsIload_0() throws Exception {
        String source = Files.readString(Path.of("examples/OpcodeDemo.jasm"));
        byte[] original = new Assembler().assemble(source);
        String disassembled = new Disassembler().disassemble(original);
        assertTrue(disassembled.contains("iload_0"));
        byte[] again = new Assembler().assemble(disassembled);
        var cm = ClassFile.of().parse(again);
        var identity = cm.methods().stream()
                .filter(m -> m.methodName().equalsString("identity"))
                .findFirst()
                .orElseThrow();
        boolean found = identity.code().orElseThrow().elementStream()
                .anyMatch(el -> el instanceof LoadInstruction li && li.opcode() == Opcode.ILOAD_0);
        assertTrue(found);
        assertEquals("9", runMain(again, "OpcodeDemo"));
    }

    private static void assertRoundTripRuns(String jasmPath, String className, String expected)
            throws Exception {
        String source = Files.readString(Path.of(jasmPath));
        byte[] original = new Assembler().assemble(source);
        assertRoundTripFromBytes(original, className, expected);
    }

    private static void assertRoundTripFromBytes(byte[] original, String className, String expected)
            throws Exception {
        String disassembled = new Disassembler().disassemble(original);
        assertTrue(disassembled.contains("invokevirtual") || disassembled.contains("invokedynamic")
                || disassembled.contains("return"));
        byte[] again = new Assembler().assemble(disassembled);
        assertEquals(expected, runMain(again, className));
    }

    private static String runMain(byte[] bytes, String className) throws Exception {
        Class<?> cls = new ByteLoader(className, bytes).loadClass(className);
        var buf = new java.io.ByteArrayOutputStream();
        var prev = System.out;
        System.setOut(new java.io.PrintStream(buf));
        try {
            cls.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(prev);
        }
        return buf.toString().trim();
    }

    private static final class ByteLoader extends ClassLoader {
        private final String name;
        private final byte[] bytes;

        ByteLoader(String name, byte[] bytes) {
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
