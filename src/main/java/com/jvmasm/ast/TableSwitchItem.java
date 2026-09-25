package com.jvmasm.ast;

import java.util.List;

/** {@code tableswitch default D low L high H { C0 C1 ... }} */
public record TableSwitchItem(
        String defaultLabel,
        int low,
        int high,
        List<String> caseLabels,
        int line
) implements CodeItem {}
