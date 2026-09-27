package com.jvmasm.util;

import java.lang.classfile.ClassFile;
import java.util.Locale;
import java.util.Set;

/** Access-flag keyword → bitmask helpers (JVMS 4.1 / 4.5 / 4.6). */
public final class AccessFlags {

    private AccessFlags() {}

    public static int parse(Set<String> keywords, Kind kind) {
        int flags = 0;
        for (String kw : keywords) {
            flags |= switch (kw.toLowerCase(Locale.ROOT)) {
                case "public" -> ClassFile.ACC_PUBLIC;
                case "private" -> ClassFile.ACC_PRIVATE;
                case "protected" -> ClassFile.ACC_PROTECTED;
                case "static" -> ClassFile.ACC_STATIC;
                case "final" -> ClassFile.ACC_FINAL;
                case "super" -> ClassFile.ACC_SUPER;
                case "synchronized" -> ClassFile.ACC_SYNCHRONIZED;
                case "volatile" -> ClassFile.ACC_VOLATILE;
                case "bridge" -> ClassFile.ACC_BRIDGE;
                case "varargs" -> ClassFile.ACC_VARARGS;
                case "native" -> ClassFile.ACC_NATIVE;
                case "interface" -> ClassFile.ACC_INTERFACE;
                case "abstract" -> ClassFile.ACC_ABSTRACT;
                case "strict" -> ClassFile.ACC_STRICT;
                case "synthetic" -> ClassFile.ACC_SYNTHETIC;
                case "annotation" -> ClassFile.ACC_ANNOTATION;
                case "enum" -> ClassFile.ACC_ENUM;
                case "module" -> ClassFile.ACC_MODULE;
                case "transient" -> ClassFile.ACC_TRANSIENT;
                case "open", "mandated" -> throw new IllegalArgumentException("access flag '" + kw + "' not applicable to " + kind);
                default -> throw new IllegalArgumentException("unknown access flag '" + kw + "'");
            };
        }

        if (kind == Kind.CLASS && (flags & ClassFile.ACC_INTERFACE) == 0) {
            flags |= ClassFile.ACC_SUPER; // historical default for classes
        }

        return flags;
    }

    public enum Kind {
        CLASS,
        FIELD,
        METHOD
    }

}
