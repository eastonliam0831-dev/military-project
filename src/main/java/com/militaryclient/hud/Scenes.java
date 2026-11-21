package com.militaryclient.hud;

import com.militaryclient.config.Config;
import com.militaryclient.util.Colors;
import com.militaryclient.util.Compat;
import com.militaryclient.util.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Set;

/** Overlays drawn after vanilla screens: military inventory styling and themed loading scenes. */
public final class Scenes {
    private static final Set<String> LOADING = Set.of("LevelLoadingScreen", "ReceivingLevelScreen", "ConnectScreen",
        "ProgressScreen", "DownloadingTerrainScreen");

    private static final String[] TIPS = {
        "BINOCULARS: hold the keybind to zoom. Zoom is limited by Minecraft's 30 degree minimum FOV.",
        "WAYPOINTS: add one in the control panel, then enable Tactical Waypoint Markers.",
        "RUNWAYS: mark two waypoints as Runway A and Runway B to light a runway.",
        "PRESETS: Fighter Pilot, Army, Tactical Green and more change look and audio only.",
        "SAFETY: every feature has its own switch. Nothing here changes gameplay or talks to the server.",
        "RADAR: Visible Contacts mode only plots what you can actually see.",
        "NIGHT VISION: brightness is restored to your original value when the filter is turned off.",
        "EXPORT: General Settings can export your whole loadout to a single file.",
        "AIRCRAFT: Phantoms, Ghasts and elytra gliders count as aircraft in vanilla.",
        "AMBIENCE: ambient sounds are rare by design. Tune intervals per feature."
    };

    private Scenes() {}

    public static boolean isLoading(Screen s) { return LOADING.contains(s.getClass().getSimpleName()); }

    // ------------------------------------------------------------------ inventory

    public static void inventory(Screen screen, GuiGraphicsExtractor g) {
        if (!Config.on("military_inventory") || !(screen instanceof AbstractContainerScreen<?>)) return;
        int sw = screen.width, sh = screen.height;
        int iw = Compat.intField(screen, "imageWidth", 176), ih = Compat.intField(screen, "imageHeight", 166);
        int x = Compat.intField(screen, "leftPos", (sw - iw) / 2), y = Compat.intField(screen, "topPos", (sh - ih) / 2);
        int col = Config.col("military_inventory", "color");
        Gfx.refreshFromConfig();
        Gfx.rect(g, x, y, iw, ih, Colors.alpha(col, (int) (Config.n("military_inventory", "tint_alpha") * 255)));
        if (Config.b("military_inventory", "scanlines"))
            for (int yy = y; yy < y + ih; yy += 3) Gfx.rect(g, x, yy, iw, 1, Colors.alpha(0xFF000000, 18));
        if (Config.b("military_inventory", "brackets")) {
            int l = 10, o = 3;
            int c = Colors.alpha(col, 230);
            Gfx.rect(g, x - o, y - o, l, 2, c); Gfx.rect(g, x - o, y - o, 2, l, c);
            Gfx.rect(g, x + iw + o - l, y - o, l, 2, c); Gfx.rect(g, x + iw + o - 2, y - o, 2, l, c);
            Gfx.rect(g, x - o, y + ih + o - 2, l, 2, c); Gfx.rect(g, x - o, y + ih + o - l, 2, l, c);
            Gfx.rect(g, x + iw + o - l, y + ih + o - 2, l, 2, c); Gfx.rect(g, x + iw + o - 2, y + ih + o - l, 2, l, c);
        }
        if (Config.b("military_inventory", "header")) {
            int hh = 11;
            Gfx.rect(g, x, y - hh - 4, iw, hh, Colors.alpha(0xFF0B0E09, 215));
            Gfx.rect(g, x, y - hh - 4, 3, hh, col);
            String t = "OPERATOR LOADOUT // " + screen.getTitle().getString().toUpperCase();
            if (t.length() > 38) t = t.substring(0, 38);
            Gfx.vanilla(g, t, x + 7, y - hh - 2, col, 1f);
        }
    }

    // ------------------------------------------------------------------ loading scene

