package com.jvmasm.ast;

public sealed interface CodeItem
        permits LabelItem, InsnItem, TableSwitchItem, LookupSwitchItem,
        StackFrameItem, LineItem, VarItem {}
