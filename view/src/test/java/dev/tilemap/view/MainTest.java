package dev.tilemap.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MainTest {
    @Test
    void mainClassMatchesBuild() {
        assertEquals("dev.tilemap.view.Main", Main.class.getName());
    }
}
