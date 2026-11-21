package com.militaryclient.fx;

import com.militaryclient.audio.AudioEngine;
import com.militaryclient.config.Config;
import com.militaryclient.config.Waypoint;
import com.militaryclient.hud.Tracker;
import com.militaryclient.util.Dim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/** Cosmetic particles and weather visuals. Counts are capped per tick and scaled by the global multiplier. */
public final class Atmosphere {
    public static float flashAlpha;
    public static int flashColor = 0xFFFFFFFF;

    private static final Random R = new Random();
    private static long tick, nextBolt, thunderAt = -1;
    private static boolean outdoors;
    private static final double[][] plumes = new double[4][2];
    private static int plumeCount;
    private static double plumeOx, plumeOz;
    private static long plumeBorn;

    private Atmosphere() {}

    public static void flash(int color, float alpha) {
        flashColor = color;
        flashAlpha = Math.max(flashAlpha, alpha);
    }

    private static int count(double expected) {
        int n = (int) expected;
        if (R.nextDouble() < expected - n) n++;
        return n;
    }

    private static int ground(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }

    private static void add(ClientLevel l, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        l.addParticle(o, x, y, z, vx, vy, vz);
    }

    private static void addFar(ClientLevel l, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        l.addAlwaysVisibleParticle(o, x, y, z, vx, vy, vz);
    }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        flashAlpha *= 0.82f;
        if (flashAlpha < 0.01f) flashAlpha = 0;
        if (p == null || level == null || mc.isPaused()) return;
        tick++;
        double pm = Config.n("global", "particles") * (Config.b("global", "low_perf") ? 0.5 : 1.0);
        if (pm <= 0) return;
        if (tick % 10 == 0) outdoors = level.canSeeSky(p.blockPosition());