    public static void loading(Screen screen, GuiGraphicsExtractor g) {
        if (!Config.on("loading_screens")) return;
        Gfx.refreshFromConfig();
        int W = screen.width, H = screen.height;
        int coverH = Math.max(40, H - 56);          // leave the Cancel button area untouched
        double t = System.nanoTime() / 1e9;
        int scene = Config.c("loading_screens", "scene");
        if (scene == 0) scene = 1 + (int) ((t / Config.i("loading_screens", "rotate")) % 6);
        g.enableScissor(0, 0, W, coverH);
        draw(g, scene, W, coverH, t);
        g.disableScissor();
        // letterbox with hazard stripe
        Gfx.rect(g, 0, coverH - 6, W, 6, 0xFF0B0E09);
        for (int i = 0; i < W; i += 14) Gfx.rect(g, i, coverH - 5, 7, 3, 0xFFFFB000);
        int accent = Config.accent();
        Gfx.begin(g, W / 2f, coverH * 0.38f, 1f);
        Gfx.end(g);
        Gfx.textCentered(g, "DEPLOYING TO THEATER", W / 2, (int) (coverH * 0.30), 0xFFEDEFE6, 3f);
        String dots = ".".repeat((int) (t * 2) % 4);
        Gfx.textCentered(g, "STAND BY" + dots, W / 2, (int) (coverH * 0.30) + 34, accent, 1.5f);
        if (Config.b("loading_screens", "bar")) {
            int bw = Math.min(260, W - 60), bx = (W - bw) / 2, by = coverH - 46;
            Gfx.rect(g, bx, by, bw, 8, 0xB0000000);
            Gfx.frame(g, bx - 1, by - 1, bw + 2, 10, accent);
            int fill = (int) (bw * (((t * 0.35) % 1.0)));
            Gfx.rect(g, bx, by, fill, 8, accent);
        }
        if (Config.b("loading_screens", "tips")) {
            String tip = TIPS[(int) ((t / 5) % TIPS.length)];
            Gfx.vanilla(g, tip, (W - Minecraft.getInstance().font.width(tip)) / 2, coverH - 24, 0xFFE6E8DD, 1f);
        }
    }

    private static int hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static void sky(GuiGraphicsExtractor g, int W, int H, int top, int bottom) {
        g.fillGradient(0, 0, W, H, top, bottom);
    }

    private static void ridge(GuiGraphicsExtractor g, int W, int H, int baseY, double amp, double freq, double phase, int col) {
        for (int x = 0; x < W; x += 3) {
            int h = (int) (amp * (0.6 * Math.sin(x * freq + phase) + 0.4 * Math.sin(x * freq * 2.3 + phase * 1.7)) + amp);
            g.fill(x, baseY - h, x + 3, H, col);
        }
    }

