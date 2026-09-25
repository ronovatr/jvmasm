package com.jvmasm;

import com.jvmasm.disasm.Disassembler;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundTripIT {

    @Test
    void helloWorldRoundTripStillRuns() throws Exception {
        String source = Files.readString(Path.of("examples/HelloWorld.jasm"));
        byte[] original = new Assembler().assemble(source);
        String disassembled = new Disassembler().disassemble(original);
        assertTrue(disassembled.contains("invokevirtual"));
        assertTrue(disassembled.contains("ldc "));

        byte[] again = new Assembler().assemble(disassembled);
        Class<?> cls = new ByteLoader("HelloWorld", again).loadClass("HelloWorld");
        var buf = new java.io.ByteArrayOutputStream();
        var prev = System.out;
        System.setOut(new java.io.PrintStream(buf));
        try {
            cls.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(prev);
        }
        assertEquals("Hello, world", buf.toString().trim());
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
