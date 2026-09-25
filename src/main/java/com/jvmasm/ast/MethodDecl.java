package com.jvmasm.ast;

import java.util.ArrayList;
import java.util.List;

public final class MethodDecl {
    public int accessFlags;
    public String name;
    public String descriptor;
    public int maxStack = -1;
    public int maxLocals = -1;
    public final List<String> thrown = new ArrayList<>();
    public final List<CodeItem> code = new ArrayList<>();
}
