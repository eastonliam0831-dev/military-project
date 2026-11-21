package com.militaryclient.config;

public final class Waypoint {
    public static final String[] TYPES = { "Point", "Base", "Restricted", "Hazard", "Landing Zone", "Runway A", "Runway B" };
    public static final int POINT = 0, BASE = 1, RESTRICTED = 2, HAZARD = 3, LZ = 4, RWY_A = 5, RWY_B = 6;

    public String name = "WP";
    public double x, y, z;
    public String dim = "minecraft:overworld";
    public int type = 0;

    public Waypoint() {}
    public Waypoint(String name, double x, double y, double z, String dim, int type) {
        this.name = name; this.x = x; this.y = y; this.z = z; this.dim = dim; this.type = type;
    }
    public String typeName() { return TYPES[Math.max(0, Math.min(TYPES.length - 1, type))]; }
}
