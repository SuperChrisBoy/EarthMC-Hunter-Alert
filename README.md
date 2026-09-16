# EarthMC Hunter Alert

Fabric client companion to [EarthMC Map Addon](https://modrinth.com/mod/emcmap-addon). Uses the addon's existing public map feed and EarthMC API clients. It does not discover hidden players, inspect remote player entities, or infer physical location from town membership.

## Use

Install the matching Hunter Alert JAR, Fabric API, EMC Map Addon **1.4.5**, and its Xaero dependencies. Press **H** (rebindable in Controls) for settings, hunter/watch management, alert history, and HUD positioning. The mod activates only on `earthmc.net` or its subdomains, also respecting the addon's activation check. Settings can be edited off-server.

The default ranges are 500 / 1500 / 3000 / 5000 blocks. Known hunters outrank automatic hunters (default: outlawed by at least ten towns), watched players, and ordinary players. Ordinary alerts are off by default. Known hunters override town/nation ignore filters. Classification is a configurable heuristic, not proof of wrongdoing.

`MAP EXPOSED` means the local name/UUID appears in a healthy public map response. `MAP HIDDEN` means it is absent from a healthy response; this says nothing about other ways to detect a player. Failed/malformed/stale feeds become `MAP STATUS UNKNOWN`. Map exposure timers are computed locally. The platform maps local Nether coordinates to overworld blocks; End locations are unknown. A fresh public self position takes precedence.

Affiliation alerts explicitly separate membership from physical position. Nation proximity uses the nearest known member-town anchor in the same world. The current addon public boundary provides claim bounding centres, so these distances are labelled approximate; spawn/home coordinates are not fabricated.

Configuration and bounded history are saved to `config/hunteralert/config.json` and `history.json`, using coalesced atomic writes. Corrupt input is preserved as an `.invalid-<timestamp>` backup. Files remain local.

## Architecture

* `model`, `detection`, `alert`, `config`, and `data`: Minecraft-independent models, threat state machine, movement/exposure, persistence and provider contracts.
* `platform`: all Minecraft/Fabric UI, sound, toast, rendering and version-specific code; direct public addon adapters.
* `platform/mixin`: two narrowly pinned addon hooks, exposing successful/failed map request completion and its existing API client. No reflection or second HTTP client.
* `integration/map/ThreatMapLayer`: optional map-layer contract. Direct Xaero marker rendering is deferred because no stable addon extension API exists.

Map requests share upstream's polling gate. Outlaw counts reuse its ten-minute reverse index. Tracked affiliation refreshes batch names through the same API client every five minutes, including players absent from the map. No full-player-directory sweep is started. Missing API profiles never imply leaving a town. Immutable snapshots cross from workers to the client. Movement uses a short bounded history and evaluates hunter displacement against one fixed local reference, so your own travel does not falsely make a stationary hunter approach.

## Branches and verification

`main` holds current architecture and documentation; `mc/1.21.11`, `mc/26.1.x`, and `mc/26.2` contain their own build configuration and platform adapter. Shared behavior changes should be carried across branches; do not add runtime version switches.

Use Java 21 for 1.21.11 and Java 25 for 26.x:

```powershell
.\gradlew.bat clean build
.\gradlew.bat runClient
.\gradlew.bat runClient -Psmoke
```

The development-only smoke option uses a separate `run/smoke` directory, renders title/settings/management/history/HUD screens, captures screenshots, and exits. Its HUD uses a clearly synthetic fixture; this is not evidence of a live EarthMC connection. See [verification results](docs/VERIFICATION.md).

Unit tests cover distances, radius transitions, threshold boundaries, exposure/outages, movement/jitter/stale data, local-motion rejection, UUID fallback, cooldowns, malformed feeds, session isolation, affiliation semantics and safe persistence.

## Current limits

No reliable friend-list API is exposed, so a friend filter is not offered. Direct map markers and last-known marker layers remain behind an extension interface. Upstream supplies name-keyed outlaw counts, not the set of outlawing towns or a successful-sweep timestamp. Count classification therefore inherits upstream cache freshness, including its partial-failure behavior. Squaremap supplies no per-player measurement timestamp through this boundary; player age represents successful feed observation, not proof that an unchanged coordinate was recently measured. Full live-server acceptance requires an authenticated Minecraft session.
