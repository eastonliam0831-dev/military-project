package com.militaryclient.config;

import static com.militaryclient.config.Feature.Category.*;
import static com.militaryclient.config.Setting.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Central registry. The control panel is generated from this list, so nothing in the UI can be a dead control. */
public final class Features {
    public static final List<Feature> ALL = new ArrayList<>();
    public static final Map<String, Feature> BY_KEY = new HashMap<>();
    /** "featureKey.settingKey" -> Setting */
    public static final Map<String, Setting> SETTINGS = new HashMap<>();

    public static final String COSMETIC_NOTE =
        "Rendered as the Operator Loadout (panel preview + HUD badge) and first-person overlays. A real 3D "
        + "player-model layer needs the entity render-layer API and is not part of this build.";
    public static final String AUDIO_NOTE =
        "Sound is synthesised inside the mod (no audio files) and played outside Minecraft's sound engine; "
        + "it is scaled by the Minecraft master volume.";
    public static final String AIRCRAFT_NOTE =
        "Vanilla has no aircraft. Supported: Phantoms, Ender Dragons, Ghasts, elytra gliders, and modded "
        + "entities whose id matches the keywords in General > Aircraft Tracking.";

    private static Setting[] pos(double x, double y) {
        return new Setting[] { sl("x", "Position X (%)", 0, 100, x), sl("y", "Position Y (%)", 0, 100, y) };
    }