        if (Config.on("dust_particles")) dust(level, p, pm);
        if (Config.on("smoke_effects")) smoke(level, p, pm);
        if (Config.on("improved_rain")) weather(level, p, pm);
        if (Config.on("aircraft_contrails")) contrails(level, p, pm);
        if (Config.on("runway_lighting")) runway(level, p, pm);
        if (Config.on("warning_lights") && Config.b("warning_lights", "world_beacons")) beacons(level, p);
    }

    private static void dust(ClientLevel l, LocalPlayer p, double pm) {
        if (Config.b("dust_particles", "outdoors") && !outdoors) return;
        double r = Config.n("dust_particles", "radius");
        int n = count(Config.n("dust_particles", "density") * pm * 4);
        ParticleOptions type = Config.c("dust_particles", "type") == 0 ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH;
        double ang = tick * 0.0007;
        for (int i = 0; i < n; i++) {
            double x = p.getX() + (R.nextDouble() * 2 - 1) * r, z = p.getZ() + (R.nextDouble() * 2 - 1) * r;
            double y = p.getY() + R.nextDouble() * 6 - 1.5;
            add(l, type, x, y, z, Math.cos(ang) * 0.03 + (R.nextDouble() - 0.5) * 0.01, -0.002, Math.sin(ang) * 0.03);
        }
    }

    private static void smoke(ClientLevel l, LocalPlayer p, double pm) {
        double r = Config.n("smoke_effects", "radius");
        int style = Config.c("smoke_effects", "style");
        double density = Config.n("smoke_effects", "density"), vis = Config.n("smoke_effects", "visibility");
        if (plumeCount == 0 || tick - plumeBorn > 3000 || Math.hypot(p.getX() - plumeOx, p.getZ() - plumeOz) > r * 2) {
            plumeCount = style == 1 ? 0 : (style == 2 ? 2 : 3);
            plumeOx = p.getX(); plumeOz = p.getZ(); plumeBorn = tick;
            for (int i = 0; i < plumeCount; i++) {
                double a = R.nextDouble() * Math.PI * 2, d = r * (0.5 + R.nextDouble() * 0.5);
                plumes[i][0] = p.getX() + Math.cos(a) * d;
                plumes[i][1] = p.getZ() + Math.sin(a) * d;
            }
        }
        for (int i = 0; i < plumeCount; i++) {
            if (!l.hasChunk((int) plumes[i][0] >> 4, (int) plumes[i][1] >> 4)) continue;
            int n = count(density * pm * (style == 2 ? 2.0 : 1.0));
            double y = ground(l, plumes[i][0], plumes[i][1]);
            for (int k = 0; k < n; k++) {
                ParticleOptions t = R.nextDouble() < vis * 0.6 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE;
                addFar(l, t, plumes[i][0] + R.nextGaussian() * 0.6, y + 0.5, plumes[i][1] + R.nextGaussian() * 0.6,
                    (R.nextDouble() - 0.5) * 0.01, 0.07 + R.nextDouble() * 0.03, (R.nextDouble() - 0.5) * 0.01);
            }
        }
        if (style == 1) { // battle haze: low drifting smoke around the player
            int n = count(density * pm * 2.5);
            for (int k = 0; k < n; k++) {
                double x = p.getX() + (R.nextDouble() * 2 - 1) * r, z = p.getZ() + (R.nextDouble() * 2 - 1) * r;
                add(l, R.nextDouble() < vis ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, ground(l, x, z) + 0.4,
                    z, 0.02, 0.005, 0.01);
            }
        }
    }

    private static void weather(ClientLevel l, LocalPlayer p, double pm) {
        if (!l.isRaining()) return;
        int n = count(Config.n("improved_rain", "splash") * pm * 6);
        for (int i = 0; i < n; i++) {
            double x = p.getX() + (R.nextDouble() * 2 - 1) * 14, z = p.getZ() + (R.nextDouble() * 2 - 1) * 14;
            add(l, ParticleTypes.RAIN, x, ground(l, x, z) + 0.1, z, 0, 0, 0);
        }
        if (l.isThundering() && Config.b("improved_rain", "lightning") && tick >= nextBolt) {
            nextBolt = tick + 100 + R.nextInt(300);
            flash(0xFFE8F0FF, 0.55f);
            if (Config.b("improved_rain", "thunder")) thunderAt = tick + 10 + R.nextInt(80);
        }
        if (thunderAt > 0 && tick >= thunderAt) {
            thunderAt = -1;
            AudioEngine.thunder(Config.n("improved_rain", "thunder_volume"));
        }
    }

    private static void contrails(ClientLevel l, LocalPlayer p, double pm) {
        int len = Config.i("aircraft_contrails", "length");
        double dens = Config.n("aircraft_contrails", "density");
        int miny = Config.i("aircraft_contrails", "min_y");
        ParticleOptions type = switch (Config.c("aircraft_contrails", "particle")) {
            case 1 -> ParticleTypes.SMOKE;
            case 2 -> ParticleTypes.POOF;
            default -> ParticleTypes.CLOUD;
        };
        int budget = 40;
        for (Tracker.Obj o : Tracker.AIR) {
            if (budget <= 0) break;
            if (o.speed < 3 || o.y < miny || o.dist > 128) continue;
            int n = Math.min(budget, Math.max(1, (int) Math.round(len * dens * pm)));
            budget -= n;
            for (int i = 0; i < n; i++) {
                double t = R.nextDouble();
                addFar(l, type, o.lastX + (o.x - o.lastX) * t, o.lastY + (o.y - o.lastY) * t + 0.3, o.lastZ + (o.z - o.lastZ) * t,
                    0, 0, 0);
            }
        }
    }

    private static Waypoint[] runway(String dim) {
        Waypoint a = null, b = null;
        for (Waypoint w : Config.waypoints()) {
            if (!w.dim.equals(dim)) continue;
            if (w.type == Waypoint.RWY_A && a == null) a = w;
            if (w.type == Waypoint.RWY_B && b == null) b = w;
        }
        return a != null && b != null ? new Waypoint[] { a, b } : null;
    }

    private static void runway(ClientLevel l, LocalPlayer p, double pm) {
        Waypoint[] rw = runway(Dim.current(l));
        if (rw == null) return;
        double dx = rw[1].x - rw[0].x, dz = rw[1].z - rw[0].z, len = Math.hypot(dx, dz);
        if (len < 8 || len > Config.n("runway_lighting", "max_length")) return;
        double cx = (rw[0].x + rw[1].x) / 2, cz = (rw[0].z + rw[1].z) / 2;
        if (Math.hypot(p.getX() - cx, p.getZ() - cz) > len / 2 + 120) return;
        double ux = dx / len, uz = dz / len, nx = -uz, nz = ux;
        int spacing = Config.i("runway_lighting", "spacing");
        int count = (int) (len / spacing) + 1;
        double bright = Config.n("runway_lighting", "brightness");
        double speed = Config.n("runway_lighting", "speed");
        if (tick % 8 == 0) { // steady edge lights
            for (int i = 0; i < count; i++) {
                for (int side = -1; side <= 1; side += 2) {
                    if (R.nextDouble() > bright * pm) continue;
                    double x = rw[0].x + ux * i * spacing + nx * 6 * side, z = rw[0].z + uz * i * spacing + nz * 6 * side;
                    if (!l.hasChunk((int) x >> 4, (int) z >> 4)) continue;
                    addFar(l, i == 0 || i == count - 1 ? ParticleTypes.FLAME : ParticleTypes.END_ROD, x, ground(l, x, z) + 0.3, z, 0, 0, 0);
                }
            }
        }
        if (Config.b("runway_lighting", "strobe") && tick % 2 == 0) { // running "rabbit" sequence along the centreline
            int idx = (int) ((tick * speed / 2.0) % count);
            for (int k = 0; k < 2; k++) {
                int i = (idx + k) % count;
                double x = rw[0].x + ux * i * spacing, z = rw[0].z + uz * i * spacing;
                if (!l.hasChunk((int) x >> 4, (int) z >> 4)) continue;
                addFar(l, ParticleTypes.END_ROD, x, ground(l, x, z) + 0.5, z, 0, 0.01, 0);
            }
        }
    }

    private static void beacons(ClientLevel l, LocalPlayer p) {
        double speed = Config.n("warning_lights", "speed");
        int period = Math.max(4, (int) (20 / speed));
        if ((tick % period) >= Math.max(2, period / 3)) return;
        String dim = Dim.current(l);
        ParticleOptions t = switch (Config.c("warning_lights", "color")) {
            case 2 -> ParticleTypes.SOUL_FIRE_FLAME;
            case 3 -> ParticleTypes.END_ROD;
            default -> ParticleTypes.FLAME;
        };
        for (Waypoint w : Config.waypoints()) {
            if (w.type != Waypoint.HAZARD || !w.dim.equals(dim)) continue;
            if (Math.hypot(p.getX() - w.x, p.getZ() - w.z) > 128) continue;
            for (int i = 0; i < 3; i++)
                addFar(l, t, w.x + 0.5 + R.nextGaussian() * 0.15, w.y + 2.5 + i * 0.3, w.z + 0.5 + R.nextGaussian() * 0.15, 0, 0.02, 0);
        }
    }
}
