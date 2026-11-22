package com.militaryclient.ui;

import com.militaryclient.MilitaryClient;
import com.militaryclient.config.Config;
import com.militaryclient.config.Feature;
import com.militaryclient.config.Features;
import com.militaryclient.config.Presets;
import com.militaryclient.config.Setting;
import com.militaryclient.config.Waypoint;
import com.militaryclient.hud.Cosmetics;
import com.militaryclient.util.Colors;
import com.militaryclient.util.Compat;
import com.militaryclient.util.Dim;
import com.militaryclient.util.Gfx;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

/** The centralized military control panel. Every control is generated from the feature registry. */
public final class ControlPanelScreen extends Screen {
    private static final String[] TABS = { "DASHBOARD", "HUD & TACTICAL", "COSMETICS", "ATMOSPHERE",
        "AIRCRAFT & VEHICLE", "IMMERSION", "GENERAL", "KEYBINDS" };
    private static final Feature.Category[] TAB_CAT = { null, Feature.Category.HUD, Feature.Category.COSMETICS,
        Feature.Category.ATMOSPHERE, Feature.Category.AIRCRAFT, Feature.Category.IMMERSION, Feature.Category.GENERAL, null };

    private static int tab = 0;
    private static final float[] SCROLL = new float[TABS.length];
    private static final Set<String> OPEN = new HashSet<>();

    private static final class Region {
        int x, y, w, h;
        DoubleConsumer press, drag;
        float clip1, clip2;
    }

    private final Screen parent;
    private final List<Region> regions = new ArrayList<>();
    private Region dragging;
    private String search = "";
    private String focus;
    private KeyMapping capturing;
    private String toast = "";
    private long toastUntil, confirmAt;
    private String wpName = "";

    // frame state
    private float S = 1f;
    private int vw, vh, px, py, pw, ph, mx, my;
    private int vx1, vy1, vx2, vy2; // content viewport (virtual coords)
    private int contentH;
    private double time;

    public ControlPanelScreen(Screen parent) {
        super(Component.literal("Military Control Panel"));
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Config.save();
        MilitaryClient.mirrorKeys();
        Compat.setScreen(parent);
    }

