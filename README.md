# EarthMC Hunter Alert

Standalone Fabric client mod using EarthMC's public Squaremap player feed and v4 API. [EarthMC Map Addon](https://modrinth.com/mod/emcmap-addon) is optional; supported version 1.4.5 shares its existing requests and caches. It does not discover hidden players, inspect remote player entities, or infer physical location from town membership.

## Use

Install the matching Hunter Alert JAR, Fabric Loader and Fabric API. No minimap or base addon is required. EMC Map Addon **1.4.5** and Xaero can optionally be installed with their own dependencies. Press **H** (rebindable in Controls) for settings, hunter/watch management, alert history, and HUD positioning. The mod activates only on `earthmc.net` or its subdomains. Settings can be edited off-server. With Mod Menu installed, use Hunter Alert's Configure button. With EarthMC Map Addon installed, open its map Settings, then **Hunter Alert → Hunter Alert Settings**.

Open **H → Hunters**, enter a Minecraft name and optional note, then choose **Save known hunter** or **Save friendly hunter**. Friendly hunters are whitelisted: they are excluded from Hunter Alert's nearby panel, threat sounds, approach/departure alerts, affiliation warnings and map markers, even if their outlaw count is high. Whitelisting clears existing last-seen and affiliation clues on the next evaluation, including during feed outages. Disable or remove the friendly entry to resume detection. Selecting a row lets you change its category or note. Friendly status takes precedence over known status in manually edited configurations. This does not hide that player from the base map's normal player layer.

**HUD → Hide Default Action Bar** is enabled by default. On EarthMC it suppresses only the exact action-bar message `Default`; other messages and other servers are unaffected. The originating mod/server feature has not been identified from the screenshot. If that text is rendered through another HUD layer, this filter will not remove it.

The default ranges are 500 / 1500 / 3000 / 5000 blocks. Known hunters override filtering; automatic hunters default to players outlawed by at least ten towns. The HUD puts threats first, then sorts them by outlaw count, classification and distance. Ordinary alerts are off by default. Known hunters override town/nation ignore filters. Classification is a configurable heuristic, not proof of wrongdoing.

`MAP EXPOSED` means the local name/UUID appears in a healthy public map response. `MAP HIDDEN` means it is absent from a healthy response; this says nothing about other ways to detect a player. Failed/malformed/stale feeds become `MAP STATUS UNKNOWN`. Map exposure timers are computed locally. The platform maps local Nether coordinates to overworld blocks; End locations are unknown. A fresh public self position takes precedence.

Affiliation alerts explicitly separate membership from physical position. Nation proximity uses the nearest known member-town anchor in the same world. Standalone mode uses API town spawns. The optional addon boundary provides claim bounding centres instead. Distances identify the anchor used.

Configuration and bounded history are saved to `config/hunteralert/config.json` and `history.json`, using coalesced atomic writes. Corrupt input is preserved as an `.invalid-<timestamp>` backup. Files remain local.

## Exposure and threat clues

The HUD displays an **exposure meter from 0% to 100%**. It starts at zero for each connection, fills over 60 seconds of observed public visibility, and drains over 120 seconds while absent from a healthy feed. Unknown/stale data pauses the meter. Configure `exposureRiseSeconds` and `exposureFallSeconds` under Exposure. This is an accumulated exposure indicator, not a measured probability of attack or potion detection.

The highest-outlaw nearby threat appears at the top even in compact mode. Red means critical range, orange warning range, yellow a more distant threat, and gray stale data. In standalone mode, public-position markers are projected in-game as horizontal X/Z bearings; target altitude is unknown. Installing EarthMC Map Addon suppresses these in-world labels and uses its full-screen map instead. A vanished hunter retains a gray last-seen marker until `expireSeconds` (default 120), without extrapolating movement.

When a monitored hunter changes town or nation, proximity is checked against both your current position and your own town's anchor in the same world. A matching change triggers an affiliation warning and a gold **POSSIBLE RISK** clue. Nation changes use the nearest member-town anchor. A hidden hunter can therefore generate an API-based warning even without a public position. Missing profiles never mean a player left a town. An initial profile establishes a baseline; it is not treated as a new join.

