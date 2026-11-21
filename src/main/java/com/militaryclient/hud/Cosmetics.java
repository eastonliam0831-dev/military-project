package com.militaryclient.hud;

import com.militaryclient.config.Config;
import com.militaryclient.config.Features;
import com.militaryclient.config.Setting;
import com.militaryclient.util.Camo;
import com.militaryclient.util.Colors;
import com.militaryclient.util.Gfx;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;

/** Cosmetic rendering: the Operator Loadout figure (panel) and first-person overlays. Visual only. */
public final class Cosmetics {
    private static final int GW = 46, GH = 94;
    private static final Map<Long, int[]> GRIDS = new HashMap<>();
    private static final int SKIN = 0xFFD8A878, BOOT = 0xFF15130F, METAL = 0xFFC9CCD0;

    private Cosmetics() {}

    private static int[] grid(int pattern) {
        double scale = Config.n("camo_patterns", "scale");
        int c1 = Config.col("camo_patterns", "custom1"), c2 = Config.col("camo_patterns", "custom2"),
            c3 = Config.col("camo_patterns", "custom3");
        long key = pattern * 31L + Double.doubleToLongBits(scale) * 17 + c1 * 13L + c2 * 7L + c3;
        int[] g = GRIDS.get(key);
        if (g == null) {
            if (GRIDS.size() > 8) GRIDS.clear();
            g = new int[GW * GH];
            int[] pal = Camo.palette(pattern, c1, c2, c3);
            for (int y = 0; y < GH; y++)
                for (int x = 0; x < GW; x++) g[y * GW + x] = Camo.color(pattern, x * 2, y * 2, scale, pal);
            GRIDS.put(key, g);
        }
        return g;
    }

    private static void pr(GuiGraphicsExtractor g, int ox, int oy, int s, int x, int y, int w, int h, int c) {
        g.fill(ox + x * s, oy + y * s, ox + (x + w) * s, oy + (y + h) * s, c);
    }

    private static void camo(GuiGraphicsExtractor g, int ox, int oy, int s, int x, int y, int w, int h, int pattern) {
        int[] grid = grid(pattern);
        for (int yy = y; yy < y + h && yy < GH; yy++) {
            int xx = x;
            while (xx < x + w && xx < GW) {
                int c = grid[yy * GW + xx];
                int start = xx;
                while (xx < x + w && xx < GW && grid[yy * GW + xx] == c) xx++;
                pr(g, ox, oy, s, start, yy, xx - start, 1, c);
            }
        }
    }

    private static int selectedPattern() { return Config.c("camo_patterns", "pattern"); }

    private static int uniformPattern(int style) {
        switch (style) {
            case 1: return 4;   // MARPAT -> digital
            case 2: return 1;   // DCU -> desert
            case 3: return 0;   // Jungle -> woodland
            default: return selectedPattern();
        }
    }

    // ------------------------------------------------------------------ operator figure

    public static int figureWidth(int s) { return GW * s; }
    public static int figureHeight(int s) { return GH * s; }

