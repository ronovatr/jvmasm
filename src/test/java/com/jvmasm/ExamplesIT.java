package com.jvmasm;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExamplesIT {

    @Test
    void addDemoPrints5() throws Exception {
        assertEquals("5", runMain("examples/AddDemo.jasm", "AddDemo"));
    }

    @Test
    void branchDemoPrintsYes() throws Exception {
        assertEquals("yes", runMain("examples/BranchDemo.jasm", "BranchDemo"));
    }

    @Test
    void stackBranchDemoPrintsYes() throws Exception {
        assertEquals("yes", runMain("examples/StackBranchDemo.jasm", "StackBranchDemo"));
    }

    private static String runMain(String jasmPath, String className) throws Exception {
        byte[] bytes = new Assembler().assemble(Files.readString(Path.of(jasmPath)));
        var loader = new ByteClassLoader(className, bytes);
        Class<?> cls = loader.loadClass(className);
        Method main = cls.getMethod("main", String[].class);
        var buf = new java.io.ByteArrayOutputStream();
        var prev = System.out;
        System.setOut(new java.io.PrintStream(buf));
        try {
            main.invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(prev);
        }
        return buf.toString().trim();
    }

    private static final class ByteClassLoader extends ClassLoader {
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