With EMC Map Addon 1.4.5 installed, live positions, last-seen positions and affiliation-town anchors are drawn directly on the full-screen EarthMC map. Markers follow its current world, pan and zoom. Red live labels, gray `LAST SEEN`, and gold `CLUE` labels distinguish their evidence. No Xaero waypoints are created. Archive and screenshot modes suppress this overlay. Affiliation markers expire after `affiliationMarkerSeconds` (default 300).

`mapHighlights` controls the optional full-screen map overlay. `worldMarkers` controls projected in-game bearing labels in standalone mode only. Both are available in HUD settings. API requests are batched and rate-limited, so warnings can lag changes; addon caches may add delay. Neither an affiliation clue nor an old coordinate establishes an invisible player's current location.

Exposure notifications use a higher-pitched note-block **pling**; hunter proximity, approach, disappearance and affiliation warnings use a lower-pitched **bell**. Change `exposureSoundId` and `threatSoundId` independently under Notifications. Other alerts retain `soundId`; all sounds respect the existing sound toggle and volume.

## HUD placement

**Avoid HUD Overlap** is enabled by default, including for existing configurations. Hunter Alert measures Xaero's map bounds and rendered coordinate/info text, including EMC Map Addon's town/nation lines, in GUI pixels. It chooses the closest free position to your preferred position, accounts for the hotbar/status area, and recalculates when the map moves, grows, or GUI scale changes. If necessary it reduces its own display scale; if no space exists even at 0.5 scale, it temporarily omits the panel instead of drawing over an occupied area. Alert sounds, chat and toasts remain available.

The HUD editor stores your preferred position. Disable overlap avoidance for fully manual positioning. Other HUD mods can register occupied rectangles through `HudObstacles.publish(id, Rect)` each frame; entries expire after one second. Automatic geometry detection currently supports Xaero, not every third-party minimap/HUD. For unsupported HUDs, use manual placement or the registration API. Live minimap geometry is observed during rendering, so a changed map layout may take one frame to settle.

## Architecture

* `model`, `detection`, `alert`, `config`, and `data`: Minecraft-independent models, threat state machine, movement/exposure, persistence and provider contracts.
* `platform`: all Minecraft/Fabric UI, sound, toast, rendering and version-specific code; direct public addon adapters.
* `platform/mixin`: optional, gated addon feed/client hooks and Xaero bounds/text observers. No optional mod classes are required during standalone startup.
* `platform/AddonMapOverlay`: optional full-screen EarthMC map overlay using its viewport coordinates; `MapProjection` provides the shared pan/zoom transform.

In standalone mode, positions refresh every three seconds on a dedicated HTTP worker. Town/outlaw intelligence refreshes every ten minutes; profiles refresh every minute on a separate worker, with batches of at most 100 names and at most one API request per second. HTTP failures preserve the previous data, failed feed observations become unknown, and generation checks reject late responses after disconnect. In addon mode, map requests share upstream's polling gate. Outlaw counts reuse its ten-minute reverse index. Tracked affiliation refreshes batch names through the same API client every minute, including players absent from the map. No full-player-directory sweep is started. Missing API profiles never imply leaving a town. Immutable snapshots cross from workers to the client. Movement uses a short bounded history and evaluates hunter displacement against one fixed local reference, so your own travel does not falsely make a stationary hunter approach.

## Branches and verification

`main` holds shared architecture/documentation with 1.21.11 as the reference platform; `mc/1.21.11`, `mc/26.1.x`, and `mc/26.2` contain their own build configuration and platform adapter. Shared behavior changes should be carried across branches; do not add runtime version switches. The local `dist/` directory contains the three tested runtime JARs and SHA-256 checksums.

Use Java 21 for 1.21.11 and Java 25 for 26.x:

```powershell
.\gradlew.bat clean build
.\gradlew.bat runClient
.\gradlew.bat runClient -Psmoke
.\gradlew.bat runClient -Psmoke -PwithMinimap
.\gradlew.bat runClient -Psmoke -PwithAddon -PwithModMenu
```

