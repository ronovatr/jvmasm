package com.jvmasm.isa;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstructionDefTest {

    @Test
    void everyMnemonicIsUniqueAndLowercase() {
        Set<String> seen = new HashSet<>();
        for (InstructionDef def : InstructionDef.values()) {
            assertTrue(seen.add(def.mnemonic()), "duplicate mnemonic: " + def.mnemonic());
            assertEquals(def.mnemonic(), def.mnemonic().toLowerCase(java.util.Locale.ROOT), def.mnemonic());
            def.assertOpcodeNameAligned();
        }
    }

    @Test
    void lookupHitsHistoricalNames() {
        assertEquals(InstructionDef.ILOAD_0, InstructionDef.lookup("iload_0").orElseThrow());
        assertEquals(InstructionDef.INVOKEVIRTUAL, InstructionDef.lookup("invokevirtual").orElseThrow());
        assertEquals(InstructionDef.LDC_W, InstructionDef.lookup("ldc_w").orElseThrow());
        assertTrue(InstructionDef.lookup("load_local_int_0").isEmpty());
    }
}
