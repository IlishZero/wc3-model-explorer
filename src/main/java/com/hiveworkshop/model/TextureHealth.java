package com.hiveworkshop.model;

/**
 * Resolution outcome for a model's texture references, computed at scan time
 * through {@code GameDataSource.resolveTextureSource}. Any reachable source
 * (DISK / CASC / MPQ / MAP / REPLACEABLE) counts as found; only MISSING counts
 * against the model. {@code null} on an asset means "not computed" (HD models,
 * parse errors, models with no texture references).
 */
public record TextureHealth(int total, int missing) {

    public boolean complete() {
        return missing == 0;
    }

    public int found() {
        return total - missing;
    }
}