The development-only smoke option uses a separate `run/smoke` directory, exercises optional addon parser/health hooks with loopback HTTP fixtures when the addon is enabled, renders title/settings/management/history/HUD screens, captures screenshots, and exits. Its HUD uses a synthetic fixture; this is not evidence of a live EarthMC connection. Version 0.3.2 passed 36 tests per branch and client smoke checks in standalone and addon-plus-Mod-Menu configurations, including the addon settings button click/return and registered Mod Menu settings factory. Version 0.2 additionally tested minimap-only configurations. See [verification results](docs/VERIFICATION.md).

Unit tests cover distances, radius transitions, threshold boundaries, exposure/outages, movement/jitter/stale data, local-motion rejection, UUID fallback, cooldowns, malformed feeds, session isolation, affiliation semantics and safe persistence.

## Current limits

No reliable friend-list API is exposed, so a friend filter is not offered. When using the optional addon, upstream supplies name-keyed outlaw counts, not the set of outlawing towns or a successful-sweep timestamp. Count classification therefore inherits upstream cache freshness, including its partial-failure behavior. Squaremap supplies no per-player measurement timestamp through this boundary; player age represents successful feed observation, not proof that an unchanged coordinate was recently measured. Full live-server acceptance requires an authenticated Minecraft session.

Recent explicit API offline status suppresses proximity, departure and affiliation warnings and clears threat markers. Positive server player-list presence overrides API status; absence from the player list does not prove logout. Missing/opted-out API profiles remain unknown. Offline evidence expires after 90 seconds. The addon requires a last-online timestamp before trusting its default-false online flag. A public-map disappearance is informational, with unknown online status, rather than a hunter threat alarm.

## Version 0.4: weather, suggestions and approach warnings

Hunter Alert now has its own **Hunter Alert** category directly below General in the optional map addon's left sidebar. Open **Hunter Alert Settings** from that category. It remains accessible in a small window, and opening/returning preserves the category.

The Hunters screen opens on **Candidates**, a paginated list built from the entire available cached outlaw index, sorted by outlaw-town count. **Automatic Hunters → Minimum outlaw towns** sets the inclusive threshold. Friendly/whitelisted players and yourself are excluded. Candidates can be selected for editing or promoted to known hunters. **Saved players** switches to the manual list. Candidates can be offline: this is a management suggestion, not an online-status or intent claim. Counts reflect the provider's existing refresh cycle; use Search to refresh the candidate rows.

Typing a player name immediately offers up to three case-insensitive prefix matches with outlaw-town counts. Click a suggestion to fill the exact name and retain its UUID when known. Suggestions combine the server player list, public map, API profiles, cached outlaw names and saved entries; typing starts no network requests. An empty name field suggests candidates meeting your configured threshold.

**Weather → Rain alerts** enables a notice when Overworld rain is observed, plus a persistent rain/Riptide reminder in the HUD. Rain can permit fast Riptide trident travel, so the notice calls out extra caution in wilderness. It does not assert that your current position is wilderness, that a hunter has a trident, or that rain is falling at a remote hunter's location. The notice works even when the map feed is unavailable; sustained rain does not repeat it every tick. Rain risk is separate from the exposure percentage. Riptide mechanics: https://www.minecraft.net/en-us/article/tridents-everyone-

**Movement → Approaching players** enables ordinary-player approach alerts independently of ordinary proximity notifications. The new-install approach threshold is **150 blocks** of the other player's movement toward your current position within the configured window (10 seconds by default). Existing saved thresholds are preserved and can be changed in Movement. Your own travel, stale feeds and cross-world coordinates cannot establish approach. High-outlaw players produce **POTENTIAL HUNTER APPROACHING**, known hunters **HUNTER APPROACHING**, and others **PLAYER APPROACHING**. Friendly players and players with recent offline evidence remain excluded.

Nearby town/nation affiliation changes continue to produce **AFFILIATION WARNING** and now explicitly explain possible incoming travel. Proximity uses the nearest same-world town anchor relative to you or your own town. A membership change can suggest travel but cannot confirm direction or intent; the warning states that uncertainty. Initial/missing profiles never imply a new join.

## Version 0.5: confirmed approach, observed paths, compact log

Approach warnings now require **three consecutive distinct map movements** by default. In **Movement**, configure **Approach range (blocks)** (default 1500), **Approach observations** (default 3), **Approaching threshold** (default 150 total blocks), **Minimum movement** (default 30 blocks per qualifying observation), and **Movement window seconds** (default 10). Both endpoints of each counted movement must be within the approach range, capped by the overall Maximum detection range. Each step measures the other player's displacement against the same current local position, so your own travel cannot increment the count.

