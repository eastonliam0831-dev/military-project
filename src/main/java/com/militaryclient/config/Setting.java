package com.militaryclient.config;

/** One adjustable value belonging to a {@link Feature}. All numeric kinds are stored as doubles. */
public final class Setting {
    public enum Kind { BOOL, SLIDER, INT, CHOICE, COLOR, TEXT }

    /** Colour palette. Index 0 ("Theme") resolves to the global accent colour. */
    public static final String[] PALETTE_NAMES = {
        "Theme", "Olive", "Lime", "Green", "Amber", "Orange", "Red", "White", "Cyan", "Tan", "Blue", "Gray"
    };
    public static final int[] PALETTE = {
        0xFFA3C255, 0xFF8FA84A, 0xFFB6FF3C, 0xFF3CFF7A, 0xFFFFB000, 0xFFFF7A1A,
        0xFFFF4040, 0xFFEDEFE6, 0xFF40E0FF, 0xFFC8B27A, 0xFF4A90FF, 0xFF9AA095
    };

    public final String key;
    public final String label;
    public final Kind kind;
    public final double min, max, def;
    public final String[] choices;
    public final int maxLen;
    public final String defText;

    private Setting(String key, String label, Kind kind, double min, double max, double def,
                    String[] choices, int maxLen, String defText) {
        this.key = key; this.label = label; this.kind = kind;
        this.min = min; this.max = max; this.def = def;
        this.choices = choices; this.maxLen = maxLen; this.defText = defText;
    }

    public static Setting bool(String key, String label, boolean def) {
        return new Setting(key, label, Kind.BOOL, 0, 1, def ? 1 : 0, null, 0, null);
    }
    public static Setting sl(String key, String label, double min, double max, double def) {
        return new Setting(key, label, Kind.SLIDER, min, max, def, null, 0, null);
    }
    public static Setting in(String key, String label, int min, int max, int def) {
        return new Setting(key, label, Kind.INT, min, max, def, null, 0, null);
    }
    public static Setting ch(String key, String label, int def, String... choices) {
        return new Setting(key, label, Kind.CHOICE, 0, choices.length - 1, def, choices, 0, null);
    }
    public static Setting col(String key, String label, int defPaletteIndex) {
        return new Setting(key, label, Kind.COLOR, 0, PALETTE.length - 1, defPaletteIndex, null, 0, null);
    }
    public static Setting tx(String key, String label, String def, int maxLen) {
        return new Setting(key, label, Kind.TEXT, 0, 0, 0, null, maxLen, def);
    }

    public double clamp(double v) {
        if (kind == Kind.BOOL) return v >= 0.5 ? 1 : 0;
        double c = Math.max(min, Math.min(max, v));
        if (kind == Kind.INT || kind == Kind.CHOICE || kind == Kind.COLOR) c = Math.round(c);
        return c;
    }

    public String format(double v) {
        switch (kind) {
            case BOOL: return v >= 0.5 ? "ON" : "OFF";
            case INT: return Integer.toString((int) Math.round(v));
            case CHOICE: { int i = (int) Math.round(v); return choices[Math.max(0, Math.min(choices.length - 1, i))]; }
            case COLOR: return PALETTE_NAMES[(int) Math.round(v)];
            default: return Math.abs(v) >= 100 ? String.format("%.0f", v) : String.format("%.2f", v);
        }
    }
}
