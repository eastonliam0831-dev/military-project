package com.militaryclient.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Presets change only visual and audio settings (toggles of visual/audio features and their look). */
public final class Presets {
    public static final class Preset {
        public final String name;
        final Map<String, Boolean> toggles = new LinkedHashMap<>();
        final Map<String, Double> values = new LinkedHashMap<>();
        Preset(String name) { this.name = name; }
        Preset on(String... keys) { for (String k : keys) toggles.put(k, true); return this; }
        Preset set(String key, double v) { values.put(key, v); return this; }
        public void apply() { Config.applyValues(values, toggles, name); }
    }

    // Palette indexes: 1 Olive, 2 Lime, 3 Green, 4 Amber, 5 Orange, 6 Red, 7 White, 8 Cyan, 9 Tan
    // Font indexes: 0 Vanilla, 1 Tactical Pixel, 2 Stencil, 3 Digital LCD
    public static final List<Preset> ALL = List.of(
        new Preset("Modern Military")
            .on("tactical_hud", "digital_compass", "altitude_display", "gps_interface", "waypoint_markers",
                "aircraft_radar", "military_fonts")
            .set("global.accent", 1).set("military_fonts.font", 1).set("global.opacity", 0.9)
            .set("tactical_hud.opacity", 0.7),
        new Preset("Fighter Pilot")
            .on("tactical_hud", "targeting_display", "digital_compass", "altitude_display", "aircraft_radar",
                "aircraft_spotting", "aircraft_labels", "warning_lights", "military_fonts")
            .set("global.accent", 3).set("military_fonts.font", 3).set("targeting_display.style", 4)
            .set("aircraft_radar.mode", 1).set("warning_lights.trigger", 1),
        new Preset("Army")
            .on("tactical_hud", "digital_compass", "gps_interface", "map_overlay", "waypoint_markers",
                "military_signage", "battlefield_wind", "dust_particles", "military_fonts")
            .set("global.accent", 1).set("military_fonts.font", 2).set("map_overlay.cells", 32)
            .set("battlefield_wind.volume", 0.25).set("dust_particles.density", 0.25),
        new Preset("Tactical Green")
            .on("tactical_hud", "digital_compass", "altitude_display", "aircraft_radar", "night_vision",
                "military_fonts")
            .set("global.accent", 3).set("military_fonts.font", 3).set("night_vision.tint", 0.4)
            .set("night_vision.scanlines", 1),
        new Preset("Night Operations")
            .on("tactical_hud", "digital_compass", "aircraft_radar", "night_vision", "realistic_fog",
                "hangar_lighting", "military_fonts")
            .set("global.accent", 3).set("global.opacity", 0.75).set("military_fonts.font", 1)
            .set("realistic_fog.color", 3).set("realistic_fog.density", 0.3).set("night_vision.tint", 0.3)
            .set("tactical_hud.opacity", 0.55),
        new Preset("Minimal Military")
            .on("digital_compass", "altitude_display")
            .set("global.accent", 1).set("global.hud_scale", 0.85).set("global.opacity", 0.8)
            .set("digital_compass.width", 180).set("altitude_display.sea", 0)
    );

    public static Preset byName(String n) {
        for (Preset p : ALL) if (p.name.equals(n)) return p;
        return null;
    }

    private Presets() {}
}
