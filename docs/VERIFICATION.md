# Verification ledger

This file records actual checks, not assumed compatibility. Final cross-version results are populated after each build and smoke run.

Initial 1.21.11 core build passed 15 tests. Added regression coverage for malformed feed payloads and responses from previous connections. The first development launches identified a missing explicitly resolved nested XaeroLib dependency and an upstream `MixinGuiMap` mismatch against Xaero World Map 1.41.2. The runtime configuration was updated to the compatible current Xaero versions, with XaeroLib explicit for Loom development runs.

Live EarthMC play has not yet been verified. A synthetic HUD smoke fixture is not a live-server test.