    // =================================================================== input

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (capturing != null) {
            if (k == 256) toast("Rebind cancelled");
            else if (k == 259 || k == 261) { rebind(capturing, -1); toast(name(capturing) + " unbound"); }
            else { rebind(capturing, k); toast(name(capturing) + " -> " + capturing.getTranslatedKeyMessage().getString()); }
            capturing = null;
            return true;
        }
        if (focus != null) {
            if (k == 256 || k == 257 || k == 335) { focus = null; return true; }
            if (k == 259) { editFocused(-1, null); return true; }
            boolean printable = k >= 32 && k <= 96;
            if (!(MilitaryClient.OPEN.matches(e) && !printable)) return true; // swallow keys while typing
        }
        if (MilitaryClient.OPEN.matches(e)) { onClose(); return true; }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (focus == null) return false;
        int cp = e.codepoint();
        if (cp < 32 || cp == 127) return true;
        editFocused(cp, new String(Character.toChars(cp)));
        return true;
    }

    private void editFocused(int cp, String ch) {
        if (focus.equals("search")) {
            if (cp < 0) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); }
            else if (search.length() < 24) search += ch;
            SCROLL[tab] = 0;
        } else if (focus.equals("wpname")) {
            if (cp < 0) { if (!wpName.isEmpty()) wpName = wpName.substring(0, wpName.length() - 1); }
            else if (wpName.length() < 14) wpName += ch;
        } else {
            int dot = focus.indexOf('.');
            String fk = focus.substring(0, dot), sk = focus.substring(dot + 1);
            String cur = Config.t(fk, sk);
            if (cp < 0) { if (!cur.isEmpty()) cur = cur.substring(0, cur.length() - 1); }
            else cur += ch;
            Config.setText(fk, sk, cur);
        }
    }

    private long handledAt;
    private boolean polledDown;
    private double lastVx, lastVy;

    /** Handles a left click at virtual coordinates (the same space the controls are drawn in). */
    private void press(double x, double y) {
        handledAt = System.nanoTime();
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (x >= r.x && x < r.x + r.w && y >= r.y && y < r.y + r.h && y >= r.clip1 && y <= r.clip2) {
                if (r.press != null) r.press.accept(x);
                if (r.drag != null) dragging = r;
                return;
            }
        }
        focus = null;
    }

    // Clicks use the mouse position Minecraft hands to extractRenderState (the one hover uses), not the event's own
    // x/y, so they cannot land in the wrong coordinate space. A polled fallback covers a missing click event; a
    // short de-dupe window stops both paths from firing for the same click.
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0) return false;
        if (System.nanoTime() - handledAt > 150_000_000L) press(lastVx, lastVy);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null && dragging.drag != null) { dragging.drag.accept(lastVx); return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        SCROLL[tab] = Math.max(0, Math.min(Math.max(0, contentH - (vy2 - vy1)), SCROLL[tab] - (float) sy * 28));
        return true;
    }

    private void toast(String s) { toast = s; toastUntil = System.currentTimeMillis() + 3500; }

    // =================================================================== frame

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Gfx.refreshFromConfig();
        // Shrink the panel automatically on high GUI scales so it always fits (needs about 664 x 432 virtual px).
        S = Math.max(0.4f, Math.min(Config.f("global", "ui_scale"), Math.min(width / 664f, height / 432f)));
        lastVx = mouseX / S; lastVy = mouseY / S;
        boolean down = Compat.leftMouseDown();
        if (down && !polledDown && System.nanoTime() - handledAt > 150_000_000L) press(lastVx, lastVy);
        if (!down && polledDown) dragging = null;
        if (down && dragging != null && dragging.drag != null) dragging.drag.accept(lastVx);
        polledDown = down;
        vw = (int) (width / S); vh = (int) (height / S);
        mx = (int) lastVx; my = (int) lastVy;
        time = System.nanoTime() / 1e9;
        regions.clear();
        pw = Math.min(vw - 12, 660); ph = Math.min(vh - 12, 420);
        px = (vw - pw) / 2; py = (vh - ph) / 2;

        g.fill(0, 0, width, height, 0xB4050604);
        g.pose().pushMatrix();
        g.pose().scale(S, S);

        Gfx.rect(g, px - 2, py - 2, pw + 4, ph + 4, 0xFF0A0C08);
        Gfx.rect(g, px, py, pw, ph, Colors.BG);
        Gfx.frame(g, px, py, pw, ph, Colors.OLIVE);
        // title bar
        Gfx.rect(g, px, py, pw, 24, Colors.PANEL);
        Gfx.rect(g, px, py + 24, pw, 1, Colors.OLIVE);
        Gfx.heading(g, "MILITARY CLIENT // COMMAND TERMINAL", px + 10, py + 8, Colors.OLIVE_HI, 1);
        boolean blink = ((long) (time * 2)) % 2 == 0;
        Gfx.disc(g, px + pw - 14, py + 12, 3, blink ? 0xFF3CFF7A : 0xFF1F7A3E);
        Gfx.vanilla(g, "ONLINE", px + pw - 56, py + 8, Colors.DIM, 1f);
        // hazard stripe
        for (int i = 0; i < pw; i += 12) Gfx.rect(g, px + i, py + ph - 3, 6, 3, (i / 12) % 2 == 0 ? Colors.AMBER : 0xFF111111);

        drawSidebar(g);
        drawSearch(g);

        vx1 = px + 124; vx2 = px + pw - 8; vy1 = py + 52; vy2 = py + ph - 22;
        int t = tab;
        g.enableScissor((int) (vx1 * S), (int) (vy1 * S), (int) (vx2 * S), (int) (vy2 * S));
        int y = vy1 - (int) SCROLL[t];
        int startY = y;
        if (!search.isEmpty()) y = drawSearchResults(g, y);
        else if (t == 0) y = drawDashboard(g, y);
        else if (t == 7) y = drawKeybinds(g, y);
        else if (t == 6) y = drawGeneral(g, y);
        else y = drawCategory(g, y, TAB_CAT[t]);
        contentH = y - startY;
        g.disableScissor();

        float max = Math.max(0, contentH - (vy2 - vy1));
        SCROLL[t] = Math.max(0, Math.min(max, SCROLL[t]));
        if (max > 0) {
            int bh = Math.max(20, (int) ((vy2 - vy1) * ((vy2 - vy1) / (float) contentH)));
            int by = vy1 + (int) ((vy2 - vy1 - bh) * (SCROLL[t] / max));
            Gfx.rect(g, vx2 + 2, vy1, 3, vy2 - vy1, 0x30FFFFFF);
            Gfx.rect(g, vx2 + 2, by, 3, bh, Colors.OLIVE_HI);
        }

        // footer
        Gfx.rect(g, px, py + ph - 20, pw, 1, 0xFF2A2E24);
        String foot = System.currentTimeMillis() < toastUntil ? toast
            : (Config.lastError != null ? Config.lastError : "Auto-save ON  |  Preset: " + Config.preset() + "  |  Client-side only");
        boolean warn = System.currentTimeMillis() < toastUntil || Config.lastError != null;
        Gfx.vanilla(g, foot, px + 8, py + ph - 15, warn ? Colors.AMBER : Colors.DIM, 1f);
        g.pose().popMatrix();
    }

    private void drawSidebar(GuiGraphicsExtractor g) {
        int x = px + 6, y = py + 32;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            boolean sel = tab == i;
            boolean hover = mx >= x && mx < x + 110 && my >= y && my < y + 22;
            Gfx.rect(g, x, y, 110, 22, sel ? Colors.OLIVE : hover ? Colors.CARD_HI : Colors.CARD);
            if (sel) Gfx.rect(g, x, y, 3, 22, Colors.OLIVE_HI);
            Gfx.vanilla(g, TABS[i], x + 8, y + 7, sel ? 0xFFFFFFFF : Colors.TEXT, 1f);
            if (TAB_CAT[i] != null && TAB_CAT[i] != Feature.Category.GENERAL) {
                String c = Config.enabledCount(TAB_CAT[i]) + "";
                Gfx.vanilla(g, c, x + 106 - Minecraft.getInstance().font.width(c) - 2, y + 7, sel ? 0xFFFFFFFF : Colors.OLIVE_HI, 1f);
            }
            region(x, y, 110, 22, v -> { tab = idx; focus = null; }, null, -1000, 10000);
            y += 25;
        }
    }

    private void drawSearch(GuiGraphicsExtractor g) {
        int x = px + 124, y = py + 30, w = pw - 132, h = 16;
        Gfx.rect(g, x, y, w, h, Colors.DARK);
        Gfx.frame(g, x, y, w, h, "search".equals(focus) ? Colors.OLIVE_HI : 0xFF3A4030);
        String shown = search.isEmpty() && !"search".equals(focus) ? "SEARCH FEATURES..." : search
            + ("search".equals(focus) && ((long) (time * 2)) % 2 == 0 ? "_" : "");
        Gfx.vanilla(g, shown, x + 5, y + 4, search.isEmpty() ? Colors.DIM : Colors.TEXT, 1f);
        if (!search.isEmpty()) {
            button(g, x + w - 16, y + 2, 12, 12, "x", Colors.CARD_HI, Colors.TEXT, v -> { search = ""; SCROLL[tab] = 0; }, -1000, 10000);
        }
        region(x, y, w - 18, h, v -> focus = "search", null, -1000, 10000);
    }

    // =================================================================== widgets

    private void region(int x, int y, int w, int h, DoubleConsumer press, DoubleConsumer drag, float c1, float c2) {
        Region r = new Region();
        r.x = x; r.y = y; r.w = w; r.h = h; r.press = press; r.drag = drag; r.clip1 = c1; r.clip2 = c2;
        regions.add(r);
    }

    private boolean visible(int y, int h) { return y + h > vy1 && y < vy2; }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int bg, int fg, DoubleConsumer press,
                        float c1, float c2) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h && my >= c1 && my <= c2;
        Gfx.rect(g, x, y, w, h, hover ? Colors.scale(bg, 1.25f) : bg);
        Gfx.frame(g, x, y, w, h, 0x30FFFFFF);
        int tw = Minecraft.getInstance().font.width(label);
        Gfx.vanilla(g, label, x + (w - tw) / 2, y + (h - 8) / 2, fg, 1f);
        if (press != null) region(x, y, w, h, press, null, c1, c2);
    }

    private void cbutton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int bg, int fg, Runnable r) {
        if (!visible(y, h)) return;
        button(g, x, y, w, h, label, bg, fg, v -> r.run(), vy1, vy2);
    }

    private List<String> wrap(String s, int maxW) {
        List<String> out = new ArrayList<>();
        var font = Minecraft.getInstance().font;
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(t) > maxW && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(t);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void sectionTitle(GuiGraphicsExtractor g, String s, int x, int y, int w) {
        if (visible(y, 12)) {
            Gfx.heading(g, s, x, y + 2, Colors.OLIVE_HI, 1);
            Gfx.rect(g, x + Gfx.headingW(s, 1) + 6, y + 6, w - Gfx.headingW(s, 1) - 6, 1, Colors.OLIVE);
        }
    }

    // =================================================================== feature cards

    private int cardW() { return vx2 - vx1 - 8; }

    private int drawCategory(GuiGraphicsExtractor g, int y, Feature.Category c) {
        int x = vx1, w = cardW();
        if (c == Feature.Category.COSMETICS) w -= 170;
        if (visible(y, 20)) {
            Gfx.vanilla(g, c.title, x, y + 5, Colors.TEXT, 1f);
            int bx = x + w - 3 * 70 - 6;
            cbutton(g, bx, y + 1, 66, 14, "ENABLE ALL", Colors.OLIVE, 0xFFFFFFFF, () -> Config.setAll(c, true));
            cbutton(g, bx + 70, y + 1, 66, 14, "DISABLE ALL", 0xFF4A3A2A, 0xFFFFFFFF, () -> Config.setAll(c, false));
            cbutton(g, bx + 140, y + 1, 66, 14, "RESET", Colors.CARD_HI, Colors.TEXT, () -> { Config.resetCategory(c); toast(c.title + " reset to defaults"); });
        }
        int y0 = y;
        y += 20;
        for (Feature f : Features.ofCategory(c)) {
            if (f.key.equals("global") || f.key.equals("tracking")) continue;
            y += card(g, f, x, y, w);
        }
        if (c == Feature.Category.COSMETICS) {
            int fx = x + w + 10;
            int s = (vy2 - vy1) >= 360 ? 3 : 2;
            Gfx.rect(g, fx - 4, vy1, 160, vy2 - vy1, Colors.PANEL);
            Gfx.frame(g, fx - 4, vy1, 160, vy2 - vy1, 0xFF2E3328);
            Gfx.heading(g, "OPERATOR LOADOUT", fx + 4, vy1 + 6, Colors.OLIVE_HI, 1);
            Cosmetics.figure(g, fx + (152 - Cosmetics.figureWidth(s)) / 2 - 4, vy1 + 22, s, time);
        }
        return y + 6;
    }

    private int drawSearchResults(GuiGraphicsExtractor g, int y) {
        String q = search.toLowerCase();
        int x = vx1, w = cardW();
        int y0 = y;
        sectionTitle(g, "SEARCH RESULTS", x, y, w);
        y += 18;
        int n = 0;
        for (Feature f : Features.ALL) {
            if (f.name.toLowerCase().contains(q) || f.desc.toLowerCase().contains(q) || f.key.replace('_', ' ').contains(q)
                || f.cat.title.toLowerCase().contains(q)) {
                y += card(g, f, x, y, w);
                n++;
            }
        }
        if (n == 0 && visible(y, 12)) Gfx.vanilla(g, "No feature matches '" + search + "'.", x + 4, y + 4, Colors.AMBER, 1f);
        return y + (n == 0 ? 20 : 6);
    }

    private int card(GuiGraphicsExtractor g, Feature f, int x, int y, int w) {
        boolean sys = !f.toggleable;
        boolean open = sys || OPEN.contains(f.key);
        List<String> desc = wrap(f.desc, w - 24), lim = f.limitation == null ? List.of() : wrap("! " + f.limitation, w - 24);
        List<String> st = f.status == null ? List.of() : wrap("FAULT: " + f.status, w - 24);
        int h = 24 + desc.size() * 10 + lim.size() * 10 + st.size() * 10 + 4;
        int rows = open ? f.settings.size() : 0;
        h += rows * 20 + (rows > 0 ? 6 : 0);
        if (!visible(y, h)) return h + 6;

        boolean on = sys || Config.on(f.key);
        Gfx.rect(g, x, y, w, h, Colors.CARD);
        Gfx.rect(g, x, y, 3, h, on ? Colors.OLIVE_HI : 0xFF4A4E44);
        if (Config.isModified(f)) Gfx.rect(g, x + w - 3, y, 3, h, Colors.AMBER);
        // header
        if (!sys) {
            cbuttonRaw(g, x + 8, y + 5, 34, 14, on ? "ON" : "OFF", on ? Colors.OLIVE : 0xFF4A3A2A, 0xFFFFFFFF, () -> Config.setOn(f.key, !Config.on(f.key)));
        } else {
            Gfx.vanilla(g, "SYS", x + 10, y + 8, Colors.DIM, 1f);
        }
        Gfx.heading(g, f.title(), x + 48, y + 8, on ? Colors.TEXT : Colors.DIM, 1);
        int rx = x + w - 6;
        boolean modified = Config.isModified(f);
        rx -= 40;
        cbuttonRaw(g, rx, y + 5, 38, 14, "RESET", modified ? Colors.CARD_HI : 0xFF2A2D26, modified ? Colors.TEXT : 0xFF6A6E64,
            () -> { Config.resetFeature(f); toast(f.name + " reset"); });
        if (!f.settings.isEmpty() && !sys) {
            rx -= 66;
            cbuttonRaw(g, rx, y + 5, 62, 14, open ? "SETTINGS v" : "SETTINGS >", Colors.CARD_HI, Colors.TEXT,
                () -> { if (!OPEN.remove(f.key)) OPEN.add(f.key); });
        }
        int ty = y + 24;
        for (String l : desc) { Gfx.vanilla(g, l, x + 10, ty, Colors.DIM, 1f); ty += 10; }
        for (String l : lim) { Gfx.vanilla(g, l, x + 10, ty, Colors.AMBER, 1f); ty += 10; }
        for (String l : st) { Gfx.vanilla(g, l, x + 10, ty, Colors.RED, 1f); ty += 10; }
        if (open) {
            if (rows > 0) { Gfx.rect(g, x + 10, ty + 1, w - 20, 1, 0xFF3A4030); ty += 6; }
            for (Setting s : f.settings) { settingRow(g, f, s, x, ty, w); ty += 20; }
        }
        return h + 6;
    }

    private void cbuttonRaw(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int bg, int fg, Runnable r) {
        button(g, x, y, w, h, label, bg, fg, v -> r.run(), vy1, vy2);
    }

    private void settingRow(GuiGraphicsExtractor g, Feature f, Setting s, int x, int y, int w) {
        if (!visible(y, 20)) return;
        Gfx.vanilla(g, s.label, x + 12, y + 6, Colors.TEXT, 1f);
        int cx = x + Math.min(210, w / 2 + 10), cw = x + w - 12 - cx;
        String k = f.key, sk = s.key;
        switch (s.kind) {
            case BOOL: {
                boolean v = Config.b(k, sk);
                cbuttonRaw(g, cx, y + 3, 34, 14, v ? "ON" : "OFF", v ? Colors.OLIVE : 0xFF4A3A2A, 0xFFFFFFFF, () -> Config.set(k, sk, v ? 0 : 1));
                break;
            }
            case SLIDER: case INT: {
                double v = Config.n(k, sk);
                int tx = cx, tw = Math.max(40, cw - 50);
                Gfx.rect(g, tx, y + 8, tw, 4, 0xFF0C0E0A);
                double frac = (v - s.min) / (s.max - s.min);
                Gfx.rect(g, tx, y + 8, (int) (tw * frac), 4, Colors.OLIVE_HI);
                Gfx.rect(g, tx + (int) (tw * frac) - 2, y + 5, 5, 10, 0xFFEDEFE6);
                Gfx.vanilla(g, s.format(v), tx + tw + 8, y + 6, Colors.TEXT, 1f);
                DoubleConsumer set = vx -> Config.set(k, sk, s.min + Math.max(0, Math.min(1, (vx - tx) / tw)) * (s.max - s.min));
                region(tx - 3, y + 2, tw + 6, 16, set, set, vy1, vy2);
                break;
            }
            case CHOICE: {
                int cur = Config.c(k, sk);
                int n = s.choices.length;
                cbuttonRaw(g, cx, y + 3, 14, 14, "<", Colors.CARD_HI, Colors.TEXT, () -> Config.set(k, sk, Math.floorMod(cur - 1, n)));
                int lw = Math.min(cw - 32, 130);
                Gfx.rect(g, cx + 16, y + 3, lw, 14, 0xFF0C0E0A);
                String label = s.choices[cur];
                Gfx.vanilla(g, label, cx + 16 + (lw - Minecraft.getInstance().font.width(label)) / 2, y + 6, Colors.TEXT, 1f);
                cbuttonRaw(g, cx + 18 + lw, y + 3, 14, 14, ">", Colors.CARD_HI, Colors.TEXT, () -> Config.set(k, sk, Math.floorMod(cur + 1, n)));
                break;
            }
            case COLOR: {
                int cur = Config.i(k, sk);
                for (int i = 0; i < Setting.PALETTE.length; i++) {
                    final int idx = i;
                    int sx = cx + i * 13;
                    int c = i == 0 ? Config.accent() : Setting.PALETTE[i];
                    Gfx.rect(g, sx, y + 4, 11, 12, c);
                    if (i == 0) Gfx.rect(g, sx + 4, y + 8, 3, 3, 0xFF000000);
                    if (i == cur) Gfx.frame(g, sx - 1, y + 3, 13, 14, 0xFFFFFFFF);
                    region(sx, y + 4, 11, 12, v -> Config.set(k, sk, idx), null, vy1, vy2);
                }
                Gfx.vanilla(g, Setting.PALETTE_NAMES[cur], cx + Setting.PALETTE.length * 13 + 4, y + 6, Colors.DIM, 1f);
                break;
            }
            default: { // TEXT
                String key = k + "." + sk;
                boolean foc = key.equals(focus);
                int bw = Math.min(cw, 200);
                Gfx.rect(g, cx, y + 3, bw, 14, 0xFF0C0E0A);
                Gfx.frame(g, cx, y + 3, bw, 14, foc ? Colors.OLIVE_HI : 0xFF3A4030);
                String t = Config.t(k, sk) + (foc && ((long) (time * 2)) % 2 == 0 ? "_" : "");
                var font = Minecraft.getInstance().font;
                while (font.width(t) > bw - 8 && t.length() > 1) t = t.substring(1);
                Gfx.vanilla(g, t, cx + 4, y + 6, Colors.TEXT, 1f);
                region(cx, y + 3, bw, 14, v -> focus = key, null, vy1, vy2);
            }
        }
    }

    // =================================================================== dashboard

    private int drawDashboard(GuiGraphicsExtractor g, int y) {
        int x = vx1, w = cardW();
        sectionTitle(g, "SYSTEM STATUS", x, y, w);
        y += 18;
        int total = 0, on = 0;
        for (Feature.Category c : new Feature.Category[] { Feature.Category.HUD, Feature.Category.COSMETICS,
            Feature.Category.ATMOSPHERE, Feature.Category.AIRCRAFT, Feature.Category.IMMERSION }) {
            int e = Config.enabledCount(c), t = Config.toggleableCount(c);
            total += t; on += e;
            if (visible(y, 14)) {
                Gfx.vanilla(g, c.title, x + 4, y + 3, Colors.TEXT, 1f);
                int bx = x + 190, bw = Math.min(200, w - 260);
                Gfx.rect(g, bx, y + 4, bw, 6, 0xFF0C0E0A);
                Gfx.rect(g, bx, y + 4, t == 0 ? 0 : bw * e / t, 6, Colors.OLIVE_HI);
                Gfx.vanilla(g, e + "/" + t, bx + bw + 8, y + 3, Colors.DIM, 1f);
            }
            y += 16;
        }
        if (visible(y, 14)) Gfx.vanilla(g, "TOTAL ACTIVE: " + on + " / " + total + " FEATURES", x + 4, y + 3, Colors.OLIVE_HI, 1f);
        y += 22;

        y = presetBlock(g, y, x, w);
        y = fileBlock(g, y, x, w);

        sectionTitle(g, "FEATURE FAULTS", x, y, w);
        y += 18;
        boolean any = false;
        for (Feature f : Features.ALL) {
            if (f.status == null) continue;
            any = true;
            for (String l : wrap(f.name + ": " + f.status, w - 16)) {
                if (visible(y, 10)) Gfx.vanilla(g, l, x + 8, y, Colors.RED, 1f);
                y += 10;
            }
        }
        if (!any) { if (visible(y, 10)) Gfx.vanilla(g, "All enabled features loaded without errors.", x + 8, y, Colors.DIM, 1f); y += 10; }
        y += 14;

        sectionTitle(g, "CLIENT-SIDE NOTES", x, y, w);
        y += 18;
        String[] notes = {
            "Everything runs on your client. Servers never see this mod and nothing is sent to them.",
            "Radar, thermal and labels only use what you can see (line of sight): no X-ray advantage.",
            "Ambient audio is synthesised in the mod; real jets and helicopters do not exist in vanilla.",
            "Player cosmetics are shown in the Operator Loadout and first-person overlays, not as 3D model layers.",
            "Each feature card lists its own limitation in amber when it has one."
        };
        for (String n : notes) for (String l : wrap("- " + n, w - 16)) {
            if (visible(y, 10)) Gfx.vanilla(g, l, x + 8, y, Colors.DIM, 1f);
            y += 10;
        }
        return y + 10;
    }

    private int presetBlock(GuiGraphicsExtractor g, int y, int x, int w) {
        sectionTitle(g, "PRESETS (visual + audio only)", x, y, w);
        y += 18;
        int bw = (w - 16) / 3 - 4;
        for (int i = 0; i < Presets.ALL.size(); i++) {
            Presets.Preset p = Presets.ALL.get(i);
            int bx = x + 4 + (i % 3) * (bw + 6), by = y + (i / 3) * 22;
            boolean act = p.name.equals(Config.preset());
            cbutton(g, bx, by, bw, 18, p.name.toUpperCase(), act ? Colors.OLIVE : Colors.CARD_HI, 0xFFFFFFFF, () -> { p.apply(); toast("Preset applied: " + p.name); });
        }
        return y + 52;
    }

    private int fileBlock(GuiGraphicsExtractor g, int y, int x, int w) {
        sectionTitle(g, "CONFIGURATION", x, y, w);
        y += 18;
        int bw = (w - 16) / 4 - 4;
        cbutton(g, x + 4, y, bw, 18, "SAVE NOW", Colors.OLIVE, 0xFFFFFFFF, () -> { Config.save(); toast("Settings saved"); });
        cbutton(g, x + 10 + bw, y, bw, 18, "EXPORT", Colors.CARD_HI, Colors.TEXT, () -> toast(Config.exportSettings()));
        cbutton(g, x + 16 + 2 * bw, y, bw, 18, "IMPORT", Colors.CARD_HI, Colors.TEXT, () -> toast(Config.importSettings()));
        boolean armed = System.currentTimeMillis() - confirmAt < 4000;
        cbutton(g, x + 22 + 3 * bw, y, bw, 18, armed ? "CONFIRM RESET" : "RESET ALL", armed ? Colors.RED : 0xFF4A3A2A, 0xFFFFFFFF, () -> {
            if (armed) { Config.resetEverything(); toast("All settings reset (waypoints kept)"); confirmAt = 0; }
            else { confirmAt = System.currentTimeMillis(); toast("Click RESET ALL again within 4s to confirm"); }
        });
        return y + 30;
    }

    // =================================================================== general

    private int drawGeneral(GuiGraphicsExtractor g, int y) {
        int x = vx1, w = cardW();
        y = presetBlock(g, y, x, w);
        y = fileBlock(g, y, x, w);
        sectionTitle(g, "ADVANCED CUSTOMIZATION", x, y, w);
        y += 18;
        y += card(g, Features.get("global"), x, y, w);
        y += card(g, Features.get("tracking"), x, y, w);
        y = waypointBlock(g, y, x, w);
        return y + 10;
    }

    private int waypointBlock(GuiGraphicsExtractor g, int y, int x, int w) {
        sectionTitle(g, "WAYPOINTS", x, y, w);
        y += 18;
        if (visible(y, 18)) {
            Gfx.rect(g, x + 4, y, 120, 16, 0xFF0C0E0A);
            Gfx.frame(g, x + 4, y, 120, 16, "wpname".equals(focus) ? Colors.OLIVE_HI : 0xFF3A4030);
            String t = wpName.isEmpty() && !"wpname".equals(focus) ? "NAME (optional)" : wpName + ("wpname".equals(focus) && ((long) (time * 2)) % 2 == 0 ? "_" : "");
            Gfx.vanilla(g, t, x + 8, y + 4, wpName.isEmpty() ? Colors.DIM : Colors.TEXT, 1f);
            region(x + 4, y, 120, 16, v -> focus = "wpname", null, vy1, vy2);
            cbutton(g, x + 130, y, 130, 16, "ADD AT MY POSITION", Colors.OLIVE, 0xFFFFFFFF, this::addWaypoint);
        }
        y += 22;
        List<Waypoint> list = Config.waypoints();
        if (list.isEmpty() && visible(y, 10)) Gfx.vanilla(g, "No waypoints yet. Add one while in a world.", x + 8, y, Colors.DIM, 1f);
        if (list.isEmpty()) y += 14;
        for (int i = 0; i < list.size(); i++) {
            Waypoint wp = list.get(i);
            if (visible(y, 18)) {
                Gfx.rect(g, x + 4, y, w - 8, 18, Colors.CARD);
                Gfx.vanilla(g, wp.name, x + 10, y + 5, Colors.TEXT, 1f);
                Gfx.vanilla(g, String.format("%d %d %d", (int) wp.x, (int) wp.y, (int) wp.z), x + 100, y + 5, Colors.DIM, 1f);
                Gfx.vanilla(g, Dim.pretty(wp.dim), x + 200, y + 5, Colors.DIM, 1f);
                cbutton(g, x + w - 180, y + 2, 100, 14, wp.typeName().toUpperCase(), Colors.CARD_HI, Colors.TEXT,
                    () -> { wp.type = (wp.type + 1) % Waypoint.TYPES.length; Config.touch(); });
                cbutton(g, x + w - 70, y + 2, 56, 14, "DELETE", 0xFF4A3A2A, 0xFFFFFFFF, () -> { Config.waypoints().remove(wp); Config.touch(); });
            }
            y += 20;
        }
        if (visible(y, 10)) Gfx.vanilla(g, "Types: Runway A + Runway B light a runway; Hazard shows world beacons; Restricted/Base/LZ drive zone signage.", x + 4, y, Colors.DIM, 1f);
        return y + 14;
    }

    private void addWaypoint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { toast("Join a world to add a waypoint"); return; }
        String n = wpName.isBlank() ? "WP-" + (Config.waypoints().size() + 1) : wpName.toUpperCase();
        Config.waypoints().add(new Waypoint(n, mc.player.getX(), mc.player.getY(), mc.player.getZ(), Dim.current(mc.level), Waypoint.POINT));
        wpName = "";
        Config.touch();
        toast("Waypoint '" + n + "' added");
    }

    // =================================================================== keybinds

    private static String name(KeyMapping k) { return Component.translatable(k.getName()).getString(); }

    private void rebind(KeyMapping km, int code) {
        km.setKey(code < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYBOARD.getOrCreate(code));
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        MilitaryClient.mirrorKeys();
    }

    private String conflict(KeyMapping km) {
        if (km.isUnbound()) return null;
        for (KeyMapping o : Minecraft.getInstance().options.keyMappings) {
            if (o != km && o.saveString().equals(km.saveString())) return name(o);
        }
        return null;
    }

    private int drawKeybinds(GuiGraphicsExtractor g, int y) {
        int x = vx1, w = cardW();
        sectionTitle(g, "KEYBIND SETTINGS  (category: MILITARY CLIENT)", x, y, w);
        y += 18;
        for (String l : wrap("These are normal Minecraft key mappings. They also appear in Options > Controls > Key Binds, where Minecraft shows its own conflict warnings. Backspace/Delete while rebinding unbinds.", w - 12)) {
            if (visible(y, 10)) Gfx.vanilla(g, l, x + 6, y, Colors.DIM, 1f);
            y += 10;
        }
        y += 6;
        for (KeyMapping km : MilitaryClient.ALL_KEYS) {
            if (visible(y, 28)) {
                Gfx.rect(g, x + 4, y, w - 8, 26, Colors.CARD);
                Gfx.vanilla(g, name(km), x + 12, y + 4, Colors.TEXT, 1f);
                String c = conflict(km);
                String keyText = capturing == km ? "PRESS ANY KEY..." : km.getTranslatedKeyMessage().getString();
                Gfx.vanilla(g, keyText, x + 12, y + 15, capturing == km ? Colors.AMBER : Colors.OLIVE_HI, 1f);
                if (c != null) Gfx.vanilla(g, "! CONFLICT with: " + c, x + 150, y + 15, Colors.AMBER, 1f);
                cbutton(g, x + w - 168, y + 6, 70, 14, capturing == km ? "CANCEL" : "CHANGE", Colors.OLIVE, 0xFFFFFFFF, () -> capturing = capturing == km ? null : km);
                cbutton(g, x + w - 94, y + 6, 80, 14, "RESET DEFAULT", Colors.CARD_HI, Colors.TEXT, () -> {
                    km.setKey(km.getDefaultKey()); KeyMapping.resetMapping(); Minecraft.getInstance().options.save();
                    MilitaryClient.mirrorKeys(); toast(name(km) + " reset");
                });
            }
            y += 30;
        }
        if (visible(y, 16)) cbutton(g, x + 4, y, 150, 16, "RESET ALL MILITARY KEYS", 0xFF4A3A2A, 0xFFFFFFFF, () -> {
            for (KeyMapping km : MilitaryClient.ALL_KEYS) km.setKey(km.getDefaultKey());
            KeyMapping.resetMapping(); Minecraft.getInstance().options.save(); MilitaryClient.mirrorKeys(); toast("Military keybinds reset");
        });
        return y + 24;
    }
}
