package com.jvmasm.ast;

import java.util.ArrayList;
import java.util.List;

/** One .jasm file = one class. */
public final class ClassDecl {
    public int accessFlags;
    public String thisClass;
    public String superClass;
    public final List<String> interfaces = new ArrayList<>();
    public String sourceFile;
    public final List<FieldDecl> fields = new ArrayList<>();
    public final List<MethodDecl> methods = new ArrayList<>();
    /** Default Java 21 (major 65); override with {@code .version}. */
    public int majorVersion = 65;
    public int minorVersion = 0;
}
