# Verification ledger

Verified on Windows, 2026-09-17 (Asia/Tokyo). These results distinguish executable checks from unverified live-server acceptance.

| Branch | Minecraft tested | Java | Loom | Loader | Fabric API | Build | Unit tests | Feed bridge | Client/UI smoke |
|---|---|---|---|---|---|---|---|---|---|
| mc/1.21.11 | 1.21.11 | 21.0.12.1 | 1.14.10 | 0.18.3 | 0.140.0+1.21.11 | PASS | 36/36 | PASS | PASS |
| mc/26.1.x | 26.1.2 | 25.0.1 | 1.17.9 | 0.19.2 | 0.150.0+26.1.2 | PASS | 36/36 | PASS | PASS |
| mc/26.2 | 26.2 | 25.0.1 | 1.17.9 | 0.19.3 | 0.152.2+26.2 | PASS | 36/36 | PASS | PASS |

Every branch ran `gradlew.bat --no-daemon clean build runClient -Psmoke --warning-mode all` and exited successfully. The 26.1.x branch was built and launched on 26.1.2; earlier 26.1 patch releases were not separately launched. The 1.21.11 branch uses Yarn 1.21.11+build.4; the 26.x branches use Minecraft's unobfuscated names with no mappings dependency.

Optional addon tests use EMC Map Addon 1.4.5; standalone and minimap-only tests run without it. Development runtime pins are Xaero Minimap 26.5.0 / World Map 1.46.0 on 1.21.11 and 26.1.2; Minimap 26.5.1 / World Map 1.46.1 on 26.2. XaeroLib 1.7.3 is explicit because Loom's development runtime does not automatically expose that nested JAR. These dependencies are not bundled into Hunter Alert.

## Version 0.3.2 friendly hunters and exact action-bar filter

All three builds pass 36 tests. Added coverage checks friendly precedence over known/automatic hunters with ordinary alerts enabled, stale-feed cleanup, affiliation/last-seen marker removal without false departure alerts, disabling a whitelist entry, UUID/name persistence, and exact action-bar text matching.

The manager now has separate known, friendly and watched save buttons. Existing full-screen map overlays and Mod Menu/addon Settings entry points remain in place. Client smoke checks exercise these real optional integrations and render the updated manager. Standalone startup is checked separately.

The `Default` screenshot resembles an action-bar message, but its producer was not identified. An EarthMC-only, configurable filter suppresses that exact action-bar text at the vanilla HUD render hook. Hook loading is checked in each client; tests check exact matching and preservation of other messages. Removal of the user's specific on-server label remains unverified. The filter intentionally does not suppress arbitrary HUD text. No user game configuration or installed mod JARs were modified.

## Version 0.3.1 map, settings and audio fixes

Each branch passed `build runClient -Psmoke -PwithAddon -PwithModMenu --offline` and a separate standalone `runClient -Psmoke --offline`. All 31 tests pass, including full-screen map pan/zoom, world and viewport filtering, distinct exposure/threat sound routing and configuration defaults.

The actual optional mixins load in each client. The addon Settings screen contains exactly one Hunter Alert Settings button; the smoke test clicks it, checks our settings screen opens, and checks Back returns to the addon screen. The registered Mod Menu entrypoint and settings factory also open our screen. Tested Mod Menu versions: 17.0.0 (1.21.11), 18.0.0-beta.1 (26.1.2), 20.0.1 (26.2).

The former temporary Xaero waypoint layer was removed. The new overlay hooks the addon's full-screen map render pass and uses its viewport coordinates. Installing the addon suppresses Hunter Alert's in-world bearing labels even for existing configurations. `verification/earthmc-map-overlay.png` is a synthetic overlay fixture; `verification/addon-settings-entry.png` shows the real addon Settings screen. Both were visually inspected. Authenticated live map panning and listening to sounds in normal gameplay remain unverified; sound selection and pitch are tested programmatically. An initial standalone smoke run exposed eager loading of the addon settings class in the development probe; optional settings probes were isolated into separately loaded classes, and all standalone clients then passed.

## Version 0.3.0 exposure and threat markers

All three versions pass 28 tests, including exposure fill/drain/clamping/unknown pause/reset, outlaw ordering/severity colors, live-to-last-seen expiry, nearby-home-town affiliation clues for hidden hunters, different-world/far-town rejection, and marker projection. The direct HTTP fixture also checks the independent one-minute profile refresh.

Standalone and addon client smoke runs render the exposure/threat HUD and a separate public-position marker fixture. The addon run tests real Xaero waypoint objects: live/last-seen/clue labels, temporary flags, unknown altitude, dimension conversion, cross-world filtering, expiration, and preserving an existing user waypoint on cleanup. Xaero inserts at the front of its collection; the fixture checks owned references rather than assuming insertion order.

Screenshots `verification/exposure-threat-hud.png` and `verification/public-position-markers.png` were visually inspected. They are synthetic fixtures. No authenticated EarthMC multiplayer session or live map panning/waypoint display was observed. In-game bearing projection uses the configured FOV and horizontal public coordinates; target altitude and a hidden player's current location remain unknown.

