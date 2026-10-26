
package ru.codex.codextest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CiProtectionDemoTest {

    @Test
    void shouldFailForCiDemo() {
        assertEquals(2, 1);
    }
}
