package com.militaryclient.audio;

import java.util.Random;

/** Procedural sound synthesis. 22.05 kHz mono, float samples in [-1, 1]. No Minecraft types. */
public final class Synth {
    public static final int RATE = 22050;
    private Synth() {}

    private static float[] buf(double seconds) { return new float[(int) (seconds * RATE)]; }

    private static void normalize(float[] b, float peak) {
        float m = 0;
        for (float v : b) m = Math.max(m, Math.abs(v));
        if (m < 1e-6f) return;
        float k = peak / m;
        for (int i = 0; i < b.length; i++) b[i] *= k;
    }

    private static void fade(float[] b, double inSec, double outSec) {
        int a = (int) (inSec * RATE), z = (int) (outSec * RATE);
        for (int i = 0; i < b.length; i++) {
            float g = 1f;
            if (i < a) g = Math.min(g, (float) i / a);
            if (b.length - i < z) g = Math.min(g, (float) (b.length - i) / z);
            b[i] *= g;
        }
    }

    /** One-pole low-pass with a per-sample cutoff in Hz. */
    private static final class LP {
        float y;
        float step(float x, double cutoff) {
            float a = (float) (1 - Math.exp(-2 * Math.PI * cutoff / RATE));
            y += a * (x - y);
            return y;
        }
    }

    private static final class BP {
        final LP lo = new LP(), hi = new LP();
        float step(float x, double loCut, double hiCut) { return lo.step(x, hiCut) - hi.step(x, loCut); }
    }