    /** Draws the operator at pixel scale s. Always shows the selected choices, regardless of feature toggles being on. */
    public static void figure(GuiGraphicsExtractor g, int ox, int oy, int s, double time) {
        boolean pilot = Config.on("pilot_flight_suits");
        int ustyle = Config.c("army_uniform", "style");
        boolean useCamo = Config.b("army_uniform", "use_camo");
        boolean uni = Config.on("army_uniform");
        boolean helmet = Config.on("military_helmets"), vest = Config.on("tactical_vests"),
            mask = Config.on("gas_masks"), pack = Config.on("military_backpacks"),
            tags = Config.on("dog_tags"), rank = Config.on("rank_insignia"), patches = Config.on("military_patches");

        // backpack (behind torso)
        if (pack) {
            float sz = Config.f("military_backpacks", "size");
            int style = Config.c("military_backpacks", "style");
            int w = (int) ((style == 1 ? 26 : 22) * sz), h = (int) ((style == 1 ? 34 : 26) * sz);
            int x = 23 - w / 2, y = 21;
            if (Config.b("camo_patterns", "apply_pack") && Config.on("camo_patterns")) camo(g, ox, oy, s, x, y, w, h, selectedPattern());
            else pr(g, ox, oy, s, x, y, w, h, 0xFF4A5230);
            pr(g, ox, oy, s, x, y + h - 2, w, 2, 0xFF22261A);
            if (style == 2) { pr(g, ox, oy, s, x + w - 4, y - 8, 1, 9, 0xFF111111); pr(g, ox, oy, s, x + w - 5, y - 9, 3, 2, 0xFF111111); }
            if (style == 3) { pr(g, ox, oy, s, x + w / 2 - 3, y + h / 2 - 1, 6, 2, 0xFFE8E8E8); pr(g, ox, oy, s, x + w / 2 - 1, y + h / 2 - 3, 2, 6, 0xFFD02020); }
        }

        // body colours
        int base; int pattern = -1;
        if (pilot) {
            int[] suits = { 0xFF7A8088, 0xFF4F5B33, 0xFFB59B68, 0xFF1E2024 };
            base = suits[Config.c("pilot_flight_suits", "style")];
        } else if (uni && ustyle == 4) base = 0xFF1A1A1C;
        else if (uni && ustyle == 5) base = 0xFF2F4A35;
        else if (uni && useCamo) { base = 0; pattern = uniformPattern(ustyle); }
        else base = 0xFF58603E;

        // legs, boots, torso, arms
        int[][] parts = { { 14, 52, 8, 32 }, { 24, 52, 8, 32 }, { 12, 22, 22, 31 }, { 5, 23, 7, 28 }, { 34, 23, 7, 28 } };
        for (int[] p : parts) {
            if (pattern >= 0) camo(g, ox, oy, s, p[0], p[1], p[2], p[3], pattern);
            else pr(g, ox, oy, s, p[0], p[1], p[2], p[3], base);
        }
        pr(g, ox, oy, s, 14, 84, 8, 8, BOOT); pr(g, ox, oy, s, 24, 84, 8, 8, BOOT);
        pr(g, ox, oy, s, 5, 51, 7, 4, SKIN); pr(g, ox, oy, s, 34, 51, 7, 4, SKIN);
        pr(g, ox, oy, s, 22, 52, 2, 32, Colors.scale(base == 0 ? 0xFF2A2A20 : base, 0.6f)); // leg gap shading
        if (pilot) { // zip, pockets, G-suit hoses
            pr(g, ox, oy, s, 22, 22, 2, 30, 0xFFCCCCCC);
            pr(g, ox, oy, s, 14, 40, 6, 5, Colors.scale(base, 0.75f)); pr(g, ox, oy, s, 26, 40, 6, 5, Colors.scale(base, 0.75f));
            if (Config.c("pilot_flight_suits", "style") == 0) pr(g, ox, oy, s, 12, 52, 22, 2, 0xFFFF8A1A);
        }
        if (uni && ustyle == 5 && !pilot) { // dress: belt, buttons, ribbons
            pr(g, ox, oy, s, 12, 46, 22, 2, 0xFF111111); pr(g, ox, oy, s, 22, 46, 2, 2, 0xFFE0B040);
            for (int i = 0; i < 4; i++) pr(g, ox, oy, s, 22, 26 + i * 5, 2, 2, 0xFFE0B040);
            pr(g, ox, oy, s, 15, 28, 6, 2, 0xFFC03030); pr(g, ox, oy, s, 15, 30, 6, 2, 0xFF3050C0);
        }

        // vest
        if (vest) {
            int style = Config.c("tactical_vests", "style");
            int top = style == 1 ? 22 : 24, bot = style == 3 ? 40 : 46;
            if (Config.b("camo_patterns", "apply_vest") && Config.on("camo_patterns")) camo(g, ox, oy, s, 13, top, 20, bot - top, selectedPattern());
            else pr(g, ox, oy, s, 13, top, 20, bot - top, 0xFF5B5642);
            pr(g, ox, oy, s, 13, top, 3, 6, 0xFF3C3A2C); pr(g, ox, oy, s, 30, top, 3, 6, 0xFF3C3A2C);
            if (style == 0) pr(g, ox, oy, s, 17, 28, 12, 11, 0xFF3F3D30);       // plate
            if (style == 1) { pr(g, ox, oy, s, 11, 22, 4, 5, 0xFF4B4838); pr(g, ox, oy, s, 31, 22, 4, 5, 0xFF4B4838); }
            if (Config.b("tactical_vests", "pouches")) {
                int n = style == 2 ? 5 : style == 3 ? 4 : 3;
                for (int i = 0; i < n; i++) pr(g, ox, oy, s, 14 + i * (18 / n), bot - 7, Math.max(2, 18 / n - 1), 6, 0xFF2E2C22);
            }
        }

        // head
        pr(g, ox, oy, s, 18, 9, 10, 12, SKIN);
        pr(g, ox, oy, s, 20, 13, 2, 2, 0xFF222222); pr(g, ox, oy, s, 24, 13, 2, 2, 0xFF222222);
        pr(g, ox, oy, s, 20, 18, 6, 1, 0xFF7A4A3A);
        pr(g, ox, oy, s, 20, 21, 6, 2, SKIN);

        if (mask) {
            int style = Config.c("gas_masks", "style");
            if (style == 3) { pr(g, ox, oy, s, 16, 7, 14, 17, 0xFF1C1E1A); pr(g, ox, oy, s, 19, 12, 8, 4, 0xFF304028); }
            else if (style == 1) { pr(g, ox, oy, s, 18, 16, 10, 6, 0xFF2A2C28); pr(g, ox, oy, s, 17, 18, 3, 3, 0xFF111111); pr(g, ox, oy, s, 26, 18, 3, 3, 0xFF111111); }
            else {
                pr(g, ox, oy, s, 17, 11, 12, 11, style == 2 ? 0xFF111111 : 0xFF3A3F32);
                pr(g, ox, oy, s, 18, 13, 4, 4, 0xFF9CC8A0); pr(g, ox, oy, s, 24, 13, 4, 4, 0xFF9CC8A0);
                pr(g, ox, oy, s, 21, 19, 4, 5, 0xFF222222);
            }
        }

        if (helmet) {
            int style = Config.c("military_helmets", "style");
            int hc = 0xFF4A5230;
            boolean cam = Config.b("camo_patterns", "apply_helmet") && Config.on("camo_patterns");
            int[] r;
            switch (style) {
                case 0: r = new int[] { 15, 5, 16, 9 }; break;       // PASGT
                case 1: r = new int[] { 16, 5, 14, 8 }; break;       // ACH
                case 2: r = new int[] { 16, 5, 14, 7 }; break;       // MICH
                case 3: r = new int[] { 17, 4, 12, 5 }; break;       // boonie crown
                case 4: r = new int[] { 15, 6, 15, 4 }; break;       // beret
                case 5: r = new int[] { 16, 4, 14, 7 }; break;       // ops-core
                default: r = new int[] { 15, 4, 16, 10 }; hc = 0xFFEDEDED; cam = false; break; // pilot
            }
            if (cam) camo(g, ox, oy, s, r[0], r[1], r[2], r[3], selectedPattern()); else pr(g, ox, oy, s, r[0], r[1], r[2], r[3], hc);
            if (style == 3) { if (cam) camo(g, ox, oy, s, 12, 9, 22, 2, selectedPattern()); else pr(g, ox, oy, s, 12, 9, 22, 2, hc); }
            if (style == 4) pr(g, ox, oy, s, 14, 8, 4, 2, 0xFF701818);
            if (style == 5) { pr(g, ox, oy, s, 14, 8, 1, 4, 0xFF222222); pr(g, ox, oy, s, 31, 8, 1, 4, 0xFF222222); }
            if (style == 6) pr(g, ox, oy, s, 18, 10, 10, 5, 0xCC1C2A38);
            if (Config.b("military_helmets", "nvg_mount")) { pr(g, ox, oy, s, 21, 3, 4, 3, 0xFF111111); pr(g, ox, oy, s, 22, 1, 2, 3, 0xFF2A5A2A); }
        }

        if (tags) {
            pr(g, ox, oy, s, 20, 22, 1, 5, 0xFF9A9A9A); pr(g, ox, oy, s, 25, 22, 1, 5, 0xFF9A9A9A);
            pr(g, ox, oy, s, 21, 27, 3, 5, METAL); pr(g, ox, oy, s, 23, 29, 3, 5, 0xFFA8ABAF);
        }
        if (patches) {
            int y = 26;
            for (String slot : new String[] { "slot1", "slot2", "slot3" }) {
                int t = Config.c("military_patches", slot);
                if (t > 0) { icon(g, ox, oy, s, 6, y, t); y += 8; }
            }
        }
        if (rank) {
            int r = Config.c("rank_insignia", "rank");
            int chevrons = r < 7 ? Math.max(1, r) : 0;
            int c = 0xFFE0B040;
            for (int i = 0; i < chevrons; i++) {
                pr(g, ox, oy, s, 35, 27 + i * 2, 2, 1, c); pr(g, ox, oy, s, 37, 26 + i * 2, 2, 1, c); pr(g, ox, oy, s, 39, 27 + i * 2, 2, 1, c);
            }
            if (r >= 7) { // officer bars / stars
                int n = r <= 9 ? r - 6 : 1;
                for (int i = 0; i < Math.min(n, 3); i++) pr(g, ox, oy, s, 36 + i * 2, 28, 1, 4, r <= 9 ? 0xFFE0E0E0 : 0xFFE0B040);
                if (r > 9) pr(g, ox, oy, s, 36, 27, 4, 4, 0xFFE0B040);
            }
        }

        // label
        String label = (rank ? Features.get("rank_insignia").settings.get(0).choices[Config.c("rank_insignia", "rank")] + "  " : "")
            + (tags ? Config.t("dog_tags", "name") : "OPERATOR");
        int lw = Gfx.headingW(label, 1);
        Gfx.heading(g, label, ox + (GW * s - lw) / 2, oy + GH * s + 4, Config.accent(), 1);
        if (patches) {
            String u = Config.t("military_patches", "unit");
            Gfx.heading(g, u, ox + (GW * s - Gfx.headingW(u, 1)) / 2, oy + GH * s + 16, 0xFF9AA090, 1);
        }
        if (tags) {
            String b = Features.get("dog_tags").settings.get(2).choices[Config.c("dog_tags", "blood")];
            String line = "BLOOD " + b + "  ID " + Config.t("dog_tags", "id");
            Gfx.heading(g, line, ox + (GW * s - Gfx.headingW(line, 1)) / 2, oy + GH * s + 28, 0xFF9AA090, 1);
        }
    }

