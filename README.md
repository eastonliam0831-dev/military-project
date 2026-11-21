# Military Client — Fabric 26.3 (client-side only)

A client-side military command suite for **Minecraft Java 26.3** on **Fabric**: tactical HUD, GPS, radar, night
vision, thermal view, aircraft tracking, synthesized ambience, weather/atmosphere effects, a military font set,
loading screens, and one centralized control panel. No server mod is needed and nothing is sent to servers
(`"environment": "client"`).

## Requirements

| What | Version |
|---|---|
| Minecraft Java | 26.3 |
| Fabric Loader | 0.19.3 or newer |
| Fabric API | 0.161.0+26.3 (any 26.3 build) |
| JDK to build | 25 |

## Build

```bash
# Gradle wrapper jar is binary, so generate it once (Gradle 9.7.1+):
gradle wrapper --gradle-version 9.7.1
./gradlew build            # Windows: gradlew.bat build
```
The mod jar is `build/libs/militaryclient-1.0.0.jar` (use that one, not `-sources`).
Verify the numbers in `gradle.properties` against https://fabricmc.net/develop/ if the build can't resolve them.
Dev client: `./gradlew runClient`.

## Install
1. Install Fabric Loader for 26.3 and put **Fabric API** in `.minecraft/mods`.
2. Put `militaryclient-1.0.0.jar` in `.minecraft/mods`.
3. Launch. Press **Right Shift** (default) in game, or on the title/pause screen, to open the panel.

## Using it
* **Open/close**: the key *Open Military Control Panel* (default Right Shift; press again to close).
  It is a normal Minecraft key mapping: **Options → Controls → Key Binds → MILITARY CLIENT**, with Minecraft's
  own conflict warnings and reset button. The panel's **KEYBINDS** tab can also rebind, unbind, reset and shows conflicts.
* Other keys (all unbound by default so they never clash with other mods): Binoculars/Zoom, Aircraft Camera,
  Add Waypoint Here, Radio Next/Previous Channel.
* Panel tabs: Dashboard, HUD & Tactical, Cosmetics, Atmosphere, Aircraft & Vehicle, Immersion, General, Keybinds.
  Every feature card has ON/OFF, SETTINGS (its own adjustable values), RESET, a description, and an amber
  limitation note when one applies. Each category has ENABLE ALL / DISABLE ALL / RESET. A search box finds features.
* **General** holds Advanced Customization (accent color, transparency, HUD scale, text size, panel scale,
  animation speed, master volume, particle density, fog multiplier, low-performance mode), aircraft detection
  keywords, presets, import/export and the waypoint manager.
* **Presets** (Modern Military, Fighter Pilot, Army, Tactical Green, Night Operations, Minimal Military) change only
  visuals and audio.

## Settings & files
* `config/militaryclient.json` — auto-saved about 1 s after any change, on closing the panel and on exit. A broken
  file is copied to `militaryclient.json.broken` and defaults are used.
* Export/Import use `config/militaryclient-export.json`.
* Key assignments are stored by Minecraft in `options.txt` (a mirror is kept in the config for export).

## The 50 features and how each is implemented