    private static Setting[] cat(Setting[] a, Setting... b) {
        Setting[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static void reg(int n, String key, Feature.Category c, String name, String desc, boolean on,
                            String limit, Setting... s) {
        Feature f = new Feature(n, key, c, name, desc, on, true, limit, List.of(s));
        ALL.add(f);
        BY_KEY.put(key, f);
        for (Setting st : s) SETTINGS.put(key + "." + st.key, st);
    }

    private static void sys(String key, String name, String desc, Setting... s) {
        Feature f = new Feature(0, key, GENERAL, name, desc, true, false, null, List.of(s));
        ALL.add(f);
        BY_KEY.put(key, f);
        for (Setting st : s) SETTINGS.put(key + "." + st.key, st);
    }

    static {
        // ---------------------------------------------------------------- HUD & TACTICAL
        reg(1, "tactical_hud", HUD, "Military Tactical HUD",
            "Information block with coordinates, heading, FPS, time, health and biome.", true, null,
            cat(pos(1, 12), sl("scale", "Size", 0.5, 2.5, 1.0), sl("opacity", "Transparency (opacity)", 0.1, 1, 0.7),
                col("color", "Color", 0), bool("coords", "Show coordinates", true),
                bool("direction", "Show direction", true), bool("fps", "Show FPS", true),
                bool("time", "Show world time", true), bool("health", "Show health", true),
                bool("biome", "Show biome", false)));
        reg(2, "targeting_display", HUD, "Fighter Jet Targeting Display",
            "Aircraft-style reticle with optional pitch ticks and lock brackets.", false,
            "Decorative only: it never locks onto or marks anything.",
            ch("style", "Reticle style", 4, "Cross", "Circle", "Brackets", "Pipper", "Fighter"),
            in("size", "Reticle size", 8, 90, 30), col("color", "Color", 0),
            sl("opacity", "Opacity", 0.1, 1, 0.8), bool("pulse", "Pulse animation", true),
            bool("side_brackets", "Side lock brackets", true), bool("ticks", "Pitch ticks", true));
        reg(3, "digital_compass", HUD, "Digital Compass Overlay",
            "Heading strip with degrees and cardinal directions.", true, null,
            ch("style", "Style", 0, "Strip", "Digital"), sl("y", "Position Y (%)", 0, 100, 1),
            in("width", "Width (px)", 120, 480, 240), sl("scale", "Size", 0.5, 2, 1.0),
            sl("opacity", "Opacity", 0.1, 1, 0.8), col("color", "Color", 0),
            bool("ticks", "Tick marks", true), bool("degrees", "Show degrees", true));
        reg(4, "gps_interface", HUD, "Military GPS Interface",
            "Position readout with dimension, heading and distance/bearing to your waypoints.", false, null,
            cat(pos(1, 42), sl("scale", "Size", 0.5, 2, 1.0), sl("opacity", "Opacity", 0.1, 1, 0.75),
                col("color", "Color", 0), bool("waypoints", "List waypoints", true),
                in("max", "Max waypoints listed", 1, 8, 4), bool("dimension", "Show dimension", true)));
        reg(5, "night_vision", HUD, "Green Night-Vision Filter",
            "Green phosphor look with real brightness boost (uses the Brightness option).", false,
            "Brightness is Minecraft's own gamma (max 'Bright'); the tint, scanlines and noise are a screen overlay. "
            + "Your previous Brightness is restored when this is turned off.",
            sl("tint", "Tint intensity", 0, 0.8, 0.35), sl("brightness", "Brightness boost", 0, 1, 1),
            ch("tone", "Tint color", 0, "Green", "Amber", "White"), bool("scanlines", "Scanlines", true),
            bool("noise", "Sensor noise", false), bool("vignette", "Lens vignette", true));
        reg(6, "thermal_filter", HUD, "Thermal-Style Visual Filter",
            "Thermal color palette wash plus heat signatures on creatures you can see.", false,
            "Cosmetic. Heat boxes are drawn only for creatures in your line of sight; this is not an X-ray.",
            sl("intensity", "Intensity", 0, 1, 0.5),
            ch("palette", "Palette", 0, "Ironbow", "White-Hot", "Black-Hot", "Rainbow"),
            bool("heat_boxes", "Heat signatures", true), in("range", "Signature range", 8, 64, 40),
            bool("scanlines", "Scanlines", true));
        reg(7, "map_overlay", HUD, "Military Map Overlay",
            "Top-down terrain map of loaded chunks with waypoints and coordinates.", false,
            "Samples only chunks your client has loaded; colors come from vanilla map colors.",
            cat(pos(78, 2), in("cells", "Grid size (cells)", 16, 48, 32), in("cell_px", "Cell size (px)", 2, 4, 3),
                in("blocks_per_cell", "Blocks per cell", 1, 4, 2), sl("opacity", "Opacity", 0.1, 1, 0.85),
                in("update_ticks", "Refresh (ticks)", 5, 60, 20), bool("waypoints", "Show waypoints", true),
                bool("coords", "Show coordinates", true), bool("grid", "Grid lines", true),
                col("color", "Frame color", 0)));
        reg(8, "aircraft_radar", HUD, "Aircraft Radar-Style Display",
            "Circular radar scope with sweep and heading indicator.", false,
            "'Visible Contacts' plots only entities you currently have line of sight to. 'Decorative' blips are "
            + "random cosmetics and represent nothing.",
            cat(pos(85, 58), in("size", "Size (px)", 60, 160, 90), in("range", "Range (blocks)", 16, 128, 48),
                ch("mode", "Blip mode", 1, "Decorative", "Visible Contacts"), bool("sweep", "Sweep animation", true),
                bool("heading", "Heading arrow", true), col("color", "Color", 0),
                sl("opacity", "Opacity", 0.1, 1, 0.8)));
        reg(9, "altitude_display", HUD, "Altitude & Coordinate Display",
            "X / Y / Z, altitude above sea level and above ground.", true, null,
            cat(pos(1, 62), sl("scale", "Size", 0.5, 2, 1.0), sl("opacity", "Opacity", 0.1, 1, 0.7),
                col("color", "Color", 0),
                ch("format", "Number format", 0, "Integer", "Decimal 1", "Decimal 2", "Block + Chunk"),
                bool("ground", "Show height above ground", true), bool("sea", "Show altitude vs sea level", true)));
        reg(10, "military_inventory", HUD, "Military Inventory Interface",
            "Camo-green tint, corner brackets, header strip and scanlines on container screens.", false,
            "Drawn as an overlay on top of container screens. Slot textures are not replaced, and all normal "
            + "inventory behavior is untouched.",
            col("color", "Tint color", 1), sl("tint_alpha", "Tint strength", 0, 0.5, 0.16),
            bool("brackets", "Corner brackets", true), bool("header", "Header strip", true),
            bool("scanlines", "Scanlines", true));

        // ---------------------------------------------------------------- COSMETICS
        reg(11, "army_uniform", COSMETICS, "Modern Army Uniforms",
            "Choose the uniform worn by your operator.", false, COSMETIC_NOTE,
            ch("style", "Uniform", 0, "ACU Army", "MARPAT Marine", "DCU Desert", "Jungle ERDL", "Special Ops Black",
                "Dress Uniform"),
            bool("use_camo", "Apply selected camouflage", true));
        reg(12, "camo_patterns", COSMETICS, "Camouflage Patterns",
            "Procedural camouflage used by uniform, helmet, vest and pack.", false, COSMETIC_NOTE,
            ch("pattern", "Pattern", 0, "Woodland", "Desert", "Urban", "Arctic", "Digital", "Night Ops", "Multicam",
                "Tiger Stripe", "Custom"),
            sl("scale", "Pattern scale", 0.5, 3, 1), col("custom1", "Custom color 1", 1),
            col("custom2", "Custom color 2", 9), col("custom3", "Custom color 3", 11),
            bool("apply_helmet", "Apply to helmet", true), bool("apply_vest", "Apply to vest", false),
            bool("apply_pack", "Apply to backpack", true));
        reg(13, "military_helmets", COSMETICS, "Military Helmets",
            "Helmet style, with an optional first-person helmet rim.", false, COSMETIC_NOTE,
            ch("style", "Helmet", 0, "PASGT", "ACH", "MICH", "Boonie Hat", "Beret", "Ops-Core FAST", "Pilot HGU"),
            bool("first_person_rim", "First-person helmet rim", true),
            bool("nvg_mount", "NVG mount accessory", false));
        reg(14, "tactical_vests", COSMETICS, "Tactical Vests",
            "Vest style, with optional first-person shoulder straps.", false, COSMETIC_NOTE,
            ch("style", "Vest", 0, "Plate Carrier", "IOTV", "MOLLE Rig", "Chest Rig"),
            bool("pouches", "Magazine pouches", true), bool("first_person_straps", "First-person straps", true));
        reg(15, "pilot_flight_suits", COSMETICS, "Pilot Flight Suits",
            "Replaces the uniform with a flight suit; optional first-person visor.", false, COSMETIC_NOTE,
            ch("style", "Flight suit", 0, "G-Suit Gray", "Olive Flight Suit", "Tan Flight Suit", "Rotary Wing Black"),
            bool("visor", "First-person visor overlay", true));
        reg(16, "gas_masks", COSMETICS, "Gas Mask Cosmetics",
            "Gas mask style with first-person lens overlay and breathing animation.", false, COSMETIC_NOTE,
            ch("style", "Mask", 0, "M50", "Half-Mask Respirator", "Full-Face Black", "Tactical Hood"),
            bool("first_person", "First-person lens overlay", true), bool("breathing", "Breathing animation", true));
        reg(17, "military_backpacks", COSMETICS, "Military Backpacks",
            "Backpack style shown on your operator.", false, COSMETIC_NOTE,
            ch("style", "Backpack", 0, "Assault Pack", "Rucksack", "Radio Pack", "Medic Pack"),
            sl("size", "Size", 0.6, 1.4, 1));
        reg(18, "dog_tags", COSMETICS, "Dog Tags",
            "Dog tag with your name, ID and blood type; optionally shown in the tactical HUD.", false, COSMETIC_NOTE,
            tx("name", "Name", "SMITH J", 14), tx("id", "ID number", "123-45-6789", 14),
            ch("blood", "Blood type", 6, "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"),
            bool("show_in_hud", "Show in tactical HUD header", true));
        reg(19, "rank_insignia", COSMETICS, "Rank Insignia",
            "Rank worn by your operator; optionally shown in the tactical HUD.", false, COSMETIC_NOTE,
            ch("rank", "Rank", 3, "PVT", "PFC", "CPL", "SGT", "SSG", "SFC", "MSG", "2LT", "1LT", "CPT", "MAJ", "LTC",
                "COL", "BG", "MG", "LTG", "GEN"),
            bool("show_in_hud", "Show in tactical HUD header", true));
        reg(20, "military_patches", COSMETICS, "Military Patches",
            "Up to three sleeve patches plus a unit tag.", false, COSMETIC_NOTE,
            ch("slot1", "Patch 1", 1, "None", "Unit Shield", "Flag", "Skull", "Eagle", "Wings", "Medic Cross", "Star"),
            ch("slot2", "Patch 2", 2, "None", "Unit Shield", "Flag", "Skull", "Eagle", "Wings", "Medic Cross", "Star"),
            ch("slot3", "Patch 3", 0, "None", "Unit Shield", "Flag", "Skull", "Eagle", "Wings", "Medic Cross", "Star"),
            tx("unit", "Unit tag", "1ST ARMD", 12));

        // ---------------------------------------------------------------- ATMOSPHERE
        reg(21, "distant_aircraft", ATMOSPHERE, "Distant Aircraft Sounds",
            "Occasional far-off aircraft passing overhead.", false, AUDIO_NOTE,
            sl("volume", "Volume", 0, 1, 0.35), in("min_interval", "Min interval (s)", 20, 600, 90),
            in("max_interval", "Max interval (s)", 30, 900, 240),
            ch("type", "Variation", 0, "Mixed", "Propeller", "Turboprop", "Airliner"));
        reg(22, "helicopter_ambience", ATMOSPHERE, "Helicopter Ambience",
            "Distant rotor chop that swells and fades.", false, AUDIO_NOTE,
            sl("volume", "Volume", 0, 1, 0.35), in("min_interval", "Min interval (s)", 30, 600, 120),
            in("max_interval", "Max interval (s)", 40, 900, 300),
            ch("type", "Variation", 0, "Mixed", "Light Chopper", "Heavy Lift"));
        reg(23, "jet_engine_sounds", ATMOSPHERE, "Jet Engine Sounds",
            "High altitude jet fly-bys with doppler sweep.", false, AUDIO_NOTE,
            sl("volume", "Volume", 0, 1, 0.35), in("min_interval", "Min interval (s)", 30, 600, 120),
            in("max_interval", "Max interval (s)", 40, 900, 300), bool("flyby", "Doppler fly-by", true));
        reg(24, "distant_explosions", ATMOSPHERE, "Distant Explosions",
            "Cinematic rolling booms from far away.", false, AUDIO_NOTE,
            sl("volume", "Volume", 0, 1, 0.35), in("min_interval", "Min interval (s)", 30, 900, 150),
            in("max_interval", "Max interval (s)", 60, 1200, 420), bool("flash", "Faint horizon flash", false));
        reg(25, "radio_chatter", ATMOSPHERE, "Military Radio Chatter",
            "Squelch, static and beeps like a distant radio net.", false,
            AUDIO_NOTE + " Chatter is synthetic babble/beeps, not recorded speech.",
            sl("volume", "Volume", 0, 1, 0.25), in("min_interval", "Min interval (s)", 30, 900, 120),
            in("max_interval", "Max interval (s)", 60, 1200, 300),
            ch("style", "Style", 0, "Squelch Chatter", "Morse", "Beep Net"));
        reg(26, "battlefield_wind", ATMOSPHERE, "Battlefield Wind Effects",
            "Soft wind gusts played every so often.", false, AUDIO_NOTE,
            sl("volume", "Volume", 0, 1, 0.3), sl("gust", "Gust strength", 0, 1, 0.5),
            in("interval", "Interval (s)", 15, 120, 35), bool("outdoors", "Only when under open sky", true));
        reg(27, "dust_particles", ATMOSPHERE, "Dust Particles",
            "Drifting dust around you.", false, null,
            sl("density", "Density", 0, 1, 0.3), in("radius", "Radius (blocks)", 6, 32, 16),
            bool("outdoors", "Only under open sky", true), ch("type", "Particle", 0, "Ash", "White Ash"));
        reg(28, "smoke_effects", ATMOSPHERE, "Smoke Effects",
            "Distant smoke plumes and haze.", false, "Purely cosmetic particles; no fire is created.",
            sl("density", "Density", 0, 1, 0.25), in("radius", "Radius (blocks)", 12, 48, 28),
            ch("style", "Style", 0, "Camp Plumes", "Battle Haze", "Heavy Plumes"),
            sl("visibility", "Visibility", 0, 1, 0.5));
        reg(29, "realistic_fog", ATMOSPHERE, "Realistic Fog",
            "Atmospheric haze with adjustable density and distance.", false,
            "Screen-space haze (a gradient layered over the world). True per-block depth fog needs a renderer hook "
            + "that Fabric does not expose in a stable way.",
            sl("density", "Density", 0, 1, 0.35), sl("distance", "Distance", 8, 100, 40),
            ch("color", "Color", 0, "Gray", "Morning", "Dust", "Night", "Toxic"),
            ch("mode", "Mode", 0, "Horizon Haze", "Full-Screen Veil"));
        reg(30, "improved_rain", ATMOSPHERE, "Improved Rain & Storms",
            "Extra rain splashes, darker storms, lightning flashes and thunder.", false,
            "Weather itself is controlled by the server; this only adds visuals and sound while it is raining.",
            sl("splash", "Splash density", 0, 1, 0.5), sl("storm_dark", "Storm darkening", 0, 0.6, 0.25),
            bool("lightning", "Lightning flashes", true), bool("thunder", "Thunder audio", true),
            sl("thunder_volume", "Thunder volume", 0, 1, 0.4));

        // ---------------------------------------------------------------- AIRCRAFT & VEHICLES
        reg(31, "aircraft_spotting", AIRCRAFT, "Aircraft Spotting Overlay",
            "List of visible aircraft with distance, altitude, speed and heading.", false, AIRCRAFT_NOTE,
            cat(pos(1, 55), sl("scale", "Size", 0.5, 2, 1), sl("opacity", "Opacity", 0.1, 1, 0.75),
                col("color", "Color", 0), in("max_entries", "Max entries", 1, 8, 5),
                in("range", "Range", 32, 256, 128)));
        reg(32, "aircraft_labels", AIRCRAFT, "Aircraft Identification Labels",
            "Floating labels over visible aircraft.", false, AIRCRAFT_NOTE,
            sl("scale", "Size", 0.5, 2, 0.8), col("color", "Color", 0), bool("type", "Show type", true),
            bool("distance", "Show distance", true), bool("altitude", "Show altitude", true),
            bool("speed", "Show speed", true), in("max_labels", "Max labels", 1, 12, 6),
            sl("opacity", "Opacity", 0.1, 1, 0.9));
        reg(33, "aircraft_contrails", AIRCRAFT, "Animated Aircraft Contrails",
            "Cosmetic vapor trails behind moving aircraft.", false, AIRCRAFT_NOTE,
            in("length", "Trail length", 1, 12, 4), sl("density", "Visibility", 0, 1, 0.6),
            ch("particle", "Particle", 0, "Cloud", "Smoke", "Poof"), in("min_y", "Only above Y", 0, 320, 0));
        reg(34, "vehicle_shadows", AIRCRAFT, "Improved Vehicle Shadows",
            "Soft shadow blob on the ground under boats, minecarts and aircraft.", false,
            "A screen-space blob placed on the surface below the entity, not a real shadow-map shadow.",
            sl("opacity", "Opacity", 0.1, 0.8, 0.4), sl("size", "Size", 0.5, 3, 1),
            bool("vehicles", "Vehicles", true), bool("aircraft", "Aircraft", true));
        reg(35, "aircraft_engine_audio", AIRCRAFT, "Realistic Aircraft Engine Audio",
            "Engine sound that follows nearby aircraft, with doppler.", false, AUDIO_NOTE + " " + AIRCRAFT_NOTE,
            sl("volume", "Volume", 0, 1, 0.35), in("range", "Audible range", 16, 128, 64),
            bool("doppler", "Doppler shift", true));
        reg(36, "vehicle_inspection", AIRCRAFT, "Vehicle Inspection GUI",
            "Military-style data panel for the vehicle or aircraft you look at.", false,
            "Shows only what the client knows: type, health if any, speed, heading, passengers, position.",
            cat(pos(40, 68), sl("scale", "Size", 0.5, 2, 1), sl("opacity", "Opacity", 0.1, 1, 0.8),
                col("color", "Color", 0), in("range", "Range", 4, 48, 24)));
        reg(37, "aircraft_camera", AIRCRAFT, "Aircraft Viewing Camera",
            "Viewfinder with REC marker and tracking box; hold the keybind to zoom.", false,
            "Zoom changes Minecraft's FOV (minimum 30 degrees, about 2.3x at default). It is a viewfinder, not a "
            + "second camera render.",
            sl("zoom", "Zoom", 1, 2.3, 1.8), ch("frame", "Frame", 0, "Broadcast", "Tactical", "Surveillance"),
            bool("rec", "REC indicator", true), bool("track_box", "Tracking box", true), col("color", "Color", 0));
        reg(38, "aircraft_cards", AIRCRAFT, "Aircraft Identification Cards",
            "Dossier-style card with silhouette for the aircraft nearest your crosshair.", false, AIRCRAFT_NOTE,
            cat(pos(60, 1), sl("scale", "Size", 0.5, 2, 1), ch("layout", "Layout", 0, "Dossier", "Compact"),
                bool("silhouette", "Silhouette", true), col("color", "Color", 0)));
        reg(39, "animated_vehicle_displays", AIRCRAFT, "Animated Vehicle Displays",
            "Animated dashboard when you ride a vehicle, and animation on the inspection panel.", false, null,
            cat(pos(1, 80), sl("speed", "Animation speed", 0.2, 3, 1),
                ch("style", "Style", 0, "Scan", "Grid", "Pulse"), sl("scale", "Size", 0.5, 2, 1),
                bool("dashboard", "Dashboard while riding", true)));
        reg(40, "vehicle_skins", AIRCRAFT, "Military Vehicle Cosmetic Skins",
            "Camouflage skins for boats and minecarts via a built-in resource pack.", false,
            "Only the vanilla boats and minecarts are reskinned. Modded vehicle models need their own pack. "
            + "Toggling reloads resources.",
            ch("skin", "Skin", 0, "Olive Drab", "Desert Tan", "Woodland Camo", "Urban Camo"));

        // ---------------------------------------------------------------- IMMERSION
        reg(41, "base_decor_pack", IMMERSION, "Military Base Decoration Pack",
            "Built-in resource pack: camo wool, hazard-stripe and steel-plate textures for base building.", false,
            "Retextures existing vanilla blocks. Custom furniture needs new models and is not included. Toggling "
            + "reloads resources.");
        reg(42, "military_signage", IMMERSION, "Realistic Military Signage",
            "Zone banners such as 'NOW ENTERING' or 'RESTRICTED AREA' near your waypoints.", false,
            "Implemented as screen banners tied to waypoints; vanilla sign blocks are not retextured.",
            in("zone_radius", "Zone radius", 8, 128, 40), ch("style", "Style", 0, "Stencil", "Warning", "Plain"),
            sl("y", "Banner Y (%)", 0, 100, 18), bool("hazard", "Hazard stripes", true),
            sl("duration", "Duration (s)", 2, 12, 5));
        reg(43, "runway_lighting", IMMERSION, "Runway Lighting Visuals",
            "Animated sequenced lights between two waypoints marked Runway A and Runway B.", false,
            "Particle lights only. Place waypoints of type 'Runway A' and 'Runway B' in the General tab.",
            sl("brightness", "Brightness", 0, 1, 0.6), sl("speed", "Sequence speed", 0.2, 3, 1),
            in("spacing", "Light spacing", 3, 12, 6), bool("strobe", "Sequenced strobe", true),
            in("max_length", "Max runway length", 16, 200, 96));
        reg(44, "warning_lights", IMMERSION, "Animated Warning Lights",
            "Flashing corner beacons and optional world beacons at Hazard waypoints.", false, null,
            ch("color", "Color", 0, "Amber", "Red", "Blue", "White"), sl("speed", "Flash speed", 0.3, 4, 1.5),
            ch("trigger", "Trigger", 0, "Always", "Low Health", "Hostile Visible", "Storm", "Low Health + Hostile"),
            sl("size", "Size", 0.5, 2, 1), bool("world_beacons", "World beacons at Hazard waypoints", true));
        reg(45, "hangar_lighting", IMMERSION, "Hangar Lighting Improvements",
            "Industrial overhead light strips and tint whenever you are indoors.", false,
            "Screen-space effect (no real light-engine change). Triggers when you have no sky access.",
            sl("intensity", "Intensity", 0, 1, 0.4), ch("tone", "Tone", 0, "Cool White", "Warm Sodium", "Neutral"),
            bool("flicker", "Light flicker", true), bool("strips", "Light strips", true));
        reg(46, "military_radio_gui", IMMERSION, "Military Radio GUI",
            "Radio panel with channel, signal bars and waveform. Channels set the chatter style.", false,
            "Channel/audio control works with the Radio Chatter feature; use the keybinds to change channel.",
            cat(pos(1, 88), sl("scale", "Size", 0.5, 2, 1), sl("opacity", "Opacity", 0.1, 1, 0.8),
                ch("channel", "Channel", 0, "CH 1", "CH 2", "CH 3", "CH 4", "CH 5", "CH 6", "CH 7", "CH 8"),
                bool("waveform", "Waveform", true), col("color", "Color", 0)));
        reg(47, "military_fonts", IMMERSION, "Custom Military Fonts",
            "Digital military typography for the HUD.", false,
            "Fonts are drawn by the mod itself as pixel glyphs (A-Z, 0-9, punctuation), not resource-pack fonts.",
            ch("font", "Font", 1, "Vanilla", "Tactical Pixel", "Stencil", "Digital LCD"),
            bool("apply_hud", "Use in HUD", true), bool("apply_panel", "Use for panel headings", true),
            sl("spacing", "Letter spacing", 0, 3, 1));
        reg(48, "waypoint_markers", IMMERSION, "Tactical Waypoint Markers",
            "On-screen markers with distance for your waypoints.", false, null,
            ch("style", "Style", 0, "Diamond", "Chevron", "Pin"), in("size", "Size", 6, 30, 12),
            in("max_distance", "Max distance", 16, 4000, 600), col("color", "Color", 0),
            bool("name", "Show name", true), bool("distance", "Show distance", true),
            bool("fade", "Fade with distance", true));
        reg(49, "binocular_overlay", IMMERSION, "Realistic Binocular Overlay",
            "Binocular / scope / rangefinder view; hold the keybind (or toggle).", false,
            "Zoom changes Minecraft's FOV (minimum 30 degrees, about 2.3x at default).",
            sl("zoom", "Zoom", 1, 2.3, 2.0), ch("style", "Style", 0, "Binocular", "Scope", "Rangefinder"),
            sl("vignette", "Vignette", 0.3, 1, 0.85), bool("range", "Range readout", true),
            col("color", "Color", 0), ch("mode", "Key mode", 0, "Hold", "Toggle"));
        reg(50, "loading_screens", IMMERSION, "Military-Themed Loading Screens",
            "Themed scene with rotating tips over world-loading and connecting screens.", false,
            "The progress bar is animated, not real progress. The Cancel button area is left uncovered.",
            ch("scene", "Scene", 0, "Rotating", "Desert Operation", "Jungle", "Arctic", "Night Operation", "Urban",
                "Carrier Deck"),
            bool("tips", "Rotating tips", true), in("rotate", "Rotate every (s)", 3, 30, 8),
            bool("bar", "Show progress bar", true));

        // ---------------------------------------------------------------- GENERAL / CUSTOMIZATION
        sys("global", "Advanced Customization",
            "Global look, volume and performance controls shared by every feature.",
            col("accent", "Accent color", 1), sl("opacity", "Interface transparency (opacity)", 0.2, 1, 1),
            sl("hud_scale", "HUD scale", 0.5, 2, 1), sl("text_scale", "Text size", 0.75, 3, 1),
            sl("ui_scale", "Control panel scale", 0.75, 1.6, 1), sl("anim_speed", "Animation speed", 0.25, 3, 1),
            sl("volume", "Master volume (all mod audio)", 0, 1, 0.8),
            sl("particles", "Particle density multiplier", 0, 2, 1),
            sl("fog", "Fog intensity multiplier", 0, 2, 1),
            bool("low_perf", "Low-performance mode", false));
        sys("tracking", "Aircraft Tracking",
            "What counts as an aircraft or vehicle. Used by all aircraft features.",
            bool("vanilla_flyers", "Treat vanilla flyers as aircraft", true),
            in("range", "Scan range (blocks)", 32, 256, 128),
            tx("keywords", "Aircraft id keywords", "plane,jet,heli,aircraft,airplane,drone,fighter,bomber,glider", 80),
            tx("vehicle_keywords", "Vehicle id keywords", "tank,jeep,truck,car,apc,humvee,vehicle,boat,raft,minecart", 80));
    }

    public static Feature get(String key) { return BY_KEY.get(key); }

    public static List<Feature> ofCategory(Feature.Category c) {
        List<Feature> r = new ArrayList<>();
        for (Feature f : ALL) if (f.cat == c) r.add(f);
        return r;
    }

    private Features() {}
}
