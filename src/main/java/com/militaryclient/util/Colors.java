package com.militaryclient.util;

public final class Colors {
    public static final int BG = 0xFF161814, PANEL = 0xFF20231D, CARD = 0xFF272B23, CARD_HI = 0xFF30352A,
        OLIVE = 0xFF5E7233, OLIVE_HI = 0xFF8FA84A, AMBER = 0xFFFFB000, RED = 0xFFFF4A3A,
        TEXT = 0xFFE6E8DD, DIM = 0xFF9AA090, DARK = 0xFF0C0D0A;

    public static int alpha(int argb, int a) { return (argb & 0x00FFFFFF) | ((a & 255) << 24); }

    /** Multiplies the existing alpha by m (0..1). */
    public static int mulA(int argb, float m) {
        int a = (int) ((argb >>> 24) * Math.max(0f, Math.min(1f, m)));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int rgb(int r, int g, int b) { return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }

    public static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int scale(int argb, float f) {
        return (argb & 0xFF000000) | (clamp((int) (((argb >> 16) & 255) * f)) << 16)
            | (clamp((int) (((argb >> 8) & 255) * f)) << 8) | clamp((int) ((argb & 255) * f));
    }

    private Colors() {}
}
