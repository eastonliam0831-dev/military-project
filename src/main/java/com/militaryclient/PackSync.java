package com.militaryclient;

import com.militaryclient.config.Config;
import com.militaryclient.config.Features;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the mod's built-in resource packs and keeps them selected/deselected to match the feature toggles
 * (40 Vehicle Skins, 41 Base Decoration). Toggling triggers one normal resource reload.
 */
public final class PackSync {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String[] SKINS = { "vehicle_olive", "vehicle_desert", "vehicle_woodland", "vehicle_urban" };
    private static final String DECOR = "base_decor";
    private static boolean registered, dirty = true, gaveUp;
    private static long lastChange;
    private static int attempts;

    private PackSync() {}

    public static void init() {
        try {
            ModContainer mod = FabricLoader.getInstance().getModContainer(MilitaryClient.ID).orElseThrow();
            for (String n : concat()) {
                ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(MilitaryClient.ID, n), mod,
                    Component.literal("Military Client: " + n.replace('_', ' ')), PackActivationType.NORMAL);
            }
            registered = true;
        } catch (Throwable t) {
            LOG.error("[MilitaryClient] Could not register built-in resource packs", t);
            fault("Resource packs could not be registered: " + t.getClass().getSimpleName());
        }
        Config.LISTENERS.add(k -> {
            if (k.equals("*") || k.startsWith("vehicle_skins") || k.startsWith("base_decor_pack")) {
                dirty = true; gaveUp = false; attempts = 0; lastChange = System.currentTimeMillis();
            }
        });
    }

    private static String[] concat() {
        String[] r = new String[SKINS.length + 1];
        System.arraycopy(SKINS, 0, r, 0, SKINS.length);
        r[SKINS.length] = DECOR;
        return r;
    }

    private static void fault(String msg) {
        Features.get("vehicle_skins").status = msg;
        Features.get("base_decor_pack").status = msg;
    }

    private static String find(PackRepository repo, String name) {
        for (String id : repo.getAvailableIds()) if (id.equals(name) || id.endsWith("/" + name) || id.endsWith(":" + name)) return id;
        return null;
    }

    public static void tick(Minecraft mc) {
        if (!registered || gaveUp || !dirty || System.currentTimeMillis() - lastChange < 600) return;
        try {
            PackRepository repo = mc.getResourcePackRepository();
            List<String> selected = new ArrayList<>(repo.getSelectedIds());
            List<String> next = new ArrayList<>(selected);
            for (String n : concat()) { String id = find(repo, n); if (id != null) next.remove(id); }
            boolean ok = true;
            if (Config.on("vehicle_skins")) {
                String id = find(repo, SKINS[Config.c("vehicle_skins", "skin")]);
                if (id == null) ok = false; else next.add(id);
            }
            if (Config.on("base_decor_pack")) {
                String id = find(repo, DECOR);
                if (id == null) ok = false; else next.add(id);
            }
            if (!ok) {
                if (++attempts >= 3) { gaveUp = true; fault("Built-in pack not found in the resource pack list"); }
                lastChange = System.currentTimeMillis();
                return;
            }
            dirty = false;
            Features.get("vehicle_skins").status = null;
            Features.get("base_decor_pack").status = null;
            if (!new java.util.HashSet<>(next).equals(new java.util.HashSet<>(selected)) || !next.equals(selected)) {
                repo.setSelected(next);
                mc.options.updateResourcePacks(repo); // saves options and reloads resources when the selection changed
            }
        } catch (Throwable t) {
            gaveUp = true;
            LOG.error("[MilitaryClient] Resource pack sync failed", t);
            fault("Pack toggle failed: " + t.getClass().getSimpleName());
        }
    }
}
