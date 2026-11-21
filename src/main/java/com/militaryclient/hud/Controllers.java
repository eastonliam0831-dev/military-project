package com.militaryclient.hud;

import com.militaryclient.MilitaryClient;
import com.militaryclient.config.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;

/** Per-tick state that touches Minecraft options (FOV, gamma) or samples the world. Always restores options. */
public final class Controllers {
    private static final Logger LOG = LogUtils.getLogger();

    public static boolean binocular, camera;
    public static double rangeM = -1;

    private static Integer savedFov;
    private static Double savedGamma;
    private static boolean toggleBin, toggleCam;

    // map
    public static int[] map = new int[0];
    public static int mapN, mapBpc, mapCx, mapCz;
    private static long lastMap;

    private Controllers() {}

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { restore(mc); return; }
        zoom(mc, p);
        nightVision(mc);
        if (Config.on("map_overlay")) updateMap(mc);
    }

    // ------------------------------------------------------------------ zoom

    private static void zoom(Minecraft mc, LocalPlayer p) {
        boolean inGame = Compat_screenIsNull();
        boolean binOn = Config.on("binocular_overlay"), camOn = Config.on("aircraft_camera");
        if (!inGame) {
            // keep current state but don't process key presses while a menu is open
        } else {
            if (binOn) {
                if (Config.c("binocular_overlay", "mode") == 0) binocular = MilitaryClient.BINOCULARS.isDown();
                else { while (MilitaryClient.BINOCULARS.consumeClick()) toggleBin = !toggleBin; binocular = toggleBin; }
            } else { binocular = false; toggleBin = false; }
            if (camOn) {
                while (MilitaryClient.CAMERA.consumeClick()) toggleCam = !toggleCam;
                camera = toggleCam || MilitaryClient.CAMERA.isDown();
            } else { camera = false; toggleCam = false; }
        }
        double zoomF = 1;
        if (binocular) zoomF = Config.n("binocular_overlay", "zoom");
        else if (camera) zoomF = Config.n("aircraft_camera", "zoom");

        try {
            var fovOpt = mc.options.fov();
            if (zoomF > 1.001) {
                if (savedFov == null) savedFov = fovOpt.get();
                int target = (int) Math.max(30, Math.min(110, Math.round(savedFov / zoomF)));
                int cur = fovOpt.get();
                int step = Math.max(1, Math.abs(target - cur) / 3);
                int next = cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step);
                if (next != cur) fovOpt.set(next);
            } else if (savedFov != null) {
                int cur = fovOpt.get();
                int step = Math.max(1, Math.abs(savedFov - cur) / 3);
                int next = cur < savedFov ? Math.min(savedFov, cur + step) : Math.max(savedFov, cur - step);
                fovOpt.set(next);
                if (next == savedFov) savedFov = null;
            }
        } catch (Throwable t) {
            LOG.warn("[MilitaryClient] FOV zoom unavailable: {}", t.toString());
            setStatus("binocular_overlay", "Zoom unavailable: " + t.getClass().getSimpleName());
            setStatus("aircraft_camera", "Zoom unavailable: " + t.getClass().getSimpleName());
        }

        // range readout
        rangeM = -1;
        if (binocular && Config.b("binocular_overlay", "range") && (p.tickCount % 3 == 0)) {
            try {
                HitResult hit = p.pick(256.0, 1.0f, false);
                if (hit.getType() != HitResult.Type.MISS) rangeM = hit.getLocation().distanceTo(p.getEyePosition());
            } catch (Throwable ignored) { }
            lastRange = rangeM;
        } else if (binocular) {
            rangeM = lastRange;
        }
    }

    private static double lastRange = -1;

    private static boolean Compat_screenIsNull() {
        return com.militaryclient.util.Compat.currentScreen() == null;
    }

    private static void setStatus(String key, String msg) {
        var f = com.militaryclient.config.Features.get(key);
        if (f != null) f.status = msg;
    }

    // ------------------------------------------------------------------ night vision (gamma)

    private static void nightVision(Minecraft mc) {
        try {
            var gamma = mc.options.gamma();
            if (Config.on("night_vision")) {
                if (savedGamma == null) savedGamma = gamma.get();
                double want = Math.max(savedGamma, Config.n("night_vision", "brightness"));
                if (Math.abs(gamma.get() - want) > 0.001) gamma.set(want);
            } else if (savedGamma != null) {
                gamma.set(savedGamma);
                savedGamma = null;
            }
        } catch (Throwable t) {
            setStatus("night_vision", "Brightness boost unavailable (tint still works): " + t.getClass().getSimpleName());
        }
    }

    /** Restores FOV and brightness. Called on disconnect and on game shutdown. */
    public static void restore(Minecraft mc) {
        try {
            if (savedFov != null) { mc.options.fov().set(savedFov); savedFov = null; }
            if (savedGamma != null) { mc.options.gamma().set(savedGamma); savedGamma = null; }
        } catch (Throwable ignored) { }
        binocular = camera = toggleBin = toggleCam = false;
    }

    // ------------------------------------------------------------------ map

    private static void updateMap(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        long now = level.getGameTime();
        int period = Config.i("map_overlay", "update_ticks");
        if (Config.b("global", "low_perf")) period *= 2;
        int n = Config.i("map_overlay", "cells"), bpc = Config.i("map_overlay", "blocks_per_cell");
        if (now - lastMap < period && map.length == n * n && mapBpc == bpc) return;
        lastMap = now;
        if (map.length != n * n) map = new int[n * n];
        mapN = n; mapBpc = bpc;
        int cx = p.getBlockX(), cz = p.getBlockZ();
        mapCx = cx; mapCz = cz;
        int half = n / 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] heights = new int[n * (n + 1)];
        for (int zi = -1; zi < n; zi++) {
            for (int xi = 0; xi < n; xi++) {
                int wx = cx + (xi - half) * bpc, wz = cz + (zi - half) * bpc;
                int h = Integer.MIN_VALUE;
                if (level.hasChunk(wx >> 4, wz >> 4)) h = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                heights[(zi + 1) * n + xi] = h;
                if (zi < 0) continue;
                if (h == Integer.MIN_VALUE) { map[zi * n + xi] = 0; continue; }
                pos.set(wx, Math.max(-64, h - 1), wz);
                BlockState st = level.getBlockState(pos);
                int col = st.getMapColor(level, pos).col;
                int hn = heights[zi * n + xi];
                float f = 1f;
                if (hn != Integer.MIN_VALUE) f += Math.max(-3, Math.min(3, h - hn)) * 0.07f;
                int r = Math.min(255, (int) (((col >> 16) & 255) * f)), g = Math.min(255, (int) (((col >> 8) & 255) * f)),
                    b = Math.min(255, (int) ((col & 255) * f));
                map[zi * n + xi] = col == 0 ? 0 : 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
    }
}
