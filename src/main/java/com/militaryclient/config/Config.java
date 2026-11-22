package com.militaryclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * All persisted state. Pure Java (no Minecraft types) so it can be tested standalone.
 * Values are held in maps keyed "feature.setting"; anything absent falls back to the registry default.
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Serialised form. */
    public static final class Data {
        public int version = 1;
        public String preset = "Custom";
        public Map<String, Boolean> enabled = new LinkedHashMap<>();
        public Map<String, Double> values = new LinkedHashMap<>();
        public Map<String, String> texts = new LinkedHashMap<>();
        public List<Waypoint> waypoints = new ArrayList<>();
        /** Informational mirror of keybinds (Minecraft's options.txt remains the source of truth). */
        public Map<String, String> keybinds = new LinkedHashMap<>();
    }

    private static Data data = new Data();
    private static Path file;
    private static boolean dirty;
    private static long dirtyAt;
    public static String lastError;

    /** Listeners get the changed key ("feature" or "feature.setting"), or "*" for bulk changes. */
    public static final List<Consumer<String>> LISTENERS = new ArrayList<>();

    private Config() {}

    // ------------------------------------------------------------------ lifecycle

    public static void init(Path configDir) {
        file = configDir.resolve("militaryclient.json");
        load();
    }

    public static Path exportFile() { return file.resolveSibling("militaryclient-export.json"); }

    public static synchronized void load() {
        if (file == null || !Files.exists(file)) { data = new Data(); return; }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Data d = GSON.fromJson(r, Data.class);
            data = sanitize(d);
            lastError = null;
        } catch (Exception e) {
            // Keep a copy of the unreadable file instead of silently destroying it.
            lastError = "Config unreadable, defaults used: " + e.getMessage();
            try { Files.copy(file, file.resolveSibling("militaryclient.json.broken"), StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException ignored) { }
            data = new Data();
        }
    }

    private static Data sanitize(Data d) {
        if (d == null) d = new Data();
        if (d.enabled == null) d.enabled = new LinkedHashMap<>();
        if (d.values == null) d.values = new LinkedHashMap<>();
        if (d.texts == null) d.texts = new LinkedHashMap<>();
        if (d.waypoints == null) d.waypoints = new ArrayList<>();
        if (d.keybinds == null) d.keybinds = new LinkedHashMap<>();
        if (d.preset == null) d.preset = "Custom";
        // Drop unknown keys and clamp values so a hand-edited file can never break rendering.
        d.enabled.keySet().removeIf(k -> Features.get(k) == null || !Features.get(k).toggleable);
        d.values.entrySet().removeIf(e -> !Features.SETTINGS.containsKey(e.getKey()));
        d.values.replaceAll((k, v) -> Features.SETTINGS.get(k).clamp(v == null ? 0 : v));
        d.texts.entrySet().removeIf(e -> {
            Setting s = Features.SETTINGS.get(e.getKey());
            return s == null || s.kind != Setting.Kind.TEXT;
        });
        d.texts.replaceAll((k, v) -> clipText(Features.SETTINGS.get(k), v));
        d.waypoints.removeIf(w -> w == null || w.name == null);
        for (Waypoint w : d.waypoints) {
            if (w.dim == null) w.dim = "minecraft:overworld";
            w.type = Math.max(0, Math.min(Waypoint.TYPES.length - 1, w.type));
        }
        return d;
    }

    private static String clipText(Setting s, String v) {
        if (v == null) return s.defText;
        return v.length() > s.maxLen ? v.substring(0, s.maxLen) : v;
    }

    public static synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("militaryclient.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { GSON.toJson(data, w); }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
            lastError = null;
        } catch (Exception e) {
            lastError = "Could not save config: " + e.getMessage();
        }
    }

    /** Called every client tick; writes at most once per second after a change (auto-save). */
    public static void tick(long nowMs) {
        if (dirty && nowMs - dirtyAt > 1000) save();
    }

    private static void changed(String key) {
        dirty = true;
        dirtyAt = System.currentTimeMillis();
        for (Consumer<String> l : LISTENERS) {
            try { l.accept(key); } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------------ getters

    public static boolean on(String feature) {
        Boolean b = data.enabled.get(feature);
        if (b != null) return b;
        Feature f = Features.get(feature);
        return f != null && f.defOn;
    }

    public static double n(String feature, String setting) {
        String k = feature + "." + setting;
        Double d = data.values.get(k);
        if (d != null) return d;
        Setting s = Features.SETTINGS.get(k);
        if (s == null) throw new IllegalArgumentException("Unknown setting " + k);
        return s.def;
    }

    public static int i(String feature, String setting) { return (int) Math.round(n(feature, setting)); }
    public static float f(String feature, String setting) { return (float) n(feature, setting); }
    public static boolean b(String feature, String setting) { return n(feature, setting) >= 0.5; }
    /** Index of the selected choice. */
    public static int c(String feature, String setting) { return i(feature, setting); }

    public static String t(String feature, String setting) {
        String k = feature + "." + setting;
        String v = data.texts.get(k);
        if (v != null) return v;
        Setting s = Features.SETTINGS.get(k);
        if (s == null) throw new IllegalArgumentException("Unknown setting " + k);
        return s.defText;
    }

    /** Palette colour; palette index 0 resolves to the global accent. */
    public static int col(String feature, String setting) {
        int idx = i(feature, setting);
        if (idx <= 0) return accent();
        return Setting.PALETTE[Math.min(idx, Setting.PALETTE.length - 1)];
    }

    public static int accent() {
        int idx = i("global", "accent");
        if (idx <= 0) idx = 1;
        return Setting.PALETTE[Math.min(idx, Setting.PALETTE.length - 1)];
    }

    // ------------------------------------------------------------------ setters

    public static void setOn(String feature, boolean v) {
        Feature f = Features.get(feature);
        if (f == null || !f.toggleable) return;
        if (on(feature) == v && data.enabled.containsKey(feature)) return;
        data.enabled.put(feature, v);
        data.preset = "Custom";
        changed(feature);
    }

    public static void set(String feature, String setting, double v) {
        String k = feature + "." + setting;
        Setting s = Features.SETTINGS.get(k);
        if (s == null) return;
        double c = s.clamp(v);
        Double old = data.values.get(k);
        if (old != null && old == c) return;
        data.values.put(k, c);
        data.preset = "Custom";
        changed(k);
    }

    public static void setText(String feature, String setting, String v) {
        String k = feature + "." + setting;
        Setting s = Features.SETTINGS.get(k);
        if (s == null || s.kind != Setting.Kind.TEXT) return;
        data.texts.put(k, clipText(s, v));
        data.preset = "Custom";
        changed(k);
    }

    /** True when the user has never changed a setting (fresh or default-only config file). */
    public static boolean isPristine() {
        return data.enabled.isEmpty() && data.values.isEmpty() && data.texts.isEmpty();
    }

    public static String preset() { return data.preset; }
    public static void setPresetName(String n) { data.preset = n; dirty = true; dirtyAt = System.currentTimeMillis(); }
    public static List<Waypoint> waypoints() { return data.waypoints; }
    public static Map<String, String> keybindMirror() { return data.keybinds; }
    public static void touch() { changed("*"); }

    // ------------------------------------------------------------------ resets

    public static boolean isModified(Feature f) {
        if (data.enabled.containsKey(f.key) && data.enabled.get(f.key) != f.defOn) return true;
        for (Setting s : f.settings) {
            String k = f.key + "." + s.key;
            if (s.kind == Setting.Kind.TEXT) {
                String v = data.texts.get(k);
                if (v != null && !v.equals(s.defText)) return true;
            } else {
                Double v = data.values.get(k);
                if (v != null && v != s.def) return true;
            }
        }
        return false;
    }

    public static void resetFeature(Feature f) {
        data.enabled.remove(f.key);
        for (Setting s : f.settings) {
            data.values.remove(f.key + "." + s.key);
            data.texts.remove(f.key + "." + s.key);
        }
        data.preset = "Custom";
        changed(f.key);
    }

    public static void resetCategory(Feature.Category c) {
        for (Feature f : Features.ofCategory(c)) resetFeature(f);
    }

    public static void resetEverything() {
        List<Waypoint> keep = data.waypoints; // waypoints are user data, not settings
        data = new Data();
        data.waypoints = keep;
        changed("*");
        save();
    }

    public static void setAll(Feature.Category c, boolean on) {
        for (Feature f : Features.ofCategory(c)) if (f.toggleable) data.enabled.put(f.key, on);
        data.preset = "Custom";
        changed("*");
    }

    public static int enabledCount(Feature.Category c) {
        int n = 0;
        for (Feature f : Features.ofCategory(c)) if (f.toggleable && on(f.key)) n++;
        return n;
    }

    public static int toggleableCount(Feature.Category c) {
        int n = 0;
        for (Feature f : Features.ofCategory(c)) if (f.toggleable) n++;
        return n;
    }

    // ------------------------------------------------------------------ import / export

    public static String exportSettings() {
        try {
            Path p = exportFile();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) { GSON.toJson(data, w); }
            return "Exported to " + p.getFileName();
        } catch (Exception e) {
            return "Export failed: " + e.getMessage();
        }
    }

    public static String importSettings() {
        Path p = exportFile();
        if (!Files.exists(p)) return "No export file found: put militaryclient-export.json in the config folder";
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null) return "Import failed: file is empty";
            data = sanitize(d);
            changed("*");
            save();
            return "Imported settings from " + p.getFileName();
        } catch (Exception e) {
            return "Import failed: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------ presets

    public static void applyValues(Map<String, Double> values, Map<String, Boolean> toggles, String name) {
        for (Feature f : Features.ALL) if (f.toggleable) data.enabled.put(f.key, false);
        for (Map.Entry<String, Boolean> e : toggles.entrySet()) {
            Feature f = Features.get(e.getKey());
            if (f != null && f.toggleable) data.enabled.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Double> e : values.entrySet()) {
            Setting s = Features.SETTINGS.get(e.getKey());
            if (s != null) data.values.put(e.getKey(), s.clamp(e.getValue()));
        }
        data.preset = name;
        changed("*");
    }
}
