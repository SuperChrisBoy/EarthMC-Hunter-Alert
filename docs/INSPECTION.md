# Initial inspection

The supplied workspace was empty (including no Git repository). There were no existing Loom, config, HUD, keybind, HTTP or map systems to replace.

Inspected upstream JR1258/EarthMC-Map-Addon, 1.4.5, commits a2b8c00 (1.21.11), 06958d4 (26.1.x), 6fc8aee (26.2). The older branch uses Yarn 1.21.11+build.4, Loom 1.14, Loader 0.18.1 and Fabric API 0.139.5. The 26.x branches use unobfuscated Mojang names and Loom 1.17.9. All addon source is under net.townymap.

Reuse points: TownyMapMod.getApiClient(), SquaremapApiClient.getAllPlayers()/getTowns(world), tickPlayers(), TownyMapMod.outlawTrustedCounts() (batched reverse town index, ten-minute cache), onlinePlayerStats(), playerWorldResolved(), isOnEarthMcServer(), and town/nation metadata. No second HTTP client or squaremap poller is introduced. tickPlayers shares upstream's in-flight gate and refresh interval.

There is no public feed-health event. A version-pinned Mixin compatibility bridge observes completion of the existing request and publishes an immutable result through EmcMapAddonBridge. It does not reflect into private fields, replace requests, or change their results. This small hook is confined to platform/mixin; replacing it with an upstream event later leaves detection unchanged. Required injections fail explicitly on incompatible addon releases.

Upstream's server check uses a substring; Hunter Alert also validates an exact earthmc.net host or subdomain. Upstream exposes no reliable friend list, so no friend-filter control is offered. No stable map-layer extension API was found; optional map rendering is represented by a separate overlay interface.
