package com.hiveworkshop.model;

import com.hiveworkshop.i18n.Messages;

/**
 * Advanced-filter option: keep all models, only models whose textures all
 * resolve, or only models with at least one missing texture.
 */
public enum TextureHealthFilter {
    ALL("texHealth.all"),
    COMPLETE("texHealth.complete"),
    MISSING("texHealth.missing");

    private final String key;

    TextureHealthFilter(String key) {
        this.key = key;
    }

    public boolean allows(ModelAsset asset) {
        TextureHealth health = asset.texHealth();
        return switch (this) {
            case ALL -> true;
            case COMPLETE -> health != null && health.complete();
            case MISSING -> health != null && health.missing() > 0;
        };
    }

    @Override
    public String toString() {
        return Messages.get(key);
    }
}
