package com.jvmasm.ast;

import java.util.List;

/** {@code lookupswitch default D { K -> L, ... }} */
public record LookupSwitchItem(
        String defaultLabel,
        List<LookupCase> cases,
        int line
) implements CodeItem {}
