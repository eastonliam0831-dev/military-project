package com.militaryclient.hud;

import com.militaryclient.config.Config;
import com.militaryclient.util.Proj;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Classifies loaded entities. Everything here is information the client already has. Creatures are only
 * exposed to radar/thermal when the player has line of sight, so these overlays cannot be used as X-ray.
 */
public final class Tracker {
    public static final int AIRCRAFT = 1, VEHICLE = 2, LIVING = 3;

    public static final class Obj {
        public Entity e;
        public int id, kind;
        public String type = "", path = "";
        public double x, y, z, dist, speed, heading, climb, closing, panHint;
        public boolean los, hostile, player;
        public double lastX, lastY, lastZ, lastDist;
        long lastTick;
        boolean seen;
    }

    public static final List<Obj> AIR = new ArrayList<>();
    public static final List<Obj> VEH = new ArrayList<>();
    public static final List<Obj> VISIBLE = new ArrayList<>();
    private static final Map<Integer, Obj> MAP = new HashMap<>();
    private static long tick;
    private static String kwCache = "", vkwCache = "";
    private static String[] kw = new String[0], vkw = new String[0];

    private Tracker() {}

    private static String[] split(String s) {
        List<String> out = new ArrayList<>();
        for (String p : s.toLowerCase().split(",")) {
            p = p.trim();
            if (!p.isEmpty()) out.add(p);
        }
        return out.toArray(new String[0]);
    }

    private static boolean matches(String[] keys, String path) {
        for (String k : keys) if (path.contains(k)) return true;
        return false;
    }

    public static void clear() {
        AIR.clear(); VEH.clear(); VISIBLE.clear(); MAP.clear();
    }

    public static boolean needed() {
        return Config.on("aircraft_spotting") || Config.on("aircraft_labels") || Config.on("aircraft_contrails")
            || Config.on("vehicle_shadows") || Config.on("aircraft_engine_audio") || Config.on("vehicle_inspection")
            || Config.on("aircraft_camera") || Config.on("aircraft_cards") || Config.on("animated_vehicle_displays")
            || Config.on("thermal_filter") || Config.on("warning_lights")
            || (Config.on("aircraft_radar") && Config.c("aircraft_radar", "mode") == 1);
    }

    public static void tick(Minecraft mc) {
        tick++;
        if ((tick & 1) != 0) return;
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || !needed()) { clear(); return; }

        String k1 = Config.t("tracking", "keywords"), k2 = Config.t("tracking", "vehicle_keywords");
        if (!k1.equals(kwCache)) { kwCache = k1; kw = split(k1); }
        if (!k2.equals(vkwCache)) { vkwCache = k2; vkw = split(k2); }
        boolean vanilla = Config.b("tracking", "vanilla_flyers");
        double range = Config.n("tracking", "range");
        double visRange = Math.max(Config.n("thermal_filter", "range"), Config.n("aircraft_radar", "range"));

        for (Obj o : MAP.values()) o.seen = false;
        AIR.clear(); VEH.clear(); VISIBLE.clear();
        int losBudget = 24;

        AABB box = p.getBoundingBox().inflate(range);
        for (Entity e : level.getEntities((Entity) null, box)) {
            if (e == p || e.isRemoved()) continue;
            String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            boolean air = matches(kw, path);
            if (!air && vanilla) {
                air = path.equals("phantom") || path.equals("ender_dragon") || path.equals("ghast")
                    || path.equals("happy_ghast") || (e instanceof LivingEntity le && le.isFallFlying());
            }
            boolean veh = !air && (path.contains("boat") || path.contains("raft") || path.contains("minecart")
                || matches(vkw, path));
            boolean living = e instanceof LivingEntity;
            double dist = e.distanceTo(p);
            boolean wantVisible = living && !air && dist <= visRange;
            if (!air && !veh && !wantVisible) continue;

            Obj o = MAP.computeIfAbsent(e.getId(), id -> new Obj());
            boolean fresh = o.e == null || o.e != e;
            o.e = e;
            o.id = e.getId();
            o.path = path;
            o.type = e.getType().getDescription().getString();
            o.kind = air ? AIRCRAFT : veh ? VEHICLE : LIVING;
            o.x = e.getX(); o.y = e.getY(); o.z = e.getZ();
            o.dist = dist;
            o.hostile = e instanceof Enemy;
            o.player = e instanceof Player;
            double dt = fresh ? 0 : (tick - o.lastTick) / 20.0;
            if (dt > 0) {
                double dx = o.x - o.lastX, dy = o.y - o.lastY, dz = o.z - o.lastZ;
                o.speed = Math.sqrt(dx * dx + dy * dy + dz * dz) / dt;
                o.climb = dy / dt;
                o.closing = (o.lastDist - dist) / dt;
                if (Math.abs(dx) + Math.abs(dz) > 0.05) o.heading = Proj.bearingTo(dx, dz);
            }
            o.lastX = o.x; o.lastY = o.y; o.lastZ = o.z; o.lastDist = dist; o.lastTick = tick;
            double hx = o.x - p.getX(), hz = o.z - p.getZ(), hd = Math.max(0.001, Math.sqrt(hx * hx + hz * hz));
            double yaw = Math.toRadians(p.getYRot());
            o.panHint = (hx * -Math.cos(yaw) + hz * -Math.sin(yaw)) / hd;
            o.seen = true;

            if (losBudget > 0 && (air || wantVisible || Config.on("vehicle_shadows"))) {
                o.los = p.hasLineOfSight(e);
                losBudget--;
            }
            if (air) AIR.add(o);
            else if (veh) VEH.add(o);
            if (wantVisible && o.los) VISIBLE.add(o);
        }
        Set<Integer> drop = new HashSet<>();
        for (Map.Entry<Integer, Obj> en : MAP.entrySet()) if (!en.getValue().seen) drop.add(en.getKey());
        for (Integer id : drop) MAP.remove(id);
        AIR.sort((a, b) -> Double.compare(a.dist, b.dist));
        VEH.sort((a, b) -> Double.compare(a.dist, b.dist));
    }

    /** Object whose projected position is closest to screen centre, within maxPx. Requires Proj.begin. */
    public static Obj nearestToCrosshair(List<Obj> list, double maxPx, boolean requireLos) {
        Obj best = null;
        double bd = maxPx * maxPx;
        float[] out = new float[3];
        for (Obj o : list) {
            if (requireLos && !o.los) continue;
            if (!Proj.project(o.x, o.y + o.e.getBbHeight() * 0.5, o.z, out)) continue;
            double dx = out[0] - Proj.w / 2.0, dy = out[1] - Proj.h / 2.0, d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }
}
