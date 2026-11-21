package com.militaryclient;

import com.militaryclient.audio.AudioEngine;
import com.militaryclient.config.Config;
import com.militaryclient.config.Features;
import com.militaryclient.config.Waypoint;
import com.militaryclient.fx.Atmosphere;
import com.militaryclient.hud.Controllers;
import com.militaryclient.hud.HudLayer;
import com.militaryclient.hud.Scenes;
import com.militaryclient.hud.Tracker;
import com.militaryclient.ui.ControlPanelScreen;
import com.militaryclient.util.Compat;
import com.militaryclient.util.Dim;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MilitaryClient implements ClientModInitializer {
    public static final String ID = "militaryclient";
    private static final Logger LOG = LogUtils.getLogger();

    public static KeyMapping.Category CATEGORY;
    public static KeyMapping OPEN, BINOCULARS, CAMERA, ADD_WAYPOINT, RADIO_NEXT, RADIO_PREV;
    public static List<KeyMapping> ALL_KEYS = List.of();
    private static final Set<String> FAILED = new HashSet<>();

    @Override
    public void onInitializeClient() {
        Config.init(FabricLoader.getInstance().getConfigDir());
        HudLayer.init();
        PackSync.init();

        // ---- key mappings (normal Minecraft system: they show up in Options > Controls with conflict warnings)
        CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
        OPEN = key("open_panel", InputConstants.KEY_RSHIFT);       // default Right Shift
        BINOCULARS = key("binoculars", 302);   // F13: valid key code that most keyboards lack, so effectively unbound
        CAMERA = key("aircraft_camera", 303);      // F14
        ADD_WAYPOINT = key("add_waypoint", 304);   // F15
        RADIO_NEXT = key("radio_next", 305);       // F16
        RADIO_PREV = key("radio_prev", 306);       // F17
        ALL_KEYS = List.of(OPEN, BINOCULARS, CAMERA, ADD_WAYPOINT, RADIO_NEXT, RADIO_PREV);

        // ---- HUD
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(ID, "hud"), HudLayer::render);

        // ---- ticking
        ClientTickEvents.END_CLIENT_TICK.register(MilitaryClient::tick);

        // ---- screens: loading scenes, inventory styling, panel hotkey on title/pause screens
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            try {
                if (Scenes.isLoading(screen)) {
                    ScreenEvents.afterExtract(screen).register((s, gr, mx, my, dt) -> guard("loading_screens", () -> Scenes.loading(s, gr)));
                }
                if (screen instanceof AbstractContainerScreen<?>) {
                    ScreenEvents.afterExtract(screen).register((s, gr, mx, my, dt) -> guard("military_inventory", () -> Scenes.inventory(s, gr)));
                }
                String n = screen.getClass().getSimpleName();
                if (n.equals("TitleScreen") || n.equals("PauseScreen")) {
                    ScreenKeyboardEvents.beforeKeyPress(screen).register((s, ev) -> {
                        if (OPEN.matches(ev)) Compat.setScreen(new ControlPanelScreen(s));
                    });
                }
            } catch (Throwable t) {
                LOG.warn("[MilitaryClient] Could not attach screen hooks to {}: {}", screen.getClass().getSimpleName(), t.toString());
            }
        });

        // ---- lifecycle: always give FOV/brightness back
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Controllers.restore(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Controllers.restore(client);
            mirrorKeys();
            Config.save();
        });

        LOG.info("[MilitaryClient] Loaded {} features (client-side only)", Features.ALL.size() - 2);
    }

    private static KeyMapping key(String name, int code) {
        return KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key." + ID + "." + name, InputConstants.Type.KEYBOARD, code, CATEGORY));
    }

    /** Runs a subsystem, logging the first failure only so a broken feature can never spam or crash the client. */
    public static void guard(String name, Runnable r) {
        if (FAILED.contains(name)) return;
        try {
            r.run();
        } catch (Throwable t) {
            FAILED.add(name);
            LOG.error("[MilitaryClient] Subsystem '{}' failed and was stopped", name, t);
            Features.ALL.stream().filter(f -> f.key.equals(name)).findFirst()
                .ifPresent(f -> f.status = "Stopped after an error: " + t.getClass().getSimpleName());
        }
    }

    public static void mirrorKeys() {
        try {
            var m = Config.keybindMirror();
            m.put("open_panel", OPEN.saveString());
            m.put("binoculars", BINOCULARS.saveString());
            m.put("aircraft_camera", CAMERA.saveString());
            m.put("add_waypoint", ADD_WAYPOINT.saveString());
            m.put("radio_next", RADIO_NEXT.saveString());
            m.put("radio_prev", RADIO_PREV.saveString());
        } catch (Throwable ignored) { }
    }

    private static void tick(Minecraft mc) {
        Config.tick(System.currentTimeMillis());
        boolean inWorld = mc.player != null && mc.level != null;
        boolean noScreen = Compat.currentScreen() == null;

        if (noScreen) while (OPEN.consumeClick()) Compat.setScreen(new ControlPanelScreen(null));
        if (inWorld && noScreen) {
            while (ADD_WAYPOINT.consumeClick()) {
                String n = "WP-" + (Config.waypoints().size() + 1);
                Config.waypoints().add(new Waypoint(n, mc.player.getX(), mc.player.getY(), mc.player.getZ(), Dim.current(mc.level), Waypoint.POINT));
                Config.touch();
                mc.player.sendSystemMessage(Component.literal("[Military Client] Waypoint " + n + " added"));
            }
            while (RADIO_NEXT.consumeClick()) Config.set("military_radio_gui", "channel", Math.floorMod(Config.c("military_radio_gui", "channel") + 1, 8));
            while (RADIO_PREV.consumeClick()) Config.set("military_radio_gui", "channel", Math.floorMod(Config.c("military_radio_gui", "channel") - 1, 8));
        }

        if (inWorld) {
            guard("tracker", () -> Tracker.tick(mc));
            guard("controllers", () -> Controllers.tick(mc));
            guard("atmosphere", () -> Atmosphere.tick(mc));
            guard("audio", () -> AudioEngine.tick(mc));
        } else {
            Tracker.clear();
        }
        PackSync.tick(mc);
    }
}
