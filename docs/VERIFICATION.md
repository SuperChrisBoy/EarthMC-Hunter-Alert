# Verification ledger

Verified on Windows, 2026-09-17 (Asia/Tokyo). These results distinguish executable checks from unverified live-server acceptance.

| Branch | Minecraft tested | Java | Loom | Loader | Fabric API | Build | Unit tests | Feed bridge | Client/UI smoke |
|---|---|---|---|---|---|---|---|---|---|
| mc/1.21.11 | 1.21.11 | 21.0.12.1 | 1.14.10 | 0.18.3 | 0.140.0+1.21.11 | PASS | 28/28 | PASS | PASS |
| mc/26.1.x | 26.1.2 | 25.0.1 | 1.17.9 | 0.19.2 | 0.150.0+26.1.2 | PASS | 28/28 | PASS | PASS |
| mc/26.2 | 26.2 | 25.0.1 | 1.17.9 | 0.19.3 | 0.152.2+26.2 | PASS | 28/28 | PASS | PASS |

Every branch ran `gradlew.bat --no-daemon clean build runClient -Psmoke --warning-mode all` and exited successfully. The 26.1.x branch was built and launched on 26.1.2; earlier 26.1 patch releases were not separately launched. The 1.21.11 branch uses Yarn 1.21.11+build.4; the 26.x branches use Minecraft's unobfuscated names with no mappings dependency.

Optional addon tests use EMC Map Addon 1.4.5; standalone and minimap-only tests run without it. Development runtime pins are Xaero Minimap 26.5.0 / World Map 1.46.0 on 1.21.11 and 26.1.2; Minimap 26.5.1 / World Map 1.46.1 on 26.2. XaeroLib 1.7.3 is explicit because Loom's development runtime does not automatically expose that nested JAR. These dependencies are not bundled into Hunter Alert.

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

Temporary Xaero threat and affiliation waypoint layers are now implemented behind `ThreatMapLayer`; their display respects Xaero waypoint settings. No reliable friend-list API was found. Standalone town proximity uses API spawn coordinates; the optional addon provider uses labelled claim bounding centres. Outlaw freshness and incomplete API sweeps inherit the addon's cache behavior. Squaremap has no per-player source timestamp through the current bridge, so an unchanged coordinate's observation time is not proof of a fresh measurement.
