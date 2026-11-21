package com.militaryclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Manual perspective projection of world points onto the GUI. Used for waypoint markers, labels, shadows
 * and heat signatures. It deliberately uses only player position, view angles and the FOV option.
 */
public final class Proj {
    public static double ex, ey, ez;
    private static double fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalf, aspect;
    public static int w, h;
    public static float yawDeg, pitchDeg;
    public static boolean valid;

    private Proj() {}

    public static void begin(Minecraft mc, float pt) {
        valid = false;
        Player p = mc.player;
        if (p == null) return;
        w = mc.getWindow().getGuiScaledWidth();
        h = mc.getWindow().getGuiScaledHeight();
        if (w <= 0 || h <= 0) return;
        ex = Mth.lerp(pt, p.xo, p.getX());
        ey = Mth.lerp(pt, p.yo, p.getY()) + p.getEyeHeight();
        ez = Mth.lerp(pt, p.zo, p.getZ());
        yawDeg = p.getViewYRot(pt);
        pitchDeg = p.getViewXRot(pt);
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch), sp = Math.sin(pitch), cy = Math.cos(yaw), sy = Math.sin(yaw);
        fx = -sy * cp; fy = -sp; fz = cy * cp;
        rx = -cy; ry = 0; rz = -sy;
        // up = right x forward
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;
        tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        aspect = (double) w / h;
        valid = true;
    }

    /** Projects into out[0]=x, out[1]=y (GUI px), out[2]=depth. Returns false if behind the camera. */
    public static boolean project(double x, double y, double z, float[] out) {
        if (!valid) return false;
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double cz = dx * fx + dy * fy + dz * fz;
        if (cz < 0.05) return false;
        double cx = dx * rx + dy * ry + dz * rz;
        double cyv = dx * ux + dy * uy + dz * uz;
        double nx = (cx / cz) / (tanHalf * aspect);
        double ny = (cyv / cz) / tanHalf;
        out[0] = (float) ((nx + 1) * 0.5 * w);
        out[1] = (float) ((1 - ny) * 0.5 * h);
        out[2] = (float) cz;
        return true;
    }

    /** Screen Y of the horizon line. */
    public static float horizonY() { return elevY(0); }

    /** Screen Y of a line at the given elevation angle (degrees above horizon). */
    public static float elevY(double deg) {
        double ny = Math.tan(Math.toRadians(pitchDeg - deg)) / tanHalf;
        return (float) ((1 - ny) * 0.5 * h);
    }

    /** Pixels per block at the given depth (for sizing markers/shadows). */
    public static float pixelsPerBlock(double depth) {
        return (float) (h / (2.0 * tanHalf * Math.max(0.1, depth)));
    }

    /** Compass bearing 0..360 (0 = north / -Z) of the view direction. */
    public static float bearing() { return ((yawDeg + 180f) % 360f + 360f) % 360f; }

    public static float bearingTo(double dx, double dz) {
        return (float) (((Math.toDegrees(Math.atan2(dx, -dz))) % 360 + 360) % 360);
    }

    public static String cardinal(float deg) {
        String[] n = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
        return n[Math.floorMod(Math.round(deg / 45f), 8)];
    }
}
