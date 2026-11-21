package com.militaryclient.util;

import com.militaryclient.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Drawing helpers built only on GuiGraphicsExtractor.fill / text, which are documented for 26.3. */
public final class Gfx {
    /** 0 vanilla, 1 tactical pixel, 2 stencil, 3 digital LCD. Refreshed once per frame by the HUD. */
    public static int fontMode = 0;
    public static float spacing = 1f;
    public static float textScale = 1f;

    private static GuiGraphicsExtractor cur;
    private static final PixelFont.Sink SINK = (x, y, w, h, c) -> cur.fill(x, y, x + w, y + h, c);

    private Gfx() {}

    public static void refreshFromConfig() {
        if (Config.on("military_fonts") && Config.b("military_fonts", "apply_hud")) {
            fontMode = Config.c("military_fonts", "font");
            spacing = Config.f("military_fonts", "spacing");
        } else {
            fontMode = 0;
            spacing = 1f;
        }
        textScale = Config.f("global", "text_scale");
    }

    // ------------------------------------------------------------------ primitives

    public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0 && (c >>> 24) != 0) g.fill(x, y, x + w, y + h, c);
    }

    public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        if (w <= 1 || h <= 1) return;
        rect(g, x, y, w, 1, c);
        rect(g, x, y + h - 1, w, 1, c);
        rect(g, x, y + 1, 1, h - 2, c);
        rect(g, x + w - 1, y + 1, 1, h - 2, c);
    }

    public static void hline(GuiGraphicsExtractor g, int x, int y, int w, int c) { rect(g, x, y, w, 1, c); }

    public static void vline(GuiGraphicsExtractor g, int x, int y, int h, int c) { rect(g, x, y, 1, h, c); }

    /** Bresenham line, 1px wide. Intended for short lines (radar, reticles). */
    public static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        for (int guard = 0; guard < 2000; guard++) {
            g.fill(x0, y0, x0 + 1, y0 + 1, c);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    public static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int c) {
        for (int y = -r; y <= r; y++) {
            int half = (int) Math.round(Math.sqrt((double) r * r - (double) y * y));
            g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, c);
        }
    }

    public static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int c) {
        int prev = r;
        for (int y = 0; y <= r; y++) {
            int half = (int) Math.round(Math.sqrt((double) r * r - (double) y * y));
            int from = y == 0 ? half : Math.min(prev, half);
            int to = y == 0 ? half : Math.max(prev, half);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int yy = cy + sgn * y;
                g.fill(cx - to, yy, cx - from + 1, yy + 1, c);
                g.fill(cx + from, yy, cx + to + 1, yy + 1, c);
            }
            prev = half;
        }
    }

    public static void ellipse(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, int c) {
        if (rx < 1 || ry < 1) return;
        for (int y = -ry; y <= ry; y++) {
            double t = (double) y / ry;
            int half = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - t * t)));
            g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, c);
        }
    }

    public static void gradientV(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    // ------------------------------------------------------------------ text

    private static int pixelSize(float scale) { return Math.max(1, Math.round(scale)); }

    public static int textW(String s, float scale) {
        if (fontMode == 0) return (int) Math.ceil(Minecraft.getInstance().font.width(s) * scale);
        return PixelFont.width(s, pixelSize(scale), spacing);
    }

    public static int textH(float scale) {
        return fontMode == 0 ? (int) Math.ceil(9 * scale) : PixelFont.height(pixelSize(scale));
    }

    /** HUD text using the configured military font. scale is multiplied by the global text scale. */
    public static void text(GuiGraphicsExtractor g, String s, int x, int y, int argb, float scale) {
        scale *= textScale;
        if (fontMode == 0) {
            vanilla(g, s, x, y, argb, scale);
        } else {
            cur = g;
            int p = pixelSize(scale);
            // shadow for legibility over bright terrain
            PixelFont.draw(SINK, s, x + Math.max(1, p / 2), y + Math.max(1, p / 2), 0xB0000000, p, fontMode, spacing);
            PixelFont.draw(SINK, s, x, y, argb, p, fontMode, spacing);
        }
    }

    public static int textWS(String s, float scale) { return textW(s, scale * textScale); }

    public static int textHS(float scale) { return textH(scale * textScale); }

    public static void textCentered(GuiGraphicsExtractor g, String s, int cx, int y, int argb, float scale) {
        text(g, s, cx - textWS(s, scale) / 2, y, argb, scale);
    }

    /** Always-vanilla font text (control panel body text). */
    public static void vanilla(GuiGraphicsExtractor g, String s, int x, int y, int argb, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (Math.abs(scale - 1f) < 0.01f) {
            g.text(mc.font, s, x, y, argb, true);
        } else {
            g.pose().pushMatrix();
            g.pose().translate((float) x, (float) y);
            g.pose().scale(scale, scale);
            g.text(mc.font, s, 0, 0, argb, true);
            g.pose().popMatrix();
        }
    }

    /** Heading text for the control panel: military font when enabled, else vanilla. */
    public static void heading(GuiGraphicsExtractor g, String s, int x, int y, int argb, int pixel) {
        if (Config.on("military_fonts") && Config.b("military_fonts", "apply_panel")) {
            int mode = Math.max(1, Config.c("military_fonts", "font"));
            cur = g;
            PixelFont.draw(SINK, s, x, y, argb, pixel, mode, 1f);
        } else {
            vanilla(g, s, x, y, argb, 1f);
        }
    }

    public static int headingW(String s, int pixel) {
        if (Config.on("military_fonts") && Config.b("military_fonts", "apply_panel"))
            return PixelFont.width(s, pixel, 1f);
        return Minecraft.getInstance().font.width(s);
    }

    // ------------------------------------------------------------------ HUD blocks

    public static void begin(GuiGraphicsExtractor g, float x, float y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
    }

    public static void end(GuiGraphicsExtractor g) { g.pose().popMatrix(); }

    /** Standard HUD plate: dark translucent body, accent bar and corner ticks. Local coordinates. */
    public static void plate(GuiGraphicsExtractor g, int w, int h, int accent, float opacity) {
        int bg = Colors.mulA(0xFF0B0E09, 0.78f * opacity);
        rect(g, 0, 0, w, h, bg);
        rect(g, 0, 0, 2, h, Colors.mulA(accent, opacity));
        int c = Colors.mulA(accent, opacity);
        rect(g, w - 5, 0, 5, 1, c); rect(g, w - 1, 0, 1, 5, c);
        rect(g, 0, h - 1, 5, 1, c); rect(g, w - 5, h - 1, 5, 1, c); rect(g, w - 1, h - 5, 1, 5, c);
    }

    public static float globalOpacity() { return Config.f("global", "opacity"); }
}
