package com.senseei.launcher.ark.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModRegistryTest {

    @Test
    void addEnforcesNoDuplicates() {
        ModRegistry r = new ModRegistry(new ArrayList<>());
        r.add(new Mod("111", "One"));
        r.add(new Mod("111", "One again"));
        assertEquals(1, r.all().size());
        assertEquals("One", r.nameOf("111"));
    }

    @Test
    void nameOfFallsBackToId() {
        assertEquals("999", new ModRegistry(List.of()).nameOf("999"));
    }

    @Test
    void missingReturnsUnknownDistinctIds() {
        ModRegistry r = new ModRegistry(List.of(new Mod("111", "One")));
        assertEquals(List.of("222"), r.missing(List.of("111", "222", "222")));
    }
}