## Version 0.2.0 standalone and HUD follow-up

All three versions passed client startup/UI smoke checks with only Fabric API, with Xaero Minimap/XaeroLib, and with EMC Map Addon plus its Xaero dependencies. `-PwithMinimap` and `-PwithAddon` opt into development dependencies; the default runtime is standalone. Optional HUD classes were explicitly loaded to validate mixin application. A rendered text fixture exercised the actual transformed Minecraft text method, and screenshots showed Hunter Alert beside an occupied map/info rectangle.

The placement screenshot is a synthetic obstruction fixture, not an authenticated live minimap. Unit tests cover left/right map placement, preserved free preferences and lack of space. Public EarthMC endpoints were checked for player-feed and town-spawn schema. The direct provider is tested against a loopback HTTP server; it does not require a live server for tests.

## What was exercised

* 22 tests: direct HTTP normalization, 304/outage handling, API batching and profile/outlaw parsing, HUD collision placement, plus same/straight-line/negative distances, directions, radius transitions, duplicate snapshots, below/equal/above outlaw threshold, priority, exposure transitions and timestamps, outages/staleness, approaching/away/stationary/jitter, local-player motion rejection, different-world rejection, watched toggle, manual-hunter ignore override, affiliation semantics/nearest nation town, UUID/name identity, input validation, cooldown, bounded config/history persistence, corrupt-config backup, malformed feed validation, old-connection response rejection, and exact EarthMC hostname matching.
* In each actual Fabric runtime, loopback HTTP fixtures used the real addon's SquaremapApiClient and parser, with the production mixins applied. Checked valid player normalization/world identity, HTTP 304 after clearing upstream player state, HTTP 503, malformed JSON, recovery via HTTP 304 using the last valid players, and a healthy empty feed. Also checked that the existing EarthMC API client constructor hook initialized. No reflective access was used for the feed bridge. The HUD adapter reads Xaero's minimap size through its public method.
* Actual title screen, settings, hunter/watch management, history, HUD editor, and populated HUD rendering. Each stage saved a screenshot. Settings, management and HUD screenshots were visually inspected. The displayed hunter is a synthetic fixture, not a real player's location.
* Shared non-platform Java sources are identical across all three branches. No runtime Minecraft-version switching is used.

The concise smoke evidence is in `docs/verification/`. Full JUnit XML/HTML reports are under each worktree's `build/test-results/test/` and `build/reports/tests/test/`; complete runtime logs and screenshots are under `run/smoke/`.

## Warnings inspected

No Hunter Alert compilation warnings, dependency-resolution errors, duplicate-resource errors, missing classes, or failed mixin applications remain in the final runs. The Gradle resource-expansion deprecation found during development was corrected.

Remaining environment/dependency messages: Loom's OneDrive performance warning; Xaero's development refmap warnings; 26.1.2's unobfuscated-runtime remapping warning; offline Fabric development-account authentication/Realms failures; intermittent Xaero version/Patreon checks. The feed tests intentionally log an HTTP 503 and malformed-JSON parser error. Clearing upstream map state during the fixture also schedules an upstream claims request, which is interrupted when the fixture client stops. These did not prevent the client/UI checks or clean exit.

An early run against upstream's older documented Xaero World Map pin (1.41.2) showed an upstream `attachedCamera` mixin mismatch. The final World Map 1.46.x pins resolved it without modifying or suppressing upstream mixins.

## Acceptance still outstanding

Authenticated EarthMC multiplayer play was **not** performed. Therefore this is a tested implementation/build delivery, not a claim that every live-server completion criterion has been observed. Verify exposure against the real feed, manual hunter/watch editing while connected, live outlaw/affiliation refresh behavior, notifications in normal gameplay, and extended-session behavior with an authenticated client.

Version 0.3.1 replaces temporary Xaero waypoints with a direct full-screen EarthMC map overlay. Live map display and panning still require authenticated acceptance. No reliable friend-list API was found. Standalone town proximity uses API spawn coordinates; the optional addon provider uses labelled claim bounding centres. Outlaw freshness and incomplete API sweeps inherit the addon's cache behavior. Squaremap has no per-player source timestamp through the current bridge, so an unchanged coordinate's observation time is not proof of a fresh measurement.

## Version 0.3.3 offline alert fix (2026-09-17)

`test build --offline` passed on all three targets: 1.21.11 (Java 21), 26.1.2 and 26.2 (Java 25), with 43 tests each. Seven new regression tests cover explicit offline entry/departure suppression and marker cleanup, server player-list overrides, missing/opted-out status, expired/future evidence, feed outages, informational disappearance, and offline affiliation baselines. Direct parsing distinguishes absent/null/malformed status from explicit true/false.

