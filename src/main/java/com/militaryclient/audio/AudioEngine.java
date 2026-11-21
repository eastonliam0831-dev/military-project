package com.militaryclient.audio;

import com.militaryclient.config.Config;
import com.militaryclient.config.Features;
import com.militaryclient.hud.Tracker;
import com.militaryclient.util.Compat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Ambient scheduler. Audio is generated on a worker thread and played through javax.sound clips. */
public final class AudioEngine {
    private static final Logger LOG = LogUtils.getLogger();
    private static final AudioFormat FORMAT = new AudioFormat(Synth.RATE, 16, 1, true, false);
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MilitaryClient-Audio");
        t.setDaemon(true);
        return t;
    });
    private static final Random RNG = new Random();
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final AtomicInteger AMBIENT_ACTIVE = new AtomicInteger();
    private static final int MAX_CLIPS = 5;

    private static final String[] KEYS = { "distant_aircraft", "helicopter_ambience", "jet_engine_sounds",
        "distant_explosions", "radio_chatter", "battlefield_wind" };
    private static final long[] next = new long[KEYS.length];
    private static long engineNext;
    private static boolean failed;
    private static long tickCount;

    private AudioEngine() {}

    private static void fail(String feature, Throwable t) {
        if (!failed) LOG.warn("[MilitaryClient] Audio unavailable: {}", t.toString());
        failed = true;
        for (String k : KEYS) mark(k, "Audio unavailable on this system: " + t.getClass().getSimpleName());
        mark("aircraft_engine_audio", "Audio unavailable on this system");
        mark("improved_rain", null);
    }

    private static void mark(String key, String msg) {
        if (Features.get(key) != null) Features.get(key).status = msg;
    }

    private static double masterGain() {
        double g = Config.n("global", "volume");
        return g * Compat.masterVolume();
    }

    /** Plays PCM at linear gain (0..1) with optional pan (-1..1). Returns false if not played. */
    public static void play(float[] samples, double gain, double pan, boolean ambient) {
        if (failed || gain < 0.004 || ACTIVE.get() >= MAX_CLIPS) return;
        if (ambient && AMBIENT_ACTIVE.get() >= 1) return; // never stack ambient beds
        final byte[] pcm = Synth.toPcm16(samples, 1f);
        ACTIVE.incrementAndGet();
        if (ambient) AMBIENT_ACTIVE.incrementAndGet();
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(FORMAT, pcm, 0, pcm.length);
            try {
                FloatControl vol = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float db = (float) (20 * Math.log10(Math.max(0.001, Math.min(1.0, gain))));
                vol.setValue(Math.max(vol.getMinimum(), Math.min(vol.getMaximum(), db)));
            } catch (Throwable ignored) { }
            try {
                FloatControl p = (FloatControl) clip.getControl(FloatControl.Type.PAN);
                p.setValue((float) Math.max(-1, Math.min(1, pan)));
            } catch (Throwable ignored) { }
            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {
                    clip.close();
                    ACTIVE.decrementAndGet();
                    if (ambient) AMBIENT_ACTIVE.decrementAndGet();
                }
            });
            clip.start();
        } catch (Throwable t) {
            ACTIVE.decrementAndGet();
            if (ambient) AMBIENT_ACTIVE.decrementAndGet();
            fail("audio", t);
        }
    }

    private static void async(Runnable r) {
        try { POOL.execute(r); } catch (Throwable ignored) { }
    }

    private static void schedule(int i, long now) {
        String k = KEYS[i];
        if (!Features.get(k).settings.isEmpty() && Config.on(k) && (k.equals("battlefield_wind"))) {
            next[i] = now + Config.i(k, "interval") * 20L;
            return;
        }
        int lo = Config.i(k, "min_interval"), hi = Math.max(lo, Config.i(k, "max_interval"));
        next[i] = now + (lo + RNG.nextInt(hi - lo + 1)) * 20L;
    }

    public static void tick(Minecraft mc) {
        tickCount++;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused() || failed) return;
        long now = tickCount;
        for (int i = 0; i < KEYS.length; i++) {
            String k = KEYS[i];
            if (!Config.on(k)) { next[i] = 0; continue; }
            if (next[i] == 0) { schedule(i, now); continue; } // first enable: wait one interval, never blast immediately
            if (now < next[i]) continue;
            schedule(i, now);
            if (k.equals("distant_explosions") && Config.b(k, "flash")) com.militaryclient.fx.Atmosphere.flash(0xFFFFA040, 0.22f);
            if (k.equals("battlefield_wind") && Config.b(k, "outdoors") && !mc.level.canSeeSky(p.blockPosition())) continue;
            final double vol = Config.n(k, "volume") * masterGain();
            final double pan = RNG.nextDouble() * 1.6 - 0.8;
            final long seed = RNG.nextLong();
            final int ii = i;
            async(() -> {
                try {
                    float[] s = make(ii, seed);
                    if (s != null) play(s, vol, pan, true);
                } catch (Throwable t) { fail(k, t); }
            });
        }
        if (Config.on("aircraft_engine_audio") && now >= engineNext) {
            engineNext = now + 45;
            engineTick(p);
        }
    }

    private static float[] make(int i, long seed) {
        switch (KEYS[i]) {
            case "distant_aircraft": {
                int t = Config.c("distant_aircraft", "type");
                return Synth.aircraft(t == 0 ? (int) (Math.abs(seed) % 3) : t - 1, seed);
            }
            case "helicopter_ambience": {
                int t = Config.c("helicopter_ambience", "type");
                return Synth.helicopter(t == 0 ? (int) (Math.abs(seed) % 2) : t - 1, seed);
            }
            case "jet_engine_sounds":
                // Doppler fly-by, or (when off) a steady high-altitude airliner rumble
                return Config.b("jet_engine_sounds", "flyby") ? Synth.jet((int) (Math.abs(seed) % 3), seed) : Synth.aircraft(2, seed);
            case "distant_explosions":
                return Synth.explosion(seed, 4 + Math.abs(seed % 3));
            case "radio_chatter": {
                int style = Config.c("radio_chatter", "style");
                if (Config.on("military_radio_gui")) style = Math.floorMod(Config.c("military_radio_gui", "channel"), 3);
                return Synth.radio(style, seed);
            }
            default:
                return Synth.wind(Config.n("battlefield_wind", "gust"), seed);
        }
    }

    private static float[] engineBase;

    private static void engineTick(LocalPlayer p) {
        Tracker.Obj near = null;
        double range = Config.n("aircraft_engine_audio", "range");
        for (Tracker.Obj o : Tracker.AIR) if (o.dist < range && (near == null || o.dist < near.dist)) near = o;
        if (near == null) return;
        final double dist = near.dist;
        final double closing = near.closing;
        final double pan = Math.max(-1, Math.min(1, near.panHint));
        final double vol = Config.n("aircraft_engine_audio", "volume") * masterGain() * Math.pow(1 - dist / range, 1.6);
        final boolean dop = Config.b("aircraft_engine_audio", "doppler");
        async(() -> {
            try {
                if (engineBase == null) engineBase = Synth.engine(1, 77);
                double pitch = dop ? 1.0 + Math.max(-0.2, Math.min(0.2, closing / 343.0 * 4)) : 1.0;
                play(Synth.resample(engineBase, pitch), vol, pan, false);
            } catch (Throwable t) { fail("aircraft_engine_audio", t); }
        });
    }

    /** Thunder / one-shot used by weather effects. */
    public static void thunder(double volume) {
        if (failed) return;
        final long seed = RNG.nextLong();
        final double v = volume * masterGain();
        async(() -> {
            try { play(Synth.explosion(seed, 6), v, RNG.nextDouble() - 0.5, false); }
            catch (Throwable t) { fail("improved_rain", t); }
        });
    }

    public static void explosionFlashSound(double volume) { thunder(volume); }
}
