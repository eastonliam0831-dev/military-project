package com.militaryclient.hud;

import com.militaryclient.config.Config;
import com.militaryclient.config.Features;
import com.militaryclient.config.Waypoint;
import com.militaryclient.fx.Atmosphere;
import com.militaryclient.util.Colors;
import com.militaryclient.util.Dim;
import com.militaryclient.util.Gfx;
import com.militaryclient.util.Proj;
import com.mojang.logging.LogUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Draws every on-screen feature. Each feature is isolated: an exception disables only that feature. */
public final class HudLayer {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<String> BROKEN = new HashSet<>();

    // per-frame context
    private static GuiGraphicsExtractor g;
    private static Minecraft mc;
    private static LocalPlayer p;
    private static ClientLevel level;
    private static int W, H, accent;
    private static float gop, hs, pt;
    private static double time;
    private static boolean fp;
    private static final float[] P = new float[3];
    private static final float[] P2 = new float[3];

    private static float hangarAlpha;
    private static int panelW, panelH;

    // signage state
    private static String zoneName;
    private static boolean zoneInside;
    private static String banner1 = "", banner2 = "";
    private static double bannerUntil, bannerFrom;
    private static int bannerType;

    private HudLayer() {}

    public static void init() {
        Config.LISTENERS.add(k -> {
            if (k.equals("*")) BROKEN.clear();
            else BROKEN.remove(k.contains(".") ? k.substring(0, k.indexOf('.')) : k);
            Feature_clear(k);
        });
    }

    private static void Feature_clear(String k) {
        String f = k.contains(".") ? k.substring(0, k.indexOf('.')) : k;
        if (k.equals("*")) { for (var x : Features.ALL) x.status = null; }
        else if (Features.get(f) != null) Features.get(f).status = null;
    }

    private interface Draw { void run() throws Exception; }

    private static void safe(String key, Draw d) {
        if (!Config.on(key) || BROKEN.contains(key)) return;
        try {
            d.run();
        } catch (Throwable t) {
            BROKEN.add(key);
            String msg = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
            Features.get(key).status = "Stopped after an error (" + msg + "). Toggle it off and on to retry.";
            LOG.error("[MilitaryClient] Feature '{}' failed and was stopped", key, t);
        }
    }

