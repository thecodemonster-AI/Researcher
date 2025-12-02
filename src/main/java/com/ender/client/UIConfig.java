package com.ender.client;

/**
 * Small holder for UI scaling value so widgets can read the current scale set by the screen.
 */
public class UIConfig {
    private static float uiScale = 1.0f;

    public static synchronized void setScale(float s) {
        uiScale = s;
    }

    public static synchronized float getScale() {
        return uiScale;
    }
}
