package net.earthmc.hunteralert.data;

import net.earthmc.hunteralert.model.Models.Snapshot;

/** Implementations consume shared snapshots; future standalone providers can implement this contract. */
public interface EarthMcDataProvider {
    Snapshot snapshot();
}
