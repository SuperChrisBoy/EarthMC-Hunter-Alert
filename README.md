# EarthMC Hunter Alert

Standalone Fabric client mod using EarthMC's public Squaremap player feed and v4 API. [EarthMC Map Addon](https://modrinth.com/mod/emcmap-addon) is optional; supported version 1.4.5 shares its existing requests and caches. It does not discover hidden players, inspect remote player entities, or infer physical location from town membership.

## Use

Install the matching Hunter Alert JAR, Fabric Loader and Fabric API. No minimap or base addon is required. EMC Map Addon **1.4.5** and Xaero can optionally be installed with their own dependencies. Press **H** (rebindable in Controls) for settings, hunter/watch management, alert history, and HUD positioning. The mod activates only on `earthmc.net` or its subdomains. Settings can be edited off-server.

The default ranges are 500 / 1500 / 3000 / 5000 blocks. Known hunters outrank automatic hunters (default: outlawed by at least ten towns), watched players, and ordinary players. Ordinary alerts are off by default. Known hunters override town/nation ignore filters. Classification is a configurable heuristic, not proof of wrongdoing.

`MAP EXPOSED` means the local name/UUID appears in a healthy public map response. `MAP HIDDEN` means it is absent from a healthy response; this says nothing about other ways to detect a player. Failed/malformed/stale feeds become `MAP STATUS UNKNOWN`. Map exposure timers are computed locally. The platform maps local Nether coordinates to overworld blocks; End locations are unknown. A fresh public self position takes precedence.

Affiliation alerts explicitly separate membership from physical position. Nation proximity uses the nearest known member-town anchor in the same world. Standalone mode uses API town spawns. The optional addon boundary provides claim bounding centres instead. Distances identify the anchor used.

Configuration and bounded history are saved to `config/hunteralert/config.json` and `history.json`, using coalesced atomic writes. Corrupt input is preserved as an `.invalid-<timestamp>` backup. Files remain local.

## HUD placement

**Avoid HUD Overlap** is enabled by default, including for existing configurations. Hunter Alert measures Xaero's map bounds and rendered coordinate/info text, including EMC Map Addon's town/nation lines, in GUI pixels. It chooses the closest free position to your preferred position, accounts for the hotbar/status area, and recalculates when the map moves, grows, or GUI scale changes. If necessary it reduces its own display scale; if no space exists even at 0.5 scale, it temporarily omits the panel instead of drawing over an occupied area. Alert sounds, chat and toasts remain available.

The HUD editor stores your preferred position. Disable overlap avoidance for fully manual positioning. Other HUD mods can register occupied rectangles through `HudObstacles.publish(id, Rect)` each frame; entries expire after one second. Automatic geometry detection currently supports Xaero, not every third-party minimap/HUD. For unsupported HUDs, use manual placement or the registration API. Live minimap geometry is observed during rendering, so a changed map layout may take one frame to settle.

## Architecture

* `model`, `detection`, `alert`, `config`, and `data`: Minecraft-independent models, threat state machine, movement/exposure, persistence and provider contracts.
* `platform`: all Minecraft/Fabric UI, sound, toast, rendering and version-specific code; direct public addon adapters.
* `platform/mixin`: optional, gated addon feed/client hooks and Xaero bounds/text observers. No optional mod classes are required during standalone startup.
* `integration/map/ThreatMapLayer`: optional map-layer contract. Direct Xaero marker rendering is deferred because no stable addon extension API exists.

In standalone mode, positions refresh every three seconds on a dedicated HTTP worker. Town/outlaw/profile intelligence refreshes every ten minutes on a separate worker, with batches of at most 100 names and at most one API request per second. HTTP failures preserve the previous data, failed feed observations become unknown, and generation checks reject late responses after disconnect. In addon mode, map requests share upstream's polling gate. Outlaw counts reuse its ten-minute reverse index. Tracked affiliation refreshes batch names through the same API client every five minutes, including players absent from the map. No full-player-directory sweep is started. Missing API profiles never imply leaving a town. Immutable snapshots cross from workers to the client. Movement uses a short bounded history and evaluates hunter displacement against one fixed local reference, so your own travel does not falsely make a stationary hunter approach.

## Branches and verification

`main` holds shared architecture/documentation with 1.21.11 as the reference platform; `mc/1.21.11`, `mc/26.1.x`, and `mc/26.2` contain their own build configuration and platform adapter. Shared behavior changes should be carried across branches; do not add runtime version switches. The local `dist/` directory contains the three tested runtime JARs and SHA-256 checksums.

Use Java 21 for 1.21.11 and Java 25 for 26.x:

```powershell
.\gradlew.bat clean build
.\gradlew.bat runClient
.\gradlew.bat runClient -Psmoke
.\gradlew.bat runClient -Psmoke -PwithMinimap
.\gradlew.bat runClient -Psmoke -PwithAddon
```

The development-only smoke option uses a separate `run/smoke` directory, exercises optional addon parser/health hooks with loopback HTTP fixtures when the addon is enabled, renders title/settings/management/history/HUD screens, captures screenshots, and exits. Its HUD uses a synthetic fixture; this is not evidence of a live EarthMC connection. Each branch passed 22 tests and client smoke checks in standalone, minimap-only, and addon configurations. See [verification results](docs/VERIFICATION.md).

Unit tests cover distances, radius transitions, threshold boundaries, exposure/outages, movement/jitter/stale data, local-motion rejection, UUID fallback, cooldowns, malformed feeds, session isolation, affiliation semantics and safe persistence.

## Current limits

No reliable friend-list API is exposed, so a friend filter is not offered. Direct map markers and last-known marker layers remain behind an extension interface. When using the optional addon, upstream supplies name-keyed outlaw counts, not the set of outlawing towns or a successful-sweep timestamp. Count classification therefore inherits upstream cache freshness, including its partial-failure behavior. Squaremap supplies no per-player measurement timestamp through this boundary; player age represents successful feed observation, not proof that an unchanged coordinate was recently measured. Full live-server acceptance requires an authenticated Minecraft session.