| # | Feature | Implementation / client-side limit |
|---|---|---|
| 1 | Tactical HUD | Panel: coords, heading, FPS, time, health, biome, rank/name. |
| 2 | Targeting display | 5 reticle styles, pitch ladder, lock brackets. Decorative, never locks on anything. |
| 3 | Digital compass | Strip or digital style. |
| 4 | GPS | Position, dimension, heading, distance + bearing to waypoints. |
| 5 | Night vision | Real brightness boost via the Brightness option (restored on off/exit) + tint, scanlines, noise. |
| 6 | Thermal | Palette wash + heat boxes, only for creatures in line of sight (no X-ray). |
| 7 | Map overlay | Top-down map from loaded chunks, waypoints, coordinates. |
| 8 | Radar | Sweep scope. *Visible Contacts* plots only what you can see; *Decorative* is labelled SIM. |
| 9 | Altitude/coords | 4 number formats, ASL and AGL. |
| 10 | Military inventory | Tint, brackets, header, scanlines over container screens; slot textures not replaced. |
| 11–20 | Uniform, camo, helmet, vest, flight suit, gas mask, backpack, dog tags, rank, patches | **Limit:** shown in the *Operator Loadout* (Cosmetics tab), the HUD badge and first-person overlays (helmet rim, vest straps, visor, gas-mask lens). A real 3D player-model layer needs the entity render-layer API and is **not included**. 9 procedural camo patterns + custom colors. |
| 21–25 | Aircraft, helicopter, jet, explosions, radio | Synthesised in code (no audio files) and played via Java Sound, scaled by Minecraft master volume. Rare by design; max one ambient bed at a time. Radio is babble/beeps, not speech. |
| 26 | Wind | Synthesised gusts, optional outdoors-only. |
| 27, 28 | Dust, smoke | Vanilla particles, capped per tick, density scaled by global multiplier. |
| 29 | Fog | **Limit:** screen-space haze, not depth fog. |
| 30 | Rain/storms | Extra splashes, darkening, lightning flashes, synthesized thunder. Weather itself is server-controlled. |
| 31–32, 35, 38 | Spotting, labels, engine audio, ID cards | **Limit:** vanilla has no aircraft. Phantoms, Ender Dragons, Ghasts and elytra gliders count; modded entities match by id keywords (General → Aircraft Tracking). |
| 33 | Contrails | Cloud/smoke particles behind moving aircraft. |
| 34 | Vehicle shadows | Screen-space blob on the surface below; not a shadow map. |
| 36, 39 | Vehicle inspection, animated displays | Data panel for the vehicle you look at; animated dashboard while riding. |
| 37 | Aircraft camera | Viewfinder, REC, tracking box, FOV zoom (min 30°). Not a second camera render. |
| 40 | Vehicle skins | Built-in resource packs (olive, desert, woodland, urban) for vanilla boats and minecarts. Toggle reloads resources. |
| 41 | Base decoration | Built-in pack retexturing camo wool, hazard concrete, steel plate, runway asphalt. No custom furniture models. |
| 42 | Signage | Zone banners from waypoint types (Base, Restricted, Hazard, Landing Zone, Runway). Sign blocks not retextured. |
| 43 | Runway lights | Particle lights between waypoints of type Runway A and Runway B. |
| 44 | Warning lights | Corner beacons (always / low health / hostile visible / storm) + world beacons at Hazard waypoints. |
| 45 | Hangar lighting | Screen-space light strips and tint when indoors. |
| 46 | Radio GUI | Channel, frequency, signal bars, waveform; channel selects chatter style. |
| 47 | Fonts | Tactical Pixel, Stencil, Digital LCD drawn by the mod (A–Z, 0–9, punctuation). |
| 48 | Waypoint markers | Diamond/chevron/pin with name and distance. |
| 49 | Binoculars | Binocular/scope/rangefinder overlay, FOV zoom (min 30°), range readout. |
| 50 | Loading screens | 6 procedural scenes + tips over loading/connecting screens; bar is animated, not real progress. |

## Performance & safety
* Every feature is isolated: an exception disables only that feature, logs once, and shows the reason on the card
  and on the Dashboard. Toggling it off/on retries.
* FOV and Brightness are restored on disconnect and on shutdown.
* Particle spawns are capped per tick; entity scans run every other tick with a line-of-sight budget; map sampling
  is throttled; low-performance mode halves rates.
* Entities are only exposed to radar/thermal when you have line of sight; no information the client would not
  show you anyway.

## Source layout
```
config/  Features (registry of all 50), Config (persistence), Presets, Waypoint
hud/     HudLayer (rendering), Tracker, Controllers (zoom/brightness/map), Cosmetics, Scenes
fx/      Atmosphere (particles, weather)     audio/  Synth + AudioEngine
ui/      ControlPanelScreen                  util/   Gfx, PixelFont, Camo, Proj, Compat, Dim
tools/gen_textures.py   regenerates the resource packs
```

## If it does not compile
This project was written against the 26.3 Fabric documentation but **could not be compiled in the environment
that produced it** (no network, no Minecraft jars). All logic was type-checked against hand-written stubs only.
Names most likely to need a one-line tweak against the real 26.3 jar:
`GuiGraphicsExtractor.fillGradient/enableScissor/disableScissor`, `Matrix3x2fStack` calls, `Minecraft.getFps()`,
`Entity.pick(...)`, `Options.updateResourcePacks(...)`, `KeyMapping.isUnbound/resetMapping/getDefaultKey`,
`ScreenEvents.afterExtract`, `Level.addAlwaysVisibleParticle`, `BlockState.getMapColor`,
`Holder.getRegisteredName`, `PackRepository.setSelected`. `Compat.java` already wraps screen switching,
container-screen fields and volume lookup in reflection for this reason.
Resource pack `pack.mcmeta` uses a wide `min_format`/`max_format` range; tighten it if Minecraft complains.