    /** Jet fly-by. variant 0..2 changes pitch and length. */
    public static float[] jet(int variant, long seed) {
        Random r = new Random(seed);
        double dur = 9 + variant * 2;
        float[] b = buf(dur);
        LP lp = new LP();
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            double t = (double) i / b.length;
            double env = Math.pow(Math.sin(Math.PI * t), 1.6);
            double cutoff = 250 + 1700 * env;
            float n = lp.step((float) (r.nextGaussian() * 0.6), cutoff);
            double doppler = 1.25 - 0.5 * t;
            ph += 2 * Math.PI * (900 + variant * 180) * doppler / RATE;
            b[i] = (float) (n * env + 0.05 * Math.sin(ph) * env);
        }
        fade(b, 0.5, 1.5);
        normalize(b, 0.9f);
        return b;
    }

    /** Propeller aircraft drone. kind 0 piston, 1 turboprop, 2 airliner (broadband). */
    public static float[] aircraft(int kind, long seed) {
        Random r = new Random(seed);
        float[] b = buf(10 + r.nextInt(5));
        double base = kind == 0 ? 85 : kind == 1 ? 130 : 60;
        LP lp = new LP();
        for (int i = 0; i < b.length; i++) {
            double t = (double) i / b.length;
            double env = Math.sin(Math.PI * t);
            double s = 0;
            for (int h = 1; h <= 5; h++) s += Math.sin(2 * Math.PI * base * h * i / RATE + h) / h;
            double warble = 0.75 + 0.25 * Math.sin(2 * Math.PI * (kind == 0 ? 7 : 3) * i / RATE);
            float n = lp.step((float) r.nextGaussian(), kind == 2 ? 900 : 300);
            b[i] = (float) ((s * 0.35 * warble + n * (kind == 2 ? 0.8 : 0.25)) * env);
        }
        fade(b, 0.8, 2.0);
        normalize(b, 0.85f);
        return b;
    }

    /** Helicopter. kind 0 light, 1 heavy. */
    public static float[] helicopter(int kind, long seed) {
        Random r = new Random(seed);
        float[] b = buf(12 + r.nextInt(4));
        double blade = kind == 0 ? 12.5 : 8.0;
        LP lp = new LP();
        for (int i = 0; i < b.length; i++) {
            double t = (double) i / b.length;
            double env = Math.pow(Math.sin(Math.PI * t), 1.3);
            double ph = (blade * i / RATE) % 1.0;
            double pulse = Math.pow(Math.max(0, Math.sin(Math.PI * ph)), 6);
            float n = lp.step((float) r.nextGaussian(), 450 + 350 * env);
            double low = Math.sin(2 * Math.PI * (kind == 0 ? 70 : 48) * i / RATE) * 0.3;
            b[i] = (float) ((n * (0.25 + 0.9 * pulse) + low * pulse) * env);
        }
        fade(b, 1.0, 2.5);
        normalize(b, 0.9f);
        return b;
    }

    /** Distant explosion with rolling rumble. */
    public static float[] explosion(long seed, double dur) {
        Random r = new Random(seed);
        float[] b = buf(dur);
        LP lp = new LP(), lp2 = new LP();
        double ph = 0;
        for (int i = 0; i < b.length; i++) {
            double t = (double) i / RATE;
            double env = Math.exp(-t * 1.1) * (1 - Math.exp(-t * 40));
            double roll = 0.6 + 0.4 * Math.sin(2 * Math.PI * 3.1 * t + Math.sin(t * 1.7) * 2);
            float n = lp.step((float) r.nextGaussian(), 90 + 500 * Math.exp(-t * 2.5));
            n = lp2.step(n, 400);
            ph += 2 * Math.PI * (55 * Math.exp(-t * 0.35) + 18) / RATE;
            b[i] = (float) ((n * 1.4 + Math.sin(ph) * 0.5) * env * roll);
        }
        fade(b, 0.005, 0.8);
        normalize(b, 0.95f);
        return b;
    }

    /** Wind gust. strength 0..1. */
    public static float[] wind(double strength, long seed) {
        Random r = new Random(seed);
        float[] b = buf(8 + r.nextInt(5));
        BP bp = new BP();
        double f = 300 + 500 * strength;
        for (int i = 0; i < b.length; i++) {
            double t = (double) i / b.length;
            double env = Math.pow(Math.sin(Math.PI * t), 1.5);
            double gust = 0.55 + 0.45 * Math.sin(2 * Math.PI * (0.35 + strength * 0.5) * i / RATE + t * 5);
            double c = f * (0.7 + 0.5 * gust);
            b[i] = (float) (bp.step((float) r.nextGaussian(), 80, c) * env * gust);
        }
        fade(b, 1.5, 2.5);
        normalize(b, 0.8f);
        return b;
    }

    /** Radio traffic. style 0 squelch babble, 1 morse, 2 beep net. */
    public static float[] radio(int style, long seed) {
        Random r = new Random(seed);
        float[] b = buf(4 + r.nextInt(5));
        BP bp = new BP();
        LP stat = new LP();
        double t = 0.0;
        // squelch open
        burst(b, 0, 0.12, r, bp);
        int pos = (int) (0.2 * RATE);
        double tone = 700 + r.nextInt(500);
        while (pos < b.length - RATE / 2) {
            if (style == 0) { // syllable-like noise bursts shaped like speech cadence
                int len = (int) ((0.06 + r.nextDouble() * 0.18) * RATE);
                double f0 = 350 + r.nextInt(500);
                for (int i = 0; i < len && pos + i < b.length; i++) {
                    double e = Math.sin(Math.PI * i / len);
                    float n = bp.step((float) r.nextGaussian(), 250, 1800 + f0);
                    b[pos + i] += (float) (n * e * 1.3 + Math.sin(2 * Math.PI * f0 * (pos + i) / RATE) * 0.12 * e);
                }
                pos += len + (int) ((r.nextDouble() < 0.2 ? 0.35 : 0.04 + r.nextDouble() * 0.08) * RATE);
            } else if (style == 1) { // morse
                int unit = (int) (0.07 * RATE);
                int len = unit * (r.nextBoolean() ? 1 : 3);
                for (int i = 0; i < len && pos + i < b.length; i++) b[pos + i] += (float) (Math.sin(2 * Math.PI * tone * i / RATE) * 0.5);
                pos += len + unit * (r.nextDouble() < 0.15 ? 5 : 1);
            } else { // beep net
                int len = (int) ((0.05 + r.nextDouble() * 0.1) * RATE);
                double f = tone * (r.nextBoolean() ? 1 : 1.5);
                for (int i = 0; i < len && pos + i < b.length; i++) b[pos + i] += (float) (Math.sin(2 * Math.PI * f * i / RATE) * 0.45 * Math.min(1, (len - i) / 200.0));
                pos += len + (int) ((0.15 + r.nextDouble() * 0.7) * RATE);
            }
        }
        // roger beep + squelch tail
        int tail = b.length - (int) (0.3 * RATE);
        for (int i = 0; i < (int) (0.09 * RATE); i++) b[tail + i] += (float) (Math.sin(2 * Math.PI * 1100 * i / RATE) * 0.5);
        burst(b, b.length - (int) (0.12 * RATE), 0.12, r, bp);
        for (int i = 0; i < b.length; i++) b[i] += stat.step((float) r.nextGaussian(), 2500) * 0.08f;
        fade(b, 0.01, 0.05);
        normalize(b, 0.7f);
        return b;
    }

    private static void burst(float[] b, int start, double sec, Random r, BP bp) {
        int len = (int) (sec * RATE);
        for (int i = 0; i < len && start + i < b.length; i++)
            b[start + i] += (float) (bp.step((float) r.nextGaussian(), 400, 3500) * Math.exp(-i * 8.0 / len) * 1.4);
    }

    /** Short engine loop used by the aircraft-following engine audio. */
    public static float[] engine(int kind, long seed) {
        Random r = new Random(seed);
        float[] b = buf(2.4);
        LP lp = new LP();
        double base = kind == 0 ? 95 : kind == 1 ? 140 : 70;
        for (int i = 0; i < b.length; i++) {
            double s = 0;
            for (int h = 1; h <= 4; h++) s += Math.sin(2 * Math.PI * base * h * i / RATE) / h;
            float n = lp.step((float) r.nextGaussian(), kind == 2 ? 1100 : 380);
            b[i] = (float) (s * 0.35 * (0.8 + 0.2 * Math.sin(2 * Math.PI * 5 * i / RATE)) + n * (kind == 2 ? 0.9 : 0.3));
        }
        fade(b, 0.2, 0.4);
        normalize(b, 0.85f);
        return b;
    }

    /** Linear-interpolating resample; factor > 1 raises pitch. */
    public static float[] resample(float[] in, double factor) {
        if (Math.abs(factor - 1.0) < 0.005) return in;
        int n = (int) (in.length / factor);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double p = i * factor;
            int a = (int) p;
            float f = (float) (p - a);
            float x0 = in[Math.min(a, in.length - 1)], x1 = in[Math.min(a + 1, in.length - 1)];
            out[i] = x0 + (x1 - x0) * f;
        }
        return out;
    }

    public static byte[] toPcm16(float[] s, float gain) {
        byte[] out = new byte[s.length * 2];
        for (int i = 0; i < s.length; i++) {
            int v = Math.round(Math.max(-1f, Math.min(1f, s[i] * gain)) * 32767);
            out[2 * i] = (byte) v;
            out[2 * i + 1] = (byte) (v >> 8);
        }
        return out;
    }
}