Repeated evaluation of cached coordinates does not count as another instance. Stopping, reversing, movement below the minimum, leaving range, missing/stale observations, world changes, whitelisting and offline suppression break the sequence. Evidence ages out with the movement window. One warning is issued per qualifying continuous approach, subject to the existing cooldown; a new approach requires new evidence. Existing distance thresholds are preserved when upgrading. Proximity, affiliation and weather notifications remain separate from approach confirmation.

**Path trace** (enabled by default) draws up to four recent observed hunter paths with directional arrows on the optional EarthMC map. These are samples from the current in-range approach sequence, including its silent confirmation period, not a predicted route or a line to your position. Ordinary-player movements still generate confirmation/log entries but are not drawn as hunter paths. Traces stop when evidence becomes stale, disappears or no longer qualifies. Drawing is clipped and bounded. In standalone mode, paths use the existing public X/Z bearing projection; altitude is unknown and a straight radial approach can collapse to one bearing.

**HUD > Compact alert log** is enabled by default, including upgrades. It replaces the large toast presentation and legacy threat panel with a bounded session log. Pre-alert observations (1/3, 2/3, etc.) are silent. Confirmed warnings keep their configured sound; `[H?]`, `[H]`, and `[P]` identify potential hunters, known hunters and ordinary players. **Log rows** (default 4, range 1-8) and **Log seconds** (default 60, range 5-600) control its contents. Names/counts/distances are prioritized in narrow rows. Width is capped at 300 GUI pixels or 48% of the window, and rows/scale adapt to a quarter of its height. Existing overlap avoidance is retained. Window resizing and GUI scale changes are handled at render time.

Turn **Compact alert log** off to restore the previous panel and toast behavior (respecting the existing Toast toggle). Full accepted alert messages remain available in History; silent observations are only kept in the bounded session log. Disconnecting clears that log. No extra network requests are made for confirmation or traces.

## Version 0.5.1: persistent awareness

The compact log pins current hunters above recent alerts, showing block distance, compass direction and X/Z coordinates. These rows update from map observations and remain after alert-log entries expire, until the hunter leaves the configured Maximum detection range or is excluded. Unavailable positions are explicitly marked LAST or location unknown and expire with the existing stale-data settings. The panel retains its window-size limits and maximum-entry setting; excess hunters are summarized. Exposure messages use complete short labels, and other shortened text ends with an ellipsis instead of a clipped word.

## Version 0.5.2: readable HUD text

Both HUD styles wrap complete messages to the available width, including long names, instead of adding ellipses. The compact panel uses a dark background with at least 92% opacity and an edge flash so the world and alert flash do not obscure the lettering. The panel uses up to 40% of the window height, tries narrower layouts to avoid other HUDs, and preserves the configured font scale. Overflow is automatically paged with a page number, spending at least eight seconds per page. Hunter rows stay pinned when room remains for alert text; when hunters alone fill the panel, all rows cycle. Log rows still controls the number of recent entries, not wrapped lines. Log expiry remains configurable and full accepted messages remain in History. Extremely crowded HUDs may require repositioning through the HUD editor. These changes supersede the clipping and quarter-height rules above.

## Version 0.5.3: entry dividers

The compact HUD draws a horizontal divider before each hunter group and alert entry. Wrapped continuation lines stay together; separators use the existing line spacing, preserving panel size and pagination.

## Version 0.5.4: dynmap status and town travel alerts

The compact log reserves a fixed dynmap status row: yellow EXPOSED or green HIDDEN with the elapsed time in that state. Unavailable data shows gray UNKNOWN. Status stays visible across log pages and does not expire with alerts. Automatic placement reserves the minimap column, keeping the log beside it rather than below it; crowded layouts page or narrow the log.

Town/nation changes name the new affiliation even outside warning range. Nearby affiliations show concise travel warnings and optional EarthMC map clues, including an already-online hunter whose existing town or nation anchor comes into range. These are possible travel points, not confirmed hunter positions. Recent offline evidence still suppresses alerts.
