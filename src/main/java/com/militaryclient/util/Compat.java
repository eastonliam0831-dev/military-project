package com.militaryclient.util;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection shims for the few vanilla calls whose location moved between 26.x releases
 * (screen switching, protected container fields, volume lookup). Every shim fails soft.
 */
public final class Compat {
    private static final Logger LOG = LogUtils.getLogger();
    private Compat() {}

    private static Method findMethod(Class<?> c, String name, Class<?>... params) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    public static void setScreen(Screen s) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Field gf = findField(mc.getClass(), "gui");
            Object gui = gf != null ? gf.get(mc) : null;
            if (gui != null) {
                Method m = findMethod(gui.getClass(), "setScreen", Screen.class);
                if (m != null) { m.invoke(gui, s); return; }
            }
            for (String name : new String[] { "setScreen", "setScreenAndShow" }) {
                Method m = findMethod(mc.getClass(), name, Screen.class);
                if (m != null) { m.invoke(mc, s); return; }
            }
            LOG.error("[MilitaryClient] No setScreen method found; cannot open screen");
        } catch (Throwable t) {
            LOG.error("[MilitaryClient] setScreen failed", t);
        }
    }

    public static Screen currentScreen() {
        Minecraft mc = Minecraft.getInstance();
        try {
            Field gf = findField(mc.getClass(), "gui");
            Object gui = gf != null ? gf.get(mc) : null;
            if (gui != null) {
                Method m = findMethod(gui.getClass(), "screen");
                if (m != null) return (Screen) m.invoke(gui);
            }
            Field sf = findField(mc.getClass(), "screen");
            if (sf != null) return (Screen) sf.get(mc);
        } catch (Throwable ignored) { }
        return null;
    }

    public static int intField(Object o, String name, int fallback) {
        try {
            Field f = findField(o.getClass(), name);
            return f != null ? f.getInt(o) : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** Minecraft's master volume (0..1); 1 if it cannot be read. */
    public static float masterVolume() {
        try {
            Minecraft mc = Minecraft.getInstance();
            for (Method m : mc.options.getClass().getMethods()) {
                if (m.getName().equals("getSoundSourceVolume") && m.getParameterCount() == 1) {
                    Class<?> pt = m.getParameterTypes()[0];
                    if (pt.isEnum()) {
                        @SuppressWarnings({ "unchecked", "rawtypes" })
                        Object master = Enum.valueOf((Class<? extends Enum>) pt, "MASTER");
                        Object r = m.invoke(mc.options, master);
                        if (r instanceof Number n) return n.floatValue();
                    }
                }
            }
        } catch (Throwable ignored) { }
        return 1f;
    }
}