    // =================================================================== frame entry

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker dt) {
        mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        g = graphics; p = mc.player; level = mc.level;
        pt = dt.getGameTimeDeltaPartialTick(false);
        Gfx.refreshFromConfig();
        time = System.nanoTime() / 1.0e9 * Config.n("global", "anim_speed");
        Proj.begin(mc, pt);
        if (!Proj.valid) return;
        W = Proj.w; H = Proj.h;
        accent = Config.accent();
        gop = Gfx.globalOpacity();
        hs = Config.f("global", "hud_scale");
        fp = mc.options.getCameraType().isFirstPerson();

        // full-screen atmosphere
        safe("realistic_fog", HudLayer::fog);
        safe("improved_rain", HudLayer::storm);
        safe("hangar_lighting", HudLayer::hangar);
        safe("night_vision", HudLayer::nightVision);
        safe("thermal_filter", HudLayer::thermal);
        if (Atmosphere.flashAlpha > 0) Gfx.rect(g, 0, 0, W, H, Colors.alpha(Atmosphere.flashColor, (int) (Atmosphere.flashAlpha * 255)));
        if (fp && Cosmetics.anyFirstPerson()) Cosmetics.firstPerson(g, W, H, time);

        // projected markers
        if (fp) {
            safe("vehicle_shadows", HudLayer::shadows);
            safe("waypoint_markers", HudLayer::waypointMarkers);
            safe("aircraft_labels", HudLayer::aircraftLabels);
        }

        // panels
        safe("tactical_hud", HudLayer::tacticalHud);
        safe("gps_interface", HudLayer::gps);
        safe("altitude_display", HudLayer::altitude);
        safe("map_overlay", HudLayer::mapOverlay);
        safe("aircraft_radar", HudLayer::radar);
        safe("aircraft_spotting", HudLayer::spotting);
        safe("vehicle_inspection", HudLayer::vehicleInspection);
        safe("aircraft_cards", HudLayer::aircraftCard);
        safe("animated_vehicle_displays", HudLayer::vehicleDashboard);
        safe("military_radio_gui", HudLayer::radioGui);

        // centre elements
        safe("digital_compass", HudLayer::compass);
        safe("targeting_display", HudLayer::targeting);
        safe("aircraft_camera", HudLayer::aircraftCamera);
        safe("binocular_overlay", HudLayer::binoculars);
        safe("warning_lights", HudLayer::warningLights);
        safe("military_signage", HudLayer::signage);
    }

    // =================================================================== helpers

    private static float[] place(String fk, int w, int h, float featureScale) {
        float s = hs * featureScale;
        float x = W * Config.f(fk, "x") / 100f, y = H * Config.f(fk, "y") / 100f;
        x = Math.max(0, Math.min(x, W - w * s));
        y = Math.max(0, Math.min(y, H - h * s));
        return new float[] { x, y, s };
    }

    private static String f1(double v) { return String.format("%.1f", v); }

    private static String bearing3(float deg) { return String.format("%03d", Math.round(deg) % 360); }

    private static int tw(String s) { return Gfx.textWS(s, 1f); }

    private static int th() { return Gfx.textHS(1f); }

    private static int line() { return th() + 3; }

    /** Standard multi-line panel. Title in accent colour. */
    private static void panel(String fk, String title, List<String> lines, Runnable overlay) {
        int w = tw(title);
        for (String l : lines) w = Math.max(w, tw(l));
        w += 14;
        int h = line() * (lines.size() + 1) + 8;
        float fs = Config.f(fk, "scale");
        float[] pl = place(fk, w, h, fs);
        float op = Config.f(fk, "opacity") * gop;
        int col = Config.col(fk, "color");
        Gfx.begin(g, pl[0], pl[1], pl[2]);
        Gfx.plate(g, w, h, col, op);
        Gfx.text(g, title, 8, 4, Colors.mulA(col, op), 1f);
        int y = 4 + line();
        for (String l : lines) { Gfx.text(g, l, 8, y, Colors.mulA(0xFFEDEFE6, Math.max(0.5f, op)), 1f); y += line(); }
        panelW = w; panelH = h;
        if (overlay != null) overlay.run();
        Gfx.end(g);
    }

    private static int groundY(double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }

    private static boolean sameDim(Waypoint w) { return w.dim.equals(Dim.current(level)); }

    // =================================================================== screen filters

    private static void fog() {
        double d = Config.n("realistic_fog", "density") * Config.n("global", "fog");
        d = Math.min(1.0, d);
        if (d <= 0.001) return;
        int[] cols = { 0xFFC4C9CC, 0xFFE8DCC8, 0xFFC9A878, 0xFF0E121A, 0xFF9BC94A };
        int c = cols[Config.c("realistic_fog", "color")];
        double dist = Config.n("realistic_fog", "distance");
        int hy = (int) Proj.horizonY();
        int band = (int) (H * (0.12 + 0.55 * (1 - (dist - 8) / 92.0)));
        int top = (int) (255 * d * 0.85), bottomA = (int) (255 * d * 0.55);
        if (Config.c("realistic_fog", "mode") == 1) Gfx.rect(g, 0, 0, W, H, Colors.alpha(c, (int) (255 * d * 0.35)));
        int y1 = Math.max(0, hy - band), y2 = Math.min(H, hy);
        if (y2 > y1) Gfx.gradientV(g, 0, y1, W, y2 - y1, Colors.alpha(c, 0), Colors.alpha(c, top));
        int y3 = Math.max(0, hy), y4 = Math.min(H, hy + (int) (band * 0.9));
        if (y4 > y3) Gfx.gradientV(g, 0, y3, W, y4 - y3, Colors.alpha(c, top), Colors.alpha(c, bottomA / 2));
        if (hy + band * 0.9 < H && hy >= 0) {
            int ys = Math.max(0, (int) (hy + band * 0.9));
            if (ys < H) Gfx.rect(g, 0, ys, W, H - ys, Colors.alpha(c, bottomA / 2 > 0 ? (int) (255 * d * 0.12) : 0));
        } else if (hy < 0) {
            Gfx.rect(g, 0, 0, W, H, Colors.alpha(c, (int) (255 * d * 0.25)));
        }
    }

    private static void storm() {
        if (!level.isRaining()) return;
        float rain = level.getRainLevel(pt);
        float a = (float) Config.n("improved_rain", "storm_dark") * rain;
        if (a > 0.01f) Gfx.rect(g, 0, 0, W, H, Colors.alpha(0xFF0A1018, (int) (a * 255)));
    }

    private static void hangar() {
        boolean indoors = !level.canSeeSky(p.blockPosition());
        hangarAlpha += ((indoors ? 1f : 0f) - hangarAlpha) * 0.06f;
        if (hangarAlpha < 0.02f) return;
        float k = (float) Config.n("hangar_lighting", "intensity") * hangarAlpha;
        int[] tones = { 0xFFB8D4FF, 0xFFFFC36A, 0xFFE6E6E6 };
        int tone = tones[Config.c("hangar_lighting", "tone")];
        Gfx.rect(g, 0, 0, W, H, Colors.alpha(tone, (int) (k * 40)));
        Gfx.gradientV(g, 0, 0, W, H / 4, Colors.alpha(0xFF000000, (int) (k * 90)), 0x00000000);
        if (Config.b("hangar_lighting", "strips")) {
            for (int i = 0; i < 3; i++) {
                int cx = (int) (W * (0.2 + 0.3 * i)), sw = (int) (W * 0.17);
                double fl = Config.b("hangar_lighting", "flicker") ? (0.85 + 0.15 * Math.sin(time * 17 + i * 3) * Math.sin(time * 5.3 + i)) : 1;
                Gfx.rect(g, cx - sw / 2, 2, sw, 3, Colors.alpha(0xFFFFFFFF, (int) (k * 255 * fl)));
                Gfx.gradientV(g, cx - sw / 2, 5, sw, H / 8, Colors.alpha(tone, (int) (k * 70 * fl)), Colors.alpha(tone, 0));
            }
        }
    }

    private static void scanlines(int alpha, int step) {
        for (int y = 0; y < H; y += step) Gfx.rect(g, 0, y, W, 1, Colors.alpha(0xFF000000, alpha));
    }

    private static void vignette(float strength) {
        int a = (int) (255 * strength);
        int e = (int) (Math.min(W, H) * 0.22);
        g.fillGradient(0, 0, W, e, Colors.alpha(0xFF000000, a), 0x00000000);
        g.fillGradient(0, H - e, W, H, 0x00000000, Colors.alpha(0xFF000000, a));
        Gfx.rect(g, 0, 0, e / 2, H, Colors.alpha(0xFF000000, a / 2));
        Gfx.rect(g, W - e / 2, 0, e / 2, H, Colors.alpha(0xFF000000, a / 2));
    }

    private static final Random NOISE = new Random();

    private static void nightVision() {
        int[] tones = { 0xFF30FF50, 0xFFFFB030, 0xFFE8F0FF };
        int tone = tones[Config.c("night_vision", "tone")];
        float t = (float) Config.n("night_vision", "tint");
        Gfx.rect(g, 0, 0, W, H, Colors.alpha(tone, (int) (t * 150)));
        if (Config.b("night_vision", "vignette")) vignette(0.65f);
        if (Config.b("night_vision", "scanlines")) scanlines(28, 3);
        if (Config.b("night_vision", "noise")) {
            int n = Config.b("global", "low_perf") ? 30 : 90;
            for (int i = 0; i < n; i++) Gfx.rect(g, NOISE.nextInt(W), NOISE.nextInt(H), 1 + NOISE.nextInt(2), 1, Colors.alpha(tone, 70));
        }
    }

    private static int heat(int palette, float t) { // t: 0 cold .. 1 hot
        switch (palette) {
            case 1: return Colors.rgb((int) (60 + 195 * t), (int) (60 + 195 * t), (int) (60 + 195 * t));
            case 2: return Colors.rgb((int) (255 - 200 * t), (int) (255 - 200 * t), (int) (255 - 200 * t));
            case 3: return t > 0.66f ? 0xFFFF3030 : t > 0.33f ? 0xFF30FF60 : 0xFF3050FF;
            default: return t > 0.8f ? 0xFFFFFFFF : t > 0.55f ? 0xFFFFD040 : t > 0.3f ? 0xFFE04820 : 0xFF6A1A8A;
        }
    }

    private static void thermal() {
        int pal = Config.c("thermal_filter", "palette");
        float k = (float) Config.n("thermal_filter", "intensity");
        int cold = heat(pal, 0.05f), warm = heat(pal, pal == 0 ? 0.4f : 0.5f);
        g.fillGradient(0, 0, W, H, Colors.alpha(cold, (int) (k * 190)), Colors.alpha(warm, (int) (k * 190)));
        if (Config.b("thermal_filter", "scanlines")) scanlines(22, 2);
        if (Config.b("thermal_filter", "heat_boxes") && fp) {
            double range = Config.n("thermal_filter", "range");
            for (Tracker.Obj o : Tracker.VISIBLE) {
                if (o.dist > range || !(o.e instanceof LivingEntity)) continue;
                double ex = lerp(o.e.xo, o.e.getX()), ey = lerp(o.e.yo, o.e.getY()), ez = lerp(o.e.zo, o.e.getZ());
                float bh = o.e.getBbHeight(), bw = o.e.getBbWidth();
                if (!Proj.project(ex, ey, ez, P) || !Proj.project(ex, ey + bh, ez, P2)) continue;
                float ppb = Proj.pixelsPerBlock(P[2]);
                int half = Math.max(2, (int) (bw * ppb * 0.5f));
                int x1 = (int) P[0] - half, y1 = (int) P2[1], x2 = (int) P[0] + half, y2 = (int) P[1];
                float t = o.hostile || o.player ? 1f : 0.7f;
                int c = heat(pal, t);
                Gfx.rect(g, x1, y1, x2 - x1, y2 - y1, Colors.alpha(c, 95));
                Gfx.frame(g, x1, y1, x2 - x1, y2 - y1, Colors.alpha(heat(pal, 0.9f), 200));
                Gfx.rect(g, (int) P[0] - half / 2, y1, Math.max(2, half), Math.max(2, (y2 - y1) / 5), Colors.alpha(heat(pal, 1f), 190));
            }
        }
    }

    private static double lerp(double a, double b) { return a + (b - a) * pt; }

    // =================================================================== 3D markers

    private static void shadows() {
        double op = Config.n("vehicle_shadows", "opacity"), size = Config.n("vehicle_shadows", "size");
        List<Tracker.Obj> all = new ArrayList<>();
        if (Config.b("vehicle_shadows", "vehicles")) all.addAll(Tracker.VEH);
        if (Config.b("vehicle_shadows", "aircraft")) all.addAll(Tracker.AIR);
        int n = 0;
        for (Tracker.Obj o : all) {
            if (!o.los || o.dist > 96 || n++ > 12) continue;
            double ex = lerp(o.e.xo, o.e.getX()), ez = lerp(o.e.zo, o.e.getZ()), ey = lerp(o.e.yo, o.e.getY());
            double gy = groundY(ex, ez);
            double height = Math.max(0, ey - gy);
            if (height > 120) continue;
            if (!Proj.project(ex, gy + 0.03, ez, P)) continue;
            float ppb = Proj.pixelsPerBlock(P[2]);
            double rx = Math.max(2, o.e.getBbWidth() * 0.65 * size * ppb * (1 + height * 0.01));
            double elev = Math.toDegrees(Math.atan2(Proj.ey - gy, Math.max(1, Math.hypot(ex - Proj.ex, ez - Proj.ez))));
            double flat = Math.max(0.15, Math.min(0.8, 0.2 + 0.5 * Math.sin(Math.toRadians(Math.min(80, Math.abs(elev))))));
            float a = (float) (op * Math.max(0.15, 1 - height / 100.0));
            Gfx.ellipse(g, (int) P[0], (int) P[1], (int) rx, (int) (rx * flat), Colors.alpha(0xFF000000, (int) (a * 110)));
            Gfx.ellipse(g, (int) P[0], (int) P[1], (int) (rx * 0.7), (int) (rx * flat * 0.7), Colors.alpha(0xFF000000, (int) (a * 150)));
        }
    }

    private static void waypointMarkers() {
        int size = Config.i("waypoint_markers", "size"), maxd = Config.i("waypoint_markers", "max_distance");
        int col = Config.col("waypoint_markers", "color"), style = Config.c("waypoint_markers", "style");
        boolean fade = Config.b("waypoint_markers", "fade");
        for (Waypoint w : Config.waypoints()) {
            if (!sameDim(w)) continue;
            double d = Math.sqrt(sq(w.x - p.getX()) + sq(w.y - p.getY()) + sq(w.z - p.getZ()));
            if (d > maxd) continue;
            if (!Proj.project(w.x + 0.5, w.y + 1.5, w.z + 0.5, P)) continue;
            int x = (int) P[0], y = (int) P[1];
            if (x < -20 || x > W + 20 || y < -20 || y > H + 20) continue;
            float a = fade ? (float) Math.max(0.35, 1 - d / maxd * 0.65) : 1f;
            a *= gop;
            int c = Colors.mulA(wpColor(w, col), a);
            int s = size;
            if (style == 0) { // diamond
                Gfx.line(g, x, y - s / 2, x + s / 2, y, c); Gfx.line(g, x + s / 2, y, x, y + s / 2, c);
                Gfx.line(g, x, y + s / 2, x - s / 2, y, c); Gfx.line(g, x - s / 2, y, x, y - s / 2, c);
            } else if (style == 1) { // chevron
                Gfx.line(g, x - s / 2, y - s / 4, x, y + s / 4, c); Gfx.line(g, x, y + s / 4, x + s / 2, y - s / 4, c);
                Gfx.line(g, x - s / 2, y - s / 4 - 1, x, y + s / 4 - 1, c); Gfx.line(g, x, y + s / 4 - 1, x + s / 2, y - s / 4 - 1, c);
            } else { // pin
                Gfx.disc(g, x, y - s / 3, Math.max(2, s / 3), c); Gfx.line(g, x, y - s / 3, x, y + s / 2, c);
            }
            int ty = y - s / 2 - th() - 3;
            if (Config.b("waypoint_markers", "name")) {
                Gfx.text(g, w.name, x - tw(w.name) / 2, ty, c, 0.9f); ty -= th();
            }
            if (Config.b("waypoint_markers", "distance")) {
                String ds = d >= 1000 ? f1(d / 1000) + "KM" : (int) d + "M";
                Gfx.text(g, ds, x - tw(ds) / 2, y + s / 2 + 3, c, 0.8f);
            }
        }
    }

    private static int wpColor(Waypoint w, int theme) {
        switch (w.type) {
            case Waypoint.RESTRICTED: return 0xFFFF4A3A;
            case Waypoint.HAZARD: return 0xFFFFB000;
            case Waypoint.LZ: return 0xFF40E0FF;
            case Waypoint.RWY_A: case Waypoint.RWY_B: return 0xFFEDEFE6;
            default: return theme;
        }
    }

    private static double sq(double v) { return v * v; }

    private static void aircraftLabels() {
        int max = Config.i("aircraft_labels", "max_labels");
        float sc = Config.f("aircraft_labels", "scale");
        int col = Config.col("aircraft_labels", "color");
        float op = Config.f("aircraft_labels", "opacity") * gop;
        int n = 0;
        for (Tracker.Obj o : Tracker.AIR) {
            if (n >= max) break;
            if (!o.los) continue;
            if (!Proj.project(lerp(o.e.xo, o.e.getX()), lerp(o.e.yo, o.e.getY()) + o.e.getBbHeight() + 0.6, lerp(o.e.zo, o.e.getZ()), P)) continue;
            n++;
            int x = (int) P[0], y = (int) P[1];
            List<String> ls = new ArrayList<>();
            if (Config.b("aircraft_labels", "type")) ls.add(o.type.toUpperCase());
            StringBuilder sb = new StringBuilder();
            if (Config.b("aircraft_labels", "distance")) sb.append((int) o.dist).append("M ");
            if (Config.b("aircraft_labels", "altitude")) sb.append("ALT").append((int) o.y).append(' ');
            if (Config.b("aircraft_labels", "speed")) sb.append((int) o.speed).append("M/S");
            if (sb.length() > 0) ls.add(sb.toString().trim());
            int yy = y - ls.size() * (int) (th() * sc + 2);
            for (String l : ls) {
                int w = Gfx.textWS(l, sc);
                Gfx.text(g, l, x - w / 2, yy, Colors.mulA(col, op), sc);
                yy += (int) (th() * sc + 2);
            }
            Gfx.frame(g, x - 4, y + 2, 8, 8, Colors.mulA(col, op));
        }
    }

    // =================================================================== panels

    private static void tacticalHud() {
        List<String> ls = new ArrayList<>();
        String header = Cosmetics.hudHeader();
        if (Config.b("tactical_hud", "coords")) ls.add(String.format("POS %d %d %d", p.getBlockX(), p.getBlockY(), p.getBlockZ()));
        if (Config.b("tactical_hud", "direction")) { float b = Proj.bearing(); ls.add("HDG " + bearing3(b) + "^ " + Proj.cardinal(b)); }
        if (Config.b("tactical_hud", "fps")) ls.add("FPS " + mc.getFps());
        if (Config.b("tactical_hud", "time")) {
            long t = (level.getDayTime() % 24000 + 24000) % 24000;
            int hr = (int) ((t / 1000 + 6) % 24), mn = (int) ((t % 1000) * 60 / 1000);
            ls.add(String.format("TIME %02d:%02d", hr, mn));
        }
        if (Config.b("tactical_hud", "health")) ls.add(String.format("HP %d/%d", (int) Math.ceil(p.getHealth()), (int) p.getMaxHealth()));
        if (Config.b("tactical_hud", "biome")) {
            String id = level.getBiome(p.blockPosition()).getRegisteredName();
            int i = id.indexOf(':');
            ls.add("BIOME " + (i >= 0 ? id.substring(i + 1) : id).replace('_', ' ').toUpperCase());
        }
        panel("tactical_hud", header != null ? header : "TACTICAL", ls, null);
    }

    private static void gps() {
        List<String> ls = new ArrayList<>();
        ls.add(String.format("X%d Y%d Z%d", p.getBlockX(), p.getBlockY(), p.getBlockZ()));
        float b = Proj.bearing();
        ls.add("HDG " + bearing3(b) + "^ " + Proj.cardinal(b));
        if (Config.b("gps_interface", "dimension")) ls.add("DIM " + Dim.pretty(Dim.current(level)));
        if (Config.b("gps_interface", "waypoints")) {
            List<Waypoint> wps = new ArrayList<>();
            for (Waypoint w : Config.waypoints()) if (sameDim(w)) wps.add(w);
            wps.sort((a, c) -> Double.compare(d2(a), d2(c)));
            int n = Math.min(wps.size(), Config.i("gps_interface", "max"));
            for (int i = 0; i < n; i++) {
                Waypoint w = wps.get(i);
                double dx = w.x - p.getX(), dz = w.z - p.getZ();
                ls.add(String.format("%s %dM %s", w.name, (int) Math.sqrt(dx * dx + dz * dz), bearing3(Proj.bearingTo(dx, dz)) + "^"));
            }
            if (wps.isEmpty()) ls.add("NO WAYPOINTS");
        }
        boolean blink = ((long) (time * 2)) % 2 == 0;
        panel("gps_interface", (blink ? "* " : "  ") + "GPS LOCK", ls, null);
    }

    private static double d2(Waypoint w) { return sq(w.x - p.getX()) + sq(w.z - p.getZ()); }

    private static void altitude() {
        int fmt = Config.c("altitude_display", "format");
        double x = p.getX(), y = p.getY(), z = p.getZ();
        List<String> ls = new ArrayList<>();
        switch (fmt) {
            case 1: ls.add("X " + f1(x)); ls.add("Y " + f1(y)); ls.add("Z " + f1(z)); break;
            case 2: ls.add(String.format("X %.2f", x)); ls.add(String.format("Y %.2f", y)); ls.add(String.format("Z %.2f", z)); break;
            case 3:
                ls.add(String.format("X %d (C%d:%d)", p.getBlockX(), p.getBlockX() >> 4, p.getBlockX() & 15));
                ls.add(String.format("Y %d", p.getBlockY()));
                ls.add(String.format("Z %d (C%d:%d)", p.getBlockZ(), p.getBlockZ() >> 4, p.getBlockZ() & 15));
                break;
            default: ls.add("X " + p.getBlockX()); ls.add("Y " + p.getBlockY()); ls.add("Z " + p.getBlockZ());
        }
        if (Config.b("altitude_display", "sea")) ls.add(String.format("ALT %+d SL", (int) Math.floor(y) - 63));
        if (Config.b("altitude_display", "ground")) ls.add("AGL " + Math.max(0, (int) Math.floor(y) - groundY(x, z)));
        panel("altitude_display", "ALTITUDE", ls, null);
    }

    private static void mapOverlay() {
        int n = Controllers.mapN;
        if (n == 0 || Controllers.map.length != n * n) return;
        int cp = Config.i("map_overlay", "cell_px"), size = n * cp;
        int w = size + 6, h = size + 6 + (Config.b("map_overlay", "coords") ? line() : 0);
        float[] pl = place("map_overlay", w, h, 1f);
        float op = Config.f("map_overlay", "opacity") * gop;
        int col = Config.col("map_overlay", "color");
        Gfx.begin(g, pl[0], pl[1], pl[2]);
        Gfx.rect(g, 0, 0, w, h, Colors.mulA(0xFF0B0E09, 0.85f * op));
        Gfx.frame(g, 0, 0, w, h, Colors.mulA(col, op));
        int[] m = Controllers.map;
        for (int zi = 0; zi < n; zi++) {
            int xi = 0;
            while (xi < n) {
                int c = m[zi * n + xi], start = xi;
                while (xi < n && m[zi * n + xi] == c) xi++;
                if (c != 0) Gfx.rect(g, 3 + start * cp, 3 + zi * cp, (xi - start) * cp, cp, Colors.mulA(c, op));
            }
        }
        if (Config.b("map_overlay", "grid")) {
            int step = Math.max(1, n / 4);
            for (int i = step; i < n; i += step) {
                Gfx.vline(g, 3 + i * cp, 3, size, Colors.alpha(0xFF000000, 60));
                Gfx.hline(g, 3, 3 + i * cp, size, Colors.alpha(0xFF000000, 60));
            }
        }
        double ppb = (double) cp / Controllers.mapBpc;
        if (Config.b("map_overlay", "waypoints")) {
            for (Waypoint wp : Config.waypoints()) {
                if (!sameDim(wp)) continue;
                int sx = 3 + (int) (size / 2 + (wp.x - Controllers.mapCx) * ppb), sy = 3 + (int) (size / 2 + (wp.z - Controllers.mapCz) * ppb);
                if (sx < 3 || sy < 3 || sx > 3 + size || sy > 3 + size) continue;
                Gfx.rect(g, sx - 2, sy - 2, 5, 5, 0xFF000000);
                Gfx.rect(g, sx - 1, sy - 1, 3, 3, wpColor(wp, col));
            }
        }
        int cx = 3 + size / 2 + (int) ((p.getX() - Controllers.mapCx) * ppb), cy = 3 + size / 2 + (int) ((p.getZ() - Controllers.mapCz) * ppb);
        double yaw = Math.toRadians(p.getYRot());
        int fx = cx + (int) (-Math.sin(yaw) * 7), fy = cy + (int) (Math.cos(yaw) * 7);
        Gfx.line(g, cx, cy, fx, fy, 0xFFFFFFFF);
        Gfx.rect(g, cx - 1, cy - 1, 3, 3, 0xFFFF4A3A);
        Gfx.text(g, "N", w / 2 - 2, 4, Colors.mulA(0xFFFFFFFF, 0.9f), 0.8f);
        if (Config.b("map_overlay", "coords"))
            Gfx.text(g, String.format("%d %d", p.getBlockX(), p.getBlockZ()), 4, size + 5, Colors.mulA(col, op), 0.8f);
        Gfx.end(g);
    }

    private static void radar() {
        int size = Config.i("aircraft_radar", "size");
        int range = Config.i("aircraft_radar", "range");
        int col = Config.col("aircraft_radar", "color");
        float op = Config.f("aircraft_radar", "opacity") * gop;
        boolean visible = Config.c("aircraft_radar", "mode") == 1;
        int h = size + line() + 2;
        float[] pl = place("aircraft_radar", size, h, 1f);
        Gfx.begin(g, pl[0], pl[1], pl[2]);
        int r = size / 2, cx = r, cy = r;
        Gfx.disc(g, cx, cy, r, Colors.mulA(0xFF081008, 0.82f * op));
        Gfx.ring(g, cx, cy, r, Colors.mulA(col, op));
        Gfx.ring(g, cx, cy, r * 2 / 3, Colors.mulA(col, op * 0.35f));
        Gfx.ring(g, cx, cy, r / 3, Colors.mulA(col, op * 0.35f));
        Gfx.hline(g, 0, cy, size, Colors.mulA(col, op * 0.3f));
        Gfx.vline(g, cx, 0, size, Colors.mulA(col, op * 0.3f));
        if (Config.b("aircraft_radar", "sweep")) {
            double a = (time * 1.6) % (Math.PI * 2);
            for (int i = 0; i < 5; i++) {
                double aa = a - i * 0.09;
                Gfx.line(g, cx, cy, cx + (int) (Math.cos(aa) * (r - 1)), cy + (int) (Math.sin(aa) * (r - 1)), Colors.mulA(col, op * (0.8f - i * 0.15f)));
            }
        }
        if (visible) {
            double yaw = Math.toRadians(p.getYRot());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            List<Tracker.Obj> all = new ArrayList<>(Tracker.VISIBLE);
            for (Tracker.Obj o : Tracker.AIR) if (o.los) all.add(o);
            for (Tracker.Obj o : all) {
                double dx = o.x - p.getX(), dz = o.z - p.getZ();
                double sx = (dx * rx + dz * rz) / range * (r - 3), sy = -(dx * fx + dz * fz) / range * (r - 3);
                if (sx * sx + sy * sy > (r - 3) * (r - 3)) continue;
                int c = o.kind == Tracker.AIRCRAFT ? 0xFF40E0FF : o.hostile ? 0xFFFF4A3A : o.player ? 0xFF4A90FF : 0xFFFFB000;
                Gfx.rect(g, cx + (int) sx - 1, cy + (int) sy - 1, 3, 3, Colors.mulA(c, Math.max(0.6f, op)));
            }
        } else { // decorative: seeded drifting points, clearly labelled SIM
            Random rnd = new Random((long) (time / 7));
            for (int i = 0; i < 5; i++) {
                double a = rnd.nextDouble() * 6.283 + time * 0.05, d = 0.2 + rnd.nextDouble() * 0.7;
                Gfx.rect(g, cx + (int) (Math.cos(a) * d * (r - 3)) - 1, cy + (int) (Math.sin(a) * d * (r - 3)) - 1, 3, 3, Colors.mulA(col, op * 0.7f));
            }
        }
        if (Config.b("aircraft_radar", "heading")) {
            Gfx.line(g, cx, cy - 4, cx - 3, cy + 2, 0xFFFFFFFF); Gfx.line(g, cx, cy - 4, cx + 3, cy + 2, 0xFFFFFFFF);
        }
        Gfx.text(g, "N", cx - 2, 1, Colors.mulA(col, op), 0.7f);
        String cap = (visible ? "VIS " : "SIM ") + range + "M";
        Gfx.text(g, cap, cx - tw(cap) / 2, size + 2, Colors.mulA(col, op), 0.8f);
        Gfx.end(g);
    }

    private static void spotting() {
        int max = Config.i("aircraft_spotting", "max_entries");
        double range = Config.n("aircraft_spotting", "range");
        List<String> ls = new ArrayList<>();
        for (Tracker.Obj o : Tracker.AIR) {
            if (ls.size() >= max) break;
            if (!o.los || o.dist > range) continue;
            ls.add(String.format("%s %dM ALT%d %dM/S", shortType(o), (int) o.dist, (int) o.y, (int) o.speed));
        }
        if (ls.isEmpty()) ls.add("NO VISUAL CONTACT");
        panel("aircraft_spotting", "AIRCRAFT SPOTTING", ls, null);
    }

    private static String shortType(Tracker.Obj o) {
        String t = o.type.toUpperCase();
        return t.length() > 12 ? t.substring(0, 12) : t;
    }

    private static Tracker.Obj targetVehicle() {
        List<Tracker.Obj> all = new ArrayList<>(Tracker.VEH);
        all.addAll(Tracker.AIR);
        double range = Config.n("vehicle_inspection", "range");
        all.removeIf(o -> o.dist > range);
        return Tracker.nearestToCrosshair(all, H * 0.18, true);
    }

    private static void vehicleInspection() {
        if (!fp) return;
        Tracker.Obj t = targetVehicle();
        if (t == null) return;
        List<String> ls = new ArrayList<>();
        ls.add("TYPE " + t.type.toUpperCase());
        ls.add(String.format("DIST %dM  SPD %dM/S", (int) t.dist, (int) t.speed));
        ls.add(String.format("HDG %s^  POS %d %d %d", bearing3((float) t.heading), (int) t.x, (int) t.y, (int) t.z));
        if (t.e instanceof LivingEntity le) ls.add(String.format("HEALTH %d/%d", (int) le.getHealth(), (int) le.getMaxHealth()));
        ls.add("CREW " + t.e.getPassengers().size());
        boolean anim = Config.on("animated_vehicle_displays");
        panel("vehicle_inspection", "VEHICLE INSPECTION", ls, anim ? () -> animatedOverlay(panelW, panelH) : null);
        if (Proj.project(t.x, t.y + t.e.getBbHeight() * 0.5, t.z, P)) {
            int x = (int) P[0], y = (int) P[1], s = 12;
            int c = Colors.mulA(Config.col("vehicle_inspection", "color"), gop);
            for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
                Gfx.rect(g, x + sx * s - (sx > 0 ? 0 : -1) - (sx > 0 ? 4 : 0), y + sy * s, 5, 1, c);
                Gfx.rect(g, x + sx * s, y + sy * s - (sy > 0 ? 4 : 0), 1, 5, c);
            }
        }
    }

    /** Animation drawn inside a panel's local coordinates (used by 36 and 39). */
    private static void animatedOverlay(int w, int h) {
        int style = Config.c("animated_vehicle_displays", "style");
        double sp = Config.n("animated_vehicle_displays", "speed");
        int col = Config.col("vehicle_inspection", "color");
        double t = time * sp;
        if (style == 0) {
            int y = (int) ((t * 24) % h);
            Gfx.rect(g, 2, y, w - 2, 1, Colors.alpha(col, 160));
            Gfx.gradientV(g, 2, Math.max(0, y - 8), w - 2, Math.min(8, y), Colors.alpha(col, 0), Colors.alpha(col, 50));
        } else if (style == 1) {
            int a = (int) (30 + 25 * Math.sin(t * 3));
            for (int x = 8; x < w; x += 12) Gfx.vline(g, x, 1, h - 2, Colors.alpha(col, a));
            for (int y = 8; y < h; y += 12) Gfx.hline(g, 2, y, w - 2, Colors.alpha(col, a));
        } else {
            int r = (int) ((t * 18) % Math.max(10, h));
            Gfx.ring(g, w - 14, h / 2, Math.max(2, Math.min(r, 20)), Colors.alpha(col, Math.max(0, 200 - r * 10)));
        }
    }

    private static void aircraftCard() {
        if (!fp) return;
        Tracker.Obj t = Tracker.nearestToCrosshair(Tracker.AIR, H * 0.3, true);
        if (t == null && !Tracker.AIR.isEmpty() && Tracker.AIR.get(0).los) t = Tracker.AIR.get(0);
        if (t == null) return;
        boolean compact = Config.c("aircraft_cards", "layout") == 1;
        boolean sil = Config.b("aircraft_cards", "silhouette");
        int col = Config.col("aircraft_cards", "color");
        int w = compact ? 120 : 150, h = compact ? 44 : 78;
        float[] pl = place("aircraft_cards", w, h, Config.f("aircraft_cards", "scale"));
        Gfx.begin(g, pl[0], pl[1], pl[2]);
        Gfx.plate(g, w, h, col, gop);
        Gfx.text(g, "ID CARD", 8, 4, col, 0.9f);
        Gfx.text(g, shortType(t), 8, 4 + line(), 0xFFEDEFE6, 1f);
        if (sil) silhouette(t.path, w - 44, 6, col);
        if (!compact) {
            Gfx.text(g, "RANGE " + (int) t.dist + "M", 8, 4 + line() * 2, 0xFFC0C4B8, 0.9f);
            Gfx.text(g, "ALT " + (int) t.y + "  SPD " + (int) t.speed + "M/S", 8, 4 + line() * 3, 0xFFC0C4B8, 0.9f);
            Gfx.text(g, "HDG " + bearing3((float) t.heading) + "^  CLB " + (int) t.climb, 8, 4 + line() * 4, 0xFFC0C4B8, 0.9f);
        } else {
            Gfx.text(g, (int) t.dist + "M  " + (int) t.speed + "M/S", 8, 4 + line() * 2, 0xFFC0C4B8, 0.9f);
        }
        Gfx.end(g);
    }

    /** Small stylised aircraft shape chosen from the entity id. Local coordinates. */
    private static void silhouette(String path, int x, int y, int c) {
        if (path.contains("phantom") || path.contains("dragon") || path.contains("bat")) { // winged creature
            Gfx.rect(g, x + 14, y + 8, 12, 5, c);
            for (int i = 0; i < 12; i++) { Gfx.rect(g, x + 14 - i, y + 8 - i / 3, 1, 4, c); Gfx.rect(g, x + 26 + i, y + 8 - i / 3, 1, 4, c); }
        } else if (path.contains("heli")) {
            Gfx.rect(g, x + 8, y + 12, 18, 8, c); Gfx.rect(g, x + 26, y + 14, 14, 3, c);
            Gfx.rect(g, x + 2, y + 9, 32, 1, c); Gfx.rect(g, x + 16, y + 10, 2, 2, c);
        } else if (path.contains("ghast")) {
            Gfx.rect(g, x + 10, y + 6, 20, 16, c);
            for (int i = 0; i < 4; i++) Gfx.rect(g, x + 12 + i * 5, y + 22, 2, 8, c);
        } else { // generic jet / plane
            Gfx.rect(g, x + 4, y + 13, 34, 4, c);
            for (int i = 0; i < 14; i++) { Gfx.rect(g, x + 14 + i / 2, y + 13 - i, 3, 1, c); Gfx.rect(g, x + 14 + i / 2, y + 17 + i, 3, 1, c); }
            Gfx.rect(g, x + 2, y + 9, 3, 12, c);
        }
    }

    private static void vehicleDashboard() {
        if (!Config.b("animated_vehicle_displays", "dashboard")) return;
        Entity v = p.getVehicle();
        if (v == null) return;
        double speed = v.getDeltaMovement().length() * 20;
        List<String> ls = new ArrayList<>();
        ls.add("VEH " + v.getType().getDescription().getString().toUpperCase());
        ls.add(String.format("SPD %.1f M/S  %.0f KPH", speed, speed * 3.6));
        float b = Proj.bearing();
        ls.add("HDG " + bearing3(b) + "^ " + Proj.cardinal(b));
        ls.add("CREW " + v.getPassengers().size());
        int w0 = 0;
        for (String l : ls) w0 = Math.max(w0, tw(l));
        int w = w0 + 14, h = line() * (ls.size() + 1) + 8 + 6;
        float[] pl = place("animated_vehicle_displays", w, h, Config.f("animated_vehicle_displays", "scale"));
        Gfx.begin(g, pl[0], pl[1], pl[2]);
        Gfx.plate(g, w, h, accent, gop);
        Gfx.text(g, "DASHBOARD", 8, 4, accent, 1f);
        int y = 4 + line();
        for (String l : ls) { Gfx.text(g, l, 8, y, 0xFFEDEFE6, 1f); y += line(); }
        int bar = (int) Math.min(w - 16, speed / 20.0 * (w - 16));
        Gfx.rect(g, 8, h - 8, w - 16, 3, 0x40FFFFFF);
        Gfx.rect(g, 8, h - 8, bar, 3, accent);
        animatedOverlay(w, h);
        Gfx.end(g);
    }

    private static void radioGui() {
        int ch = Config.c("military_radio_gui", "channel");
        float op = Config.f("military_radio_gui", "opacity") * gop;
        int col = Config.col("military_radio_gui", "color");
        int w = 118, h = 54 + (Config.b("military_radio_gui", "waveform") ? 14 : 0);
        float[] pl = place("military_radio_gui", w, h, Config.f("military_radio_gui", "scale"));
        Gfx.begin(g, pl[0], pl[1], pl[2]);
        Gfx.plate(g, w, h, col, op);
        Gfx.text(g, "RADIO", 8, 4, Colors.mulA(col, op), 1f);
        Gfx.text(g, "CH " + (ch + 1), 8, 4 + line(), 0xFFEDEFE6, 1f);
        Gfx.text(g, String.format("%.2f MHZ", 30.0 + ch * 1.25), 8, 4 + line() * 2, 0xFFC0C4B8, 0.9f);
        boolean active = Config.on("radio_chatter");
        for (int i = 0; i < 5; i++) {
            int bh = 3 + i * 2;
            boolean lit = active ? i <= (int) (2.5 + 2.4 * Math.sin(time * 1.3 + ch)) : i == 0;
            Gfx.rect(g, w - 34 + i * 5, 24 - bh, 3, bh, lit ? Colors.mulA(col, op) : 0x40FFFFFF);
        }
        Gfx.text(g, active ? "RX" : "STBY", w - 30, 28, active ? 0xFF3CFF7A : 0xFF9AA090, 0.8f);
        if (Config.b("military_radio_gui", "waveform")) {
            for (int i = 0; i < 24; i++) {
                int bh = active ? 1 + (int) (5 * Math.abs(Math.sin(time * 6 + i * 0.9) * Math.sin(time * 2.1 + i * 0.3))) : 1;
                Gfx.rect(g, 8 + i * 4, h - 7 - bh, 3, bh * 2, Colors.mulA(col, op * 0.8f));
            }
        }
        Gfx.end(g);
    }

    // =================================================================== centre elements

    private static void compass() {
        int style = Config.c("digital_compass", "style");
        int width = Config.i("digital_compass", "width");
        float sc = Config.f("digital_compass", "scale") * hs;
        float op = Config.f("digital_compass", "opacity") * gop;
        int col = Config.col("digital_compass", "color");
        float b = Proj.bearing();
        int h = th() + 14;
        int y = (int) (H * Config.f("digital_compass", "y") / 100f);
        Gfx.begin(g, W / 2f - width * sc / 2f, y, sc);
        Gfx.rect(g, 0, 0, width, h, Colors.mulA(0xFF0B0E09, 0.7f * op));
        Gfx.frame(g, 0, 0, width, h, Colors.mulA(col, op * 0.8f));
        if (style == 1) {
            String s = bearing3(b) + "^ " + Proj.cardinal(b);
            Gfx.textCentered(g, s, width / 2, h / 2 - th() / 2, Colors.mulA(col, Math.max(0.6f, op)), 1.4f);
        } else {
            double ppd = width / 120.0;
            boolean ticks = Config.b("digital_compass", "ticks"), deg = Config.b("digital_compass", "degrees");
            for (int d = (int) (Math.floor((b - 60) / 5) * 5); d <= b + 60; d += 5) {
                int x = (int) (width / 2 + (d - b) * ppd);
                if (x < 2 || x > width - 2) continue;
                int dd = Math.floorMod(d, 360);
                if (dd % 45 == 0) {
                    String s = Proj.cardinal(dd);
                    Gfx.textCentered(g, s, x, 3, Colors.mulA(dd % 90 == 0 ? 0xFFFFFFFF : col, op), 1f);
                } else if (dd % 15 == 0) {
                    if (deg) Gfx.textCentered(g, Integer.toString(dd), x, 3, Colors.mulA(col, op * 0.8f), 0.8f);
                    else if (ticks) Gfx.vline(g, x, h - 8, 6, Colors.mulA(col, op));
                } else if (ticks) Gfx.vline(g, x, h - 5, 3, Colors.mulA(col, op * 0.6f));
            }
            Gfx.rect(g, width / 2 - 1, h - 4, 3, 4, 0xFFFF4A3A);
            Gfx.rect(g, width / 2, 0, 1, 2, 0xFFFF4A3A);
        }
        Gfx.end(g);
    }

    private static void targeting() {
        if (!fp) return;
        int style = Config.c("targeting_display", "style");
        int size = Config.i("targeting_display", "size");
        int col = Colors.mulA(Config.col("targeting_display", "color"), Config.f("targeting_display", "opacity") * gop);
        if (Config.b("targeting_display", "pulse")) size += (int) Math.round(Math.sin(time * 4) * 1.5);
        int cx = W / 2, cy = H / 2, r = size / 2;
        switch (style) {
            case 0:
                Gfx.hline(g, cx - r, cy, r - 3, col); Gfx.hline(g, cx + 4, cy, r - 3, col);
                Gfx.vline(g, cx, cy - r, r - 3, col); Gfx.vline(g, cx, cy + 4, r - 3, col);
                break;
            case 1: Gfx.ring(g, cx, cy, r, col); Gfx.rect(g, cx - 1, cy - 1, 3, 3, col); break;
            case 2: brackets(cx, cy, r, col); break;
            case 3:
                Gfx.ring(g, cx, cy, r, col); Gfx.rect(g, cx - 1, cy - 1, 3, 3, col);
                Gfx.vline(g, cx, cy - r - 4, 4, col); Gfx.vline(g, cx, cy + r + 1, 4, col);
                Gfx.hline(g, cx - r - 4, cy, 4, col); Gfx.hline(g, cx + r + 1, cy, 4, col);
                break;
            default:
                Gfx.ring(g, cx, cy, r, col); Gfx.ring(g, cx, cy, Math.max(2, r / 4), col);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    Gfx.line(g, cx + (int) (Math.cos(a) * (r + 1)), cy + (int) (Math.sin(a) * (r + 1)),
                        cx + (int) (Math.cos(a) * (r + 4)), cy + (int) (Math.sin(a) * (r + 4)), col);
                }
        }
        if (Config.b("targeting_display", "side_brackets")) {
            int off = r + 36;
            Gfx.vline(g, cx - off, cy - 14, 28, col); Gfx.hline(g, cx - off, cy - 14, 5, col); Gfx.hline(g, cx - off, cy + 13, 5, col);
            Gfx.vline(g, cx + off, cy - 14, 28, col); Gfx.hline(g, cx + off - 4, cy - 14, 5, col); Gfx.hline(g, cx + off - 4, cy + 13, 5, col);
        }
        if (Config.b("targeting_display", "ticks") && (style == 4 || style == 3)) {
            for (int deg = -30; deg <= 30; deg += 10) {
                float y = Proj.elevY(deg);
                if (Math.abs(y - cy) > H * 0.35f || y < 0 || y > H) continue;
                int len = deg == 0 ? 60 : 24;
                if (deg == 0) { Gfx.hline(g, cx - 90, (int) y, len, Colors.mulA(col, 0.9f)); Gfx.hline(g, cx + 30, (int) y, len, Colors.mulA(col, 0.9f)); }
                else {
                    Gfx.hline(g, cx - 60, (int) y, len, Colors.mulA(col, 0.7f)); Gfx.hline(g, cx + 36, (int) y, len, Colors.mulA(col, 0.7f));
                    Gfx.text(g, Integer.toString(Math.abs(deg)), cx + 64, (int) y - 3, Colors.mulA(col, 0.8f), 0.7f);
                }
            }
        }
    }

    private static void brackets(int cx, int cy, int r, int col) {
        int l = Math.max(4, r / 2);
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
            int x = cx + sx * r, y = cy + sy * r;
            Gfx.rect(g, sx > 0 ? x - l : x, y, l, 1, col);
            Gfx.rect(g, x, sy > 0 ? y - l : y, 1, l, col);
        }
    }

    private static void aircraftCamera() {
        if (!Controllers.camera) return;
        int col = Config.col("aircraft_camera", "color");
        int frame = Config.c("aircraft_camera", "frame");
        int m = (int) (Math.min(W, H) * 0.06);
        if (frame == 0) {
            brackets2(m, col);
        } else if (frame == 1) {
            brackets2(m, col);
            Gfx.hline(g, W / 2 - 30, H / 2, 24, col); Gfx.hline(g, W / 2 + 6, H / 2, 24, col);
            Gfx.vline(g, W / 2, H / 2 - 30, 24, col); Gfx.vline(g, W / 2, H / 2 + 6, 24, col);
            for (int i = 1; i < 3; i++) {
                Gfx.vline(g, W * i / 3, m, H - 2 * m, Colors.alpha(col, 40));
                Gfx.hline(g, m, H * i / 3, W - 2 * m, Colors.alpha(col, 40));
            }
        } else {
            scanlines(40, 3);
            vignette(0.5f);
            brackets2(m, col);
        }
        if (Config.b("aircraft_camera", "rec") && ((long) (time * 2)) % 2 == 0) {
            Gfx.disc(g, m + 8, m + 10, 4, 0xFFFF2A2A);
        }
        Gfx.text(g, "REC", m + 16, m + 6, col, 1f);
        long secs = (long) (time) % 86400;
        Gfx.text(g, String.format("%02d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60), W - m - 60, m + 6, col, 1f);
        Gfx.text(g, String.format("ZOOM %.1fX", Config.n("aircraft_camera", "zoom")), m + 6, H - m - 12, col, 1f);
        if (Config.b("aircraft_camera", "track_box") && fp) {
            Tracker.Obj t = Tracker.nearestToCrosshair(Tracker.AIR, H * 0.4, true);
            if (t != null && Proj.project(t.x, t.y + t.e.getBbHeight() * 0.5, t.z, P)) {
                int s = Math.max(10, (int) (t.e.getBbWidth() * Proj.pixelsPerBlock(P[2])));
                int x = (int) P[0] - s, y = (int) P[1] - s;
                Gfx.frame(g, x, y, s * 2, s * 2, 0xFFFFB000);
                Gfx.text(g, "TRK " + shortType(t) + " " + (int) t.dist + "M", x, y - th() - 2, 0xFFFFB000, 0.9f);
            }
        }
    }

    private static void brackets2(int m, int col) {
        int l = 18;
        Gfx.rect(g, m, m, l, 2, col); Gfx.rect(g, m, m, 2, l, col);
        Gfx.rect(g, W - m - l, m, l, 2, col); Gfx.rect(g, W - m - 2, m, 2, l, col);
        Gfx.rect(g, m, H - m - 2, l, 2, col); Gfx.rect(g, m, H - m - l, 2, l, col);
        Gfx.rect(g, W - m - l, H - m - 2, l, 2, col); Gfx.rect(g, W - m - 2, H - m - l, 2, l, col);
    }

    private static void binoculars() {
        if (!Controllers.binocular) return;
        int style = Config.c("binocular_overlay", "style");
        int col = Config.col("binocular_overlay", "color");
        float vig = (float) Config.n("binocular_overlay", "vignette");
        int black = Colors.alpha(0xFF000000, (int) (255 * vig));
        if (style == 0) {
            int r = (int) (H * 0.42), off = (int) (r * 0.92);
            int c1 = W / 2 - off, c2 = W / 2 + off;
            for (int y = 0; y < H; y += 1) {
                double dy = y - H / 2.0;
                double hh = r * r - dy * dy;
                if (hh <= 0) { Gfx.rect(g, 0, y, W, 1, black); continue; }
                int half = (int) Math.sqrt(hh);
                int a1 = c1 - half, b1 = c1 + half, a2 = c2 - half, b2 = c2 + half;
                // union of two intervals; fill the gaps
                int left = Math.min(a1, a2), right = Math.max(b1, b2);
                Gfx.rect(g, 0, y, Math.max(0, left), 1, black);
                Gfx.rect(g, Math.max(0, right), y, Math.max(0, W - right), 1, black);
                if (b1 < a2) Gfx.rect(g, b1, y, a2 - b1, 1, black);
            }
            Gfx.ring(g, c1, H / 2, r, Colors.alpha(col, 160)); Gfx.ring(g, c2, H / 2, r, Colors.alpha(col, 160));
            Gfx.hline(g, c2 - 24, H / 2, 48, Colors.alpha(col, 200)); Gfx.vline(g, c2, H / 2 - 24, 48, Colors.alpha(col, 200));
        } else if (style == 1) {
            int r = (int) (H * 0.46);
            for (int y = 0; y < H; y++) {
                double dy = y - H / 2.0, hh = r * r - dy * dy;
                if (hh <= 0) { Gfx.rect(g, 0, y, W, 1, black); continue; }
                int half = (int) Math.sqrt(hh);
                Gfx.rect(g, 0, y, W / 2 - half, 1, black);
                Gfx.rect(g, W / 2 + half, y, W / 2 - half, 1, black);
            }
            Gfx.ring(g, W / 2, H / 2, r, Colors.alpha(col, 170));
            Gfx.hline(g, W / 2 - r, H / 2, r * 2, Colors.alpha(col, 140)); Gfx.vline(g, W / 2, H / 2 - r, r * 2, Colors.alpha(col, 140));
            for (int i = -4; i <= 4; i++) { Gfx.rect(g, W / 2 + i * 14, H / 2 - 2, 1, 5, col); Gfx.rect(g, W / 2 - 2, H / 2 + i * 14, 5, 1, col); }
        } else {
            int bar = (int) (H * 0.16);
            Gfx.rect(g, 0, 0, W, bar, black); Gfx.rect(g, 0, H - bar, W, bar, black);
            int bx = (int) (W * 0.2);
            Gfx.rect(g, 0, bar, bx, H - 2 * bar, Colors.mulA(black, 0.7f)); Gfx.rect(g, W - bx, bar, bx, H - 2 * bar, Colors.mulA(black, 0.7f));
            brackets(W / 2, H / 2, 40, col);
            Gfx.hline(g, W / 2 - 8, H / 2, 16, col); Gfx.vline(g, W / 2, H / 2 - 8, 16, col);
        }
        String z = String.format("%.1fX", Config.n("binocular_overlay", "zoom"));
        Gfx.text(g, z, W / 2 - tw(z) / 2, H - (int) (H * 0.1), col, 1.2f);
        if (Config.b("binocular_overlay", "range")) {
            String r = Controllers.rangeM > 0 ? "RNG " + (int) Controllers.rangeM + " M" : "RNG ---";
            Gfx.text(g, r, W / 2 - tw(r) / 2, (int) (H * 0.1), col, 1.2f);
        }
    }

    private static void warningLights() {
        boolean low = p.getHealth() <= p.getMaxHealth() * 0.3f;
        boolean hostile = false;
        for (Tracker.Obj o : Tracker.VISIBLE) if (o.hostile && o.dist < 24) { hostile = true; break; }
        boolean storm = level.isThundering();
        boolean active;
        switch (Config.c("warning_lights", "trigger")) {
            case 1: active = low; break;
            case 2: active = hostile; break;
            case 3: active = storm; break;
            case 4: active = low || hostile; break;
            default: active = true;
        }
        if (!active) return;
        double sp = Config.n("warning_lights", "speed");
        double ph = (time * sp) % 1.0;
        if (ph > 0.5) return;
        float k = (float) (1 - ph * 2);
        int[] cols = { 0xFFFFB000, 0xFFFF2A2A, 0xFF3A7AFF, 0xFFFFFFFF };
        int c = cols[Config.c("warning_lights", "color")];
        int s = (int) (26 * Config.n("warning_lights", "size"));
        for (int sx = 0; sx < 2; sx++) {
            int x = sx == 0 ? 6 : W - 6 - s;
            Gfx.rect(g, x, 6, s, s / 2, Colors.alpha(c, (int) (200 * k * gop)));
            Gfx.gradientV(g, x - 6, 6 + s / 2, s + 12, s, Colors.alpha(c, (int) (110 * k * gop)), Colors.alpha(c, 0));
        }
    }

    private static void signage() {
        double dur = Config.n("military_signage", "duration");
        double rad = Config.n("military_signage", "zone_radius");
        Waypoint inside = null;
        double best = rad * rad;
        for (Waypoint w : Config.waypoints()) {
            if (!sameDim(w)) continue;
            double d = sq(w.x - p.getX()) + sq(w.z - p.getZ());
            if (d <= best) { best = d; inside = w; }
        }
        String now = inside == null ? null : inside.name;
        if (now != null && !now.equals(zoneName)) {
            bannerType = inside.type;
            switch (inside.type) {
                case Waypoint.RESTRICTED: banner1 = "RESTRICTED AREA"; banner2 = inside.name + " // AUTHORIZED PERSONNEL ONLY"; break;
                case Waypoint.HAZARD: banner1 = "WARNING // HAZARD ZONE"; banner2 = inside.name; break;
                case Waypoint.LZ: banner1 = "LANDING ZONE"; banner2 = inside.name; break;
                case Waypoint.RWY_A: case Waypoint.RWY_B: banner1 = "ACTIVE RUNWAY"; banner2 = inside.name; break;
                case Waypoint.BASE: banner1 = "NOW ENTERING"; banner2 = inside.name; break;
                default: banner1 = "AREA"; banner2 = inside.name;
            }
            bannerFrom = time; bannerUntil = time + dur;
            zoneInside = true;
        } else if (now == null && zoneName != null && zoneInside) {
            banner1 = "NOW LEAVING"; banner2 = zoneName; bannerType = Waypoint.POINT;
            bannerFrom = time; bannerUntil = time + dur;
            zoneInside = false;
        }
        zoneName = now;
        if (time > bannerUntil) return;
        float fade = (float) Math.min(1.0, Math.min((time - bannerFrom) * 3, (bannerUntil - time) * 1.2));
        int style = Config.c("military_signage", "style");
        int w = Math.max(tw(banner1) * 2, tw(banner2)) + 56, h = th() * 2 + 26;
        int x = (W - w) / 2, y = (int) (H * Config.f("military_signage", "y") / 100f);
        boolean warn = bannerType == Waypoint.RESTRICTED || bannerType == Waypoint.HAZARD || style == 1;
        int c = warn ? 0xFFFFB000 : accent;
        Gfx.rect(g, x, y, w, h, Colors.mulA(0xFF0B0E09, 0.85f * fade));
        Gfx.frame(g, x, y, w, h, Colors.mulA(c, fade));
        if (Config.b("military_signage", "hazard") && style != 2) {
            for (int i = 0; i < w; i += 16) {
                int a = (i / 16) % 2 == 0 ? c : 0xFF111111;
                Gfx.rect(g, x + i, y, Math.min(16, w - i), 4, Colors.mulA(a, fade));
                Gfx.rect(g, x + i, y + h - 4, Math.min(16, w - i), 4, Colors.mulA(a, fade));
            }
        }
        Gfx.text(g, banner1, x + (w - tw(banner1) * 2) / 2, y + 9, Colors.mulA(c, fade), 2f);
        Gfx.text(g, banner2, x + (w - tw(banner2)) / 2, y + 12 + th() * 2, Colors.mulA(0xFFEDEFE6, fade), 1f);
    }
}
