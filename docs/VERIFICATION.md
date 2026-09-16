# Verification ledger

Verified on Windows, 2026-09-17 (Asia/Tokyo). These results distinguish executable checks from unverified live-server acceptance.

| Branch | Minecraft tested | Java | Loom | Loader | Fabric API | Build | Unit tests | Feed bridge | Client/UI smoke |
|---|---|---|---|---|---|---|---|---|---|
| mc/1.21.11 | 1.21.11 | 21.0.12.1 | 1.14.10 | 0.18.3 | 0.140.0+1.21.11 | PASS | 18/18 | PASS | PASS |
| mc/26.1.x | 26.1.2 | 25.0.1 | 1.17.9 | 0.19.2 | 0.150.0+26.1.2 | PASS | 18/18 | PASS | PASS |
| mc/26.2 | 26.2 | 25.0.1 | 1.17.9 | 0.19.3 | 0.152.2+26.2 | PASS | 18/18 | PASS | PASS |

Every branch ran `gradlew.bat --no-daemon clean build runClient -Psmoke --warning-mode all` and exited successfully. The 26.1.x branch was built and launched on 26.1.2; earlier 26.1 patch releases were not separately launched. The 1.21.11 branch uses Yarn 1.21.11+build.4; the 26.x branches use Minecraft's unobfuscated names with no mappings dependency.

All use EMC Map Addon 1.4.5. Development runtime pins are Xaero Minimap 26.5.0 / World Map 1.46.0 on 1.21.11 and 26.1.2; Minimap 26.5.1 / World Map 1.46.1 on 26.2. XaeroLib 1.7.3 is explicit because Loom's development runtime does not automatically expose that nested JAR. These dependencies are not bundled into Hunter Alert.

## What was exercised

* 18 unit tests: same/straight-line/negative distances, directions, radius transitions, duplicate snapshots, below/equal/above outlaw threshold, priority, exposure transitions and timestamps, outages/staleness, approaching/away/stationary/jitter, local-player motion rejection, different-world rejection, watched toggle, manual-hunter ignore override, affiliation semantics/nearest nation town, UUID/name identity, input validation, cooldown, bounded config/history persistence, corrupt-config backup, malformed feed validation, old-connection response rejection, and exact EarthMC hostname matching.
* In each actual Fabric runtime, loopback HTTP fixtures used the real addon's SquaremapApiClient and parser, with the production mixins applied. Checked valid player normalization/world identity, HTTP 304 after clearing upstream player state, HTTP 503, malformed JSON, recovery via HTTP 304 using the last valid players, and a healthy empty feed. Also checked that the existing EarthMC API client constructor hook initialized. No reflective access was used.
* Actual title screen, settings, hunter/watch management, history, HUD editor, and populated HUD rendering. Each stage saved a screenshot. Settings, management and HUD screenshots were visually inspected. The displayed hunter is a synthetic fixture, not a real player's location.
* Shared non-platform Java sources are identical across all three branches. No runtime Minecraft-version switching is used.

The concise smoke evidence is in `docs/verification/`. Full JUnit XML/HTML reports are under each worktree's `build/test-results/test/` and `build/reports/tests/test/`; complete runtime logs and screenshots are under `run/smoke/`.

## Warnings inspected

No Hunter Alert compilation warnings, dependency-resolution errors, duplicate-resource errors, missing classes, or failed mixin applications remain in the final runs. The Gradle resource-expansion deprecation found during development was corrected.

Remaining environment/dependency messages: Loom's OneDrive performance warning; Xaero's development refmap warnings; 26.1.2's unobfuscated-runtime remapping warning; offline Fabric development-account authentication/Realms failures; intermittent Xaero version/Patreon checks. The feed tests intentionally log an HTTP 503 and malformed-JSON parser error. Clearing upstream map state during the fixture also schedules an upstream claims request, which is interrupted when the fixture client stops. These did not prevent the client/UI checks or clean exit.

An early run against upstream's older documented Xaero World Map pin (1.41.2) showed an upstream `attachedCamera` mixin mismatch. The final World Map 1.46.x pins resolved it without modifying or suppressing upstream mixins.

## Acceptance still outstanding

Authenticated EarthMC multiplayer play was **not** performed. Therefore this is a tested implementation/build delivery, not a claim that every live-server completion criterion has been observed. Verify exposure against the real feed, manual hunter/watch editing while connected, live outlaw/affiliation refresh behavior, notifications in normal gameplay, and extended-session behavior with an authenticated client.

Direct Xaero marker layers remain deferred behind `ThreatMapLayer`, as the initial-implementation allowance permits. No reliable friend-list API was found. Town proximity currently uses labelled claim bounding centres; upstream does not expose spawn/home coordinates in its shared public town snapshot. Outlaw freshness and incomplete API sweeps inherit the addon's cache behavior. Squaremap has no per-player source timestamp through the current bridge, so an unchanged coordinate's observation time is not proof of a fresh measurement.
