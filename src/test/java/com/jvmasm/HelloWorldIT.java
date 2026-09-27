package com.jvmasm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelloWorldIT {

    @TempDir
    Path tmp;

    @Test
    void assemblesAndRuns() throws Exception {
        String source = Files.readString(Path.of("examples/HelloWorld.jasm"));
        byte[] bytes = new Assembler().assemble(source);
        assertTrue(bytes.length > 16);
        assertEquals(0xCAFEBABE, ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF));

        Path classFile = tmp.resolve("HelloWorld.class");
        Files.write(classFile, bytes);

		URLClassLoader loader = new URLClassLoader(new java.net.URL[]{tmp.toUri().toURL()}, null);
        Class<?> cls = loader.loadClass("HelloWorld");
        Method main = cls.getMethod("main", String[].class);

        PrintStream prev = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf));
        try {
            main.invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(prev);
            loader.close();
        }
        assertEquals("Hello, world", buf.toString().trim());
    }
}
