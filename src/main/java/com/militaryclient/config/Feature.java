package com.militaryclient.config;

import java.util.List;

public final class Feature {
    public enum Category {
        HUD("HUD & TACTICAL VISUALS"), COSMETICS("PLAYER COSMETICS"), ATMOSPHERE("WORLD ATMOSPHERE"),
        AIRCRAFT("AIRCRAFT & VEHICLE VISUALS"), IMMERSION("IMMERSION & ENVIRONMENT"), GENERAL("GENERAL SETTINGS");
        public final String title;
        Category(String t) { title = t; }
    }

    public final int number;           // 1..50, 0 for non-numbered system entries
    public final String key, name, desc;
    public final Category cat;
    public final boolean defOn;
    public final boolean toggleable;
    public final String limitation;    // nullable: honest note about client-side limits
    public final List<Setting> settings;
    /** Runtime error text. Set when the feature failed; shown amber in the panel. Never persisted. */
    public volatile String status;

    public Feature(int number, String key, Category cat, String name, String desc, boolean defOn,
                   boolean toggleable, String limitation, List<Setting> settings) {
        this.number = number; this.key = key; this.cat = cat; this.name = name; this.desc = desc;
        this.defOn = defOn; this.toggleable = toggleable; this.limitation = limitation; this.settings = settings;
    }

    public String title() { return number > 0 ? String.format("%02d  %s", number, name.toUpperCase()) : name.toUpperCase(); }
}