    private static void draw(GuiGraphicsExtractor g, int scene, int W, int H, double t) {
        switch (scene) {
            case 1: { // desert
                sky(g, W, H, 0xFF3A2A4A, 0xFFF0A860);
                Gfx.disc(g, (int) (W * 0.75), (int) (H * 0.55), 26, 0xFFFFE0A0);
                ridge(g, W, H, (int) (H * 0.80), 14, 0.012, 1, 0xFFB5733A);
                ridge(g, W, H, (int) (H * 0.88), 18, 0.008, 3, 0xFF8E5A2C);
                ridge(g, W, H, H, 12, 0.02, 5, 0xFF5E3B1E);
                for (int i = 0; i < 6; i++) Gfx.rect(g, (int) ((t * 20 + i * 90) % W), (int) (H * 0.78) + i * 2, 40, 1, 0x30FFFFFF);
                break;
            }
            case 2: { // jungle
                sky(g, W, H, 0xFF1C3A2A, 0xFF7AA860);
                ridge(g, W, H, (int) (H * 0.72), 20, 0.015, 2, 0xFF1F4A2A);
                for (int x = 0; x < W; x += 22) {
                    int th = 40 + (hash(x, 1) & 63);
                    Gfx.rect(g, x, H - th - 20, 5, th + 20, 0xFF14281A);
                    Gfx.disc(g, x + 2, H - th - 20, 14 + (hash(x, 2) & 7), 0xFF1A3A22);
                }
                g.fillGradient(0, (int) (H * 0.6), W, H, 0x00DDEEDD, 0x50DDEEDD);
                break;
            }
            case 3: { // arctic
                sky(g, W, H, 0xFF1A2A4A, 0xFFB8D4EA);
                for (int i = 0; i < 3; i++) {
                    int y = (int) (H * (0.15 + 0.07 * i) + Math.sin(t + i) * 6);
                    g.fillGradient(0, y, W, y + 28, 0x0040FF90, 0x4040FF90);
                }
                ridge(g, W, H, (int) (H * 0.74), 26, 0.01, 4, 0xFFDDE8F2);
                ridge(g, W, H, H, 10, 0.02, 1, 0xFFF4F8FC);
                break;
            }
            case 4: { // night operation
                sky(g, W, H, 0xFF03060C, 0xFF14202E);
                for (int i = 0; i < 70; i++) {
                    int sx = (hash(i, 7) & 0xFFFF) % W, sy = (hash(i, 9) & 0xFFFF) % (int) (H * 0.6);
                    int a = 90 + (int) (90 * Math.sin(t * 2 + i));
                    g.fill(sx, sy, sx + 1, sy + 1, Colors.alpha(0xFFFFFFFF, Math.max(30, a)));
                }
                Gfx.disc(g, (int) (W * 0.2), (int) (H * 0.22), 14, 0xFFE8EEF4);
                ridge(g, W, H, (int) (H * 0.80), 16, 0.011, 6, 0xFF050A08);
                double a = Math.sin(t * 0.6) * 0.5;
                int bx = (int) (W * 0.65), by = (int) (H * 0.82);
                for (int i = 0; i < 70; i++) {
                    double d = i * 4.0;
                    int px = bx + (int) (Math.sin(a) * d), py = by - (int) (Math.cos(a) * d);
                    g.fill(px - i / 6, py, px + i / 6 + 2, py + 3, Colors.alpha(0xFFE8F0FF, Math.max(5, 60 - i)));
                }
                break;
            }
            case 5: { // urban dusk
                sky(g, W, H, 0xFF2A1E3A, 0xFFE07A4A);
                for (int x = 0; x < W; x += 28) {
                    int bh = 50 + (hash(x, 3) & 127);
                    Gfx.rect(g, x, H - bh, 26, bh, 0xFF0C0E14);
                    for (int wy = H - bh + 6; wy < H - 8; wy += 9)
                        for (int wx = x + 4; wx < x + 22; wx += 7)
                            if ((hash(wx, wy) & 7) < 3) Gfx.rect(g, wx, wy, 3, 4, 0xFFFFD27A);
                }
                for (int i = 0; i < 3; i++) {
                    int sx = (int) (W * (0.25 + 0.25 * i));
                    for (int k = 0; k < 40; k++) g.fill(sx + (int) (Math.sin(t + k * 0.2 + i) * k * 0.4), H - 90 - k * 3, sx + 10 + k / 3, H - 87 - k * 3, Colors.alpha(0xFF333340, 90 - k * 2));
                }
                break;
            }
            default: { // carrier deck
                sky(g, W, H, 0xFF5A86B0, 0xFFC8DCEA);
                g.fill(0, (int) (H * 0.62), W, H, 0xFF1C4A70);
                for (int i = 0; i < 12; i++) Gfx.rect(g, (int) ((t * 30 + i * 80) % W), (int) (H * 0.64) + i * 4, 30, 1, 0x40FFFFFF);
                int dy = (int) (H * 0.74);
                g.fill(0, dy, W, H, 0xFF4A5058);
                for (int x = 0; x < W; x += 36) Gfx.rect(g, x, dy + (H - dy) / 2, 20, 2, 0xFFE8E8E8);
                int jx = (int) (W * 0.62), jy = dy - 6;
                Gfx.rect(g, jx, jy, 70, 6, 0xFF2A2E34);
                for (int i = 0; i < 22; i++) { Gfx.rect(g, jx + 24 + i / 2, jy - i, 6, 1, 0xFF2A2E34); }
                Gfx.rect(g, jx + 62, jy - 14, 3, 14, 0xFF2A2E34);
                break;
            }
        }
    }
}
