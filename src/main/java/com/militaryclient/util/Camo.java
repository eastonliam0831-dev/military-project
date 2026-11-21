package com.militaryclient.util;

/** Procedural camouflage. Deterministic value noise, so the pattern is stable between frames. */
public final class Camo {
    public static final String[] NAMES = {
        "Woodland", "Desert", "Urban", "Arctic", "Digital", "Night Ops", "Multicam", "Tiger Stripe", "Custom"
    };

    private static final int[][] PALETTES = {
        {0xFF4B5A2A, 0xFF2F3B1C, 0xFF6B5A3A, 0xFF16190F},   // Woodland: base, green, brown, black
        {0xFFC2A878, 0xFFA8895A, 0xFF8C7048, 0xFFD8C79A},   // Desert
        {0xFF8A8D90, 0xFF5E6165, 0xFF3A3C3F, 0xFFB5B8BB},   // Urban
        {0xFFE8ECEF, 0xFFC4CBD1, 0xFF9AA4AC, 0xFFFFFFFF},   // Arctic
        {0xFF5A6B3C, 0xFF3E4C2A, 0xFFA89466, 0xFF2A2F20},   // Digital (MARPAT-like)
        {0xFF1E2A1C, 0xFF111812, 0xFF2F3B2A, 0xFF070A07},   // Night Ops
        {0xFF8A7A56, 0xFF5E6B40, 0xFF6B5A3E, 0xFF3A3A2A},   // Multicam
        {0xFF6A6A2E, 0xFF1F2410, 0xFF8A5A2A, 0xFF111111},   // Tiger Stripe
    };

    public static int[] palette(int pattern, int c1, int c2, int c3) {
        if (pattern >= PALETTES.length) return new int[] { c1, c2, c3, Colors.scale(c1, 0.35f) };
        return PALETTES[pattern];
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static double rnd(int x, int y) { return (hash(x, y) & 0xFFFF) / 65535.0; }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private static double noise(double x, double y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double xf = smooth(x - xi), yf = smooth(y - yi);
        double a = rnd(xi, yi), b = rnd(xi + 1, yi), c = rnd(xi, yi + 1), d = rnd(xi + 1, yi + 1);
        return (a + (b - a) * xf) + ((c + (d - c) * xf) - (a + (b - a) * xf)) * yf;
    }

    private static double fbm(double x, double y) {
        return noise(x, y) * 0.6 + noise(x * 2.1 + 17, y * 2.1 + 9) * 0.3 + noise(x * 4.3 + 5, y * 4.3 + 31) * 0.1;
    }

    /** Colour at pixel (x, y). scale 1.0 ~ 12px blobs. */
    public static int color(int pattern, int x, int y, double scale, int[] pal) {
        double s = 1.0 / (12.0 * scale);
        switch (pattern) {
            case 1: { // Desert: large soft blobs, 3 tones
                double n = fbm(x * s * 0.7, y * s * 0.7);
                return n > 0.62 ? pal[2] : n > 0.45 ? pal[1] : pal[0];
            }
            case 2: { // Urban: blocky rectangles
                int cell = Math.max(2, (int) (10 * scale));
                double n = rnd(x / cell, y / (cell * 2 / 3 + 1));
                return n > 0.7 ? pal[2] : n > 0.4 ? pal[1] : pal[0];
            }
            case 3: { // Arctic
                double n = fbm(x * s * 0.8 + 40, y * s * 0.8);
                return n > 0.6 ? pal[2] : n > 0.47 ? pal[1] : pal[0];
            }
            case 4: { // Digital: quantised pixels
                int cell = Math.max(2, (int) (4 * scale));
                int qx = (x / cell) * cell, qy = (y / cell) * cell;
                double n = fbm(qx * s * 1.4, qy * s * 1.4);
                return n > 0.62 ? pal[2] : n > 0.48 ? pal[1] : pal[0];
            }
            case 7: { // Tiger stripe
                double n = Math.sin((x * s * 3.0 + fbm(x * s, y * s) * 5.0) * 2.0);
                double m = Math.sin((y * s * 1.2 + x * s * 0.8 + fbm(y * s + 7, x * s) * 3.0) * 2.0);
                return n > 0.55 ? pal[1] : m > 0.7 ? pal[2] : pal[0];
            }
            case 6: { // Multicam: four tones
                double n = fbm(x * s, y * s), m = fbm(x * s * 1.6 + 60, y * s * 1.6 + 11);
                return m > 0.66 ? pal[3] : n > 0.6 ? pal[2] : n > 0.46 ? pal[1] : pal[0];
            }
            case 5: // Night ops
            default: { // Woodland / Custom: blobs + dark flecks
                double n = fbm(x * s, y * s), m = fbm(x * s * 1.7 + 90, y * s * 1.7 + 3);
                return m > 0.68 ? pal[3] : n > 0.58 ? pal[2] : n > 0.44 ? pal[1] : pal[0];
            }
        }
    }

    private Camo() {}
}