    private static void icon(GuiGraphicsExtractor g, int ox, int oy, int s, int x, int y, int t) {
        switch (t) {
            case 1: pr(g, ox, oy, s, x, y, 6, 6, 0xFFE0B040); pr(g, ox, oy, s, x + 1, y + 1, 4, 4, 0xFF3C5A2A); break;
            case 2: pr(g, ox, oy, s, x, y, 6, 6, 0xFFE8E8E8); pr(g, ox, oy, s, x, y + 1, 6, 1, 0xFFC03030); pr(g, ox, oy, s, x, y + 3, 6, 1, 0xFFC03030); pr(g, ox, oy, s, x, y, 3, 3, 0xFF2A3A8A); break;
            case 3: pr(g, ox, oy, s, x, y, 6, 6, 0xFF111111); pr(g, ox, oy, s, x + 1, y + 1, 4, 3, 0xFFE8E8E8); pr(g, ox, oy, s, x + 2, y + 2, 1, 1, 0xFF111111); pr(g, ox, oy, s, x + 4, y + 2, 1, 1, 0xFF111111); break;
            case 4: pr(g, ox, oy, s, x, y, 6, 6, 0xFF8A6A3A); pr(g, ox, oy, s, x + 1, y + 1, 4, 1, 0xFFE0D0A0); pr(g, ox, oy, s, x + 2, y + 2, 2, 3, 0xFFE0D0A0); break;
            case 5: pr(g, ox, oy, s, x, y, 6, 6, 0xFF1C3A6A); pr(g, ox, oy, s, x, y + 2, 6, 1, 0xFFD0D4D8); pr(g, ox, oy, s, x + 1, y + 1, 1, 1, 0xFFD0D4D8); pr(g, ox, oy, s, x + 4, y + 1, 1, 1, 0xFFD0D4D8); break;
            case 6: pr(g, ox, oy, s, x, y, 6, 6, 0xFFE8E8E8); pr(g, ox, oy, s, x + 2, y + 1, 2, 4, 0xFFD02020); pr(g, ox, oy, s, x + 1, y + 2, 4, 2, 0xFFD02020); break;
            default: pr(g, ox, oy, s, x, y, 6, 6, 0xFF1C3A6A); pr(g, ox, oy, s, x + 2, y + 1, 2, 4, 0xFFFFFFFF); pr(g, ox, oy, s, x + 1, y + 2, 4, 2, 0xFFFFFFFF); break;
        }
    }