Recent explicit offline evidence is trusted for at most 90 seconds. The optional addon only derives offline status from freshly requested profiles with a last-online timestamp; cached online-player statistics cannot independently establish offline status. Missing profile responses discard previous status rather than refreshing it. Server player-list absence is never used as proof of logout.

No authenticated multiplayer test or new client smoke run was performed for this change. API polling and upstream caches can delay status changes, and unknown status cannot establish whether a player logged out. Runtime JARs and checksums are in `dist/` under version 0.3.3.

## Version 0.4.0 (2026-09-17)

All three targets (1.21.11 on Java 21; 26.1.2 and 26.2 on Java 25) passed 51 unit tests, distribution builds, and addon-plus-Mod-Menu client smoke checks. New regressions cover ordinary approach thresholds/toggles, high-outlaw approach wording, whitelist exclusion, local motion/stale feed rejection, rain transition/outage handling, cached global candidate thresholds, autocomplete deduplication/identity preservation, and automatic affiliation warnings.

The development UI fixture types a mixed-case prefix, clicks a name suggestion and checks the selected name and threshold-filtered global candidate list. The addon fixture verifies that the settings action is initially absent from General, clicks the Hunter Alert sidebar row, verifies the action becomes visible, opens it, and returns. Screenshots cover the manager, sidebar and synthetic rain HUD. The sidebar entry was moved directly below General after visual inspection showed that the bottom of the rail is not clickable at small GUI heights. Final logs are retained in `logs/features-0.4.0.log`; screenshots are under `run/smoke/screenshots`.

These are synthetic development checks, not an authenticated multiplayer session. Live rain packets, hunter travel, claims/wilderness status and real affiliation transitions have not been observed in-game. Weather notices deliberately do not infer claim status or remote trident equipment. Cached counts/status may lag live changes. The distribution JARs and SHA-256 checksums are under `dist/` with version 0.4.0.

## Version 0.5.0 (2026-09-18)

All three versions passed 62 unit tests and distribution builds. New coverage includes three independent observations, duplicate feeds, range entry/exit and the overall range cap, cumulative displacement, stopping/reversal, local motion, stale/cross-world data, settings/window changes, whitelist removal, observed paths, log deduplication/expiry/session clearing, layout limits over multiple window sizes/scales, clipped path geometry, and upgrade defaults preserving existing thresholds.

Addon-plus-Mod-Menu smoke checks passed on 1.21.11, 26.1.2 and 26.2. The final 1.21.11 check also passed in standalone mode; final 26.x checks used the addon. Synthetic fixtures render the compact log with two silent observations and a confirmed warning, directional path traces, and the legacy HUD after switching modes. Small-window screenshots were inspected; compact rows were shortened to keep identities/counts/distances visible. Full-map traces are verified with synthetic public positions, not an authenticated live hunter.

Final logs are saved in `logs/approach-0.5.0.log`; screenshots are in `run/smoke/screenshots/`, including `hunteralert-smoke-5.png`, `hunteralert-smoke-6.png`, and `hunteralert-legacy.png`. Runtime JARs and SHA-256 sums are under `dist/` with version 0.5.0. Authenticated multiplayer acceptance remains outstanding.

## Version 0.5.1 (2026-09-18)

All three targets passed 67 unit tests, distribution builds and addon-plus-Mod-Menu client smoke checks. Five additional regressions verify persistent rows without events, coordinate/distance updates, range and view removal, stale labeling/expiry, ellipsis fitting and short exposure messages. Synthetic screenshots verify the pinned hunter location above the bounded alert log. Logs are in `awareness-smoke.log`; screenshots are in `run/smoke/screenshots`. Authenticated multiplayer behavior has not been tested.

## Version 0.5.2 (2026-09-18)

All three targets passed 73 unit tests, distribution builds and addon-plus-Mod-Menu smoke checks. Six new tests cover complete affiliation messages across widths, long unbroken names and Unicode, explicit paragraphs, pinned rows across pages, tiny page capacities and cycling. Synthetic small-window screenshots include a long affiliation message and active flash; these were inspected for wrapping, contrast and minimap separation. Logs: `wrapping-smoke.log`. No authenticated multiplayer test was performed.

## Version 0.5.3: entry dividers

All three targets passed the existing 73 tests, distribution builds and addon-plus-Mod-Menu client smoke checks. Small-window HUD screenshots were inspected for dividers between entries without lines crossing wrapped message text. Logs: `divider-smoke.log`. No authenticated multiplayer test was performed.

## Version 0.5.4 (2026-09-28)

All three targets passed 76 tests and distribution builds. Regression coverage includes keeping the compact log out of the minimap column, naming distant affiliation changes, existing online hunter affiliation proximity, offline suppression, and local movement into hunter range between feed refreshes. Addon-plus-Mod-Menu client smoke checks passed on all three targets; HUD screenshots were inspected for placement and the fixed yellow dynmap timer. The final proximity change was then rebuilt and unit tested on all targets. Logs: hud-status-build.log and hud-status-smoke.log. No authenticated multiplayer test was performed.

