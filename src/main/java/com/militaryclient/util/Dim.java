package com.militaryclient.util;

import net.minecraft.client.multiplayer.ClientLevel;

public final class Dim {
    private Dim() {}

    /** Current dimension id like "minecraft:overworld", parsed from ResourceKey#toString to avoid API renames. */
    public static String current(ClientLevel level) {
        try {
            String s = String.valueOf(level.dimension());
            int a = s.lastIndexOf("/ "), b = s.lastIndexOf(']');
            if (a >= 0 && b > a) return s.substring(a + 2, b).trim();
            return s;
        } catch (Throwable t) {
            return "minecraft:overworld";
        }
    }

    public static String pretty(String dim) {
        int i = dim.indexOf(':');
        return (i >= 0 ? dim.substring(i + 1) : dim).replace('_', ' ').toUpperCase();
    }
}