    // ------------------------------------------------------------------ first-person overlays

    public static void firstPerson(GuiGraphicsExtractor g, int w, int h, double time) {
        // visor
        if (Config.on("pilot_flight_suits") && Config.b("pilot_flight_suits", "visor")) {
            Gfx.gradientV(g, 0, 0, w, (int) (h * 0.16), 0xB0302A10, 0x00302A10);
            Gfx.rect(g, 0, (int) (h * 0.16), w, 1, 0x60FFB000);
            Gfx.gradientV(g, 0, 0, (int) (w * 0.05), h, 0x70000000, 0x70000000);
            Gfx.rect(g, w - (int) (w * 0.05), 0, (int) (w * 0.05), h, 0x70000000);
        }
        // helmet rim
        if (Config.on("military_helmets") && Config.b("military_helmets", "first_person_rim")) {
            int style = Config.c("military_helmets", "style");
            int band = (int) (h * (style == 3 ? 0.14 : style == 4 ? 0.04 : 0.08));
            Gfx.gradientV(g, 0, 0, w, band, 0xF0101208, 0x00101208);
            if (style == 4) Gfx.gradientV(g, 0, 0, (int) (w * 0.25), (int) (h * 0.12), 0xD0101208, 0x00101208);
            if (Config.b("military_helmets", "nvg_mount")) {
                Gfx.rect(g, w / 2 - 14, 0, 28, 5, 0xFF1A1A1A); Gfx.rect(g, w / 2 - 4, 5, 8, 4, 0xFF2A5A2A);
            }
        }
        // vest straps
        if (Config.on("tactical_vests") && Config.b("tactical_vests", "first_person_straps")) {
            int sw = (int) (w * 0.07), sh = (int) (h * 0.2);
            Gfx.gradientV(g, 0, h - sh, sw, sh, 0x005B5642, 0xF05B5642);
            Gfx.gradientV(g, w - sw, h - sh, sw, sh, 0x005B5642, 0xF05B5642);
            Gfx.rect(g, sw - 2, h - sh + 8, 2, sh - 8, 0xFF2E2C22); Gfx.rect(g, w - sw, h - sh + 8, 2, sh - 8, 0xFF2E2C22);
        }
        // gas mask
        if (Config.on("gas_masks") && Config.b("gas_masks", "first_person")) {
            int style = Config.c("gas_masks", "style");
            int edge = style == 3 ? (int) (w * 0.16) : style == 1 ? 0 : (int) (w * 0.11);
            int dark = style == 2 ? 0xF0080808 : 0xF0141810;
            if (style == 1) { // half mask: only a bottom rubber bulge
                Gfx.gradientV(g, 0, (int) (h * 0.82), w, (int) (h * 0.18), 0x00141810, 0xF0141810);
                Gfx.rect(g, w / 2 - 24, h - 12, 48, 12, 0xF0141810);
            } else {
                Gfx.rect(g, 0, 0, edge, h, dark); Gfx.rect(g, w - edge, 0, edge, h, dark);
                int tb = (int) (h * 0.09);
                Gfx.gradientV(g, edge, 0, w - 2 * edge, tb, dark, 0x00141810);
                Gfx.gradientV(g, edge, h - tb * 2, w - 2 * edge, tb * 2, 0x00141810, dark);
                Gfx.rect(g, w / 2 - 3, 0, 6, h, 0x60101010);  // nose bridge divider
                Gfx.ring(g, w / 4, h / 2, (int) (h * 0.42), 0x80101010);
                Gfx.ring(g, 3 * w / 4, h / 2, (int) (h * 0.42), 0x80101010);
            }
            if (Config.b("gas_masks", "breathing")) {
                float f = (float) (0.5 + 0.5 * Math.sin(time * 1.15));
                Gfx.rect(g, 0, 0, w, h, Colors.alpha(0xFFFFFFFF, (int) (f * f * 34)));
            }
        }
    }

    public static boolean anyFirstPerson() {
        return (Config.on("pilot_flight_suits") && Config.b("pilot_flight_suits", "visor"))
            || (Config.on("military_helmets") && Config.b("military_helmets", "first_person_rim"))
            || (Config.on("tactical_vests") && Config.b("tactical_vests", "first_person_straps"))
            || (Config.on("gas_masks") && Config.b("gas_masks", "first_person"));
    }

    /** Short rank/name header for the tactical HUD, or null when neither is enabled for HUD display. */
    public static String hudHeader() {
        StringBuilder sb = new StringBuilder();
        if (Config.on("rank_insignia") && Config.b("rank_insignia", "show_in_hud")) {
            Setting s = Features.get("rank_insignia").settings.get(0);
            sb.append(s.choices[Config.c("rank_insignia", "rank")]).append(' ');
        }
        if (Config.on("dog_tags") && Config.b("dog_tags", "show_in_hud")) sb.append(Config.t("dog_tags", "name"));
        String r = sb.toString().trim();
        return r.isEmpty() ? null : r;
    }
}
