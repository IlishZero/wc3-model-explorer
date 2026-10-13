package com.hiveworkshop.edit;

import com.hiveworkshop.rms.editor.model.util.FilterMode;
import com.hiveworkshop.rms.parsers.mdlx.MdlxLayer;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

/**
 * Material/layer editing primitives (P3a) with reference counting and bounds
 * validation. All methods mutate the in-memory {@link MdlxModel} only — the
 * caller owns preview + write-back through {@link ModelFileService}.
 */
public final class MaterialEditService {

    /**
     * Layer flag bits — empirically mapped from the bundled RMS jar (round-trip
     * verified). These DIFFER from the Magos MDX spec for the high bits
     * (TwoSided 0x10, NoDepthTest 0x40, NoDepthSet 0x80, Unlit 0x100).
     * Binary MDX round-trips symmetrically; for MDL text the upstream writer
     * has inconsistent Unlit/NoDepthSet mappings, so the UI disables those two
     * flags on .mdl files.
     */
    public static final int FLAG_UNSHADED = 0x1;
    public static final int FLAG_SPHERE_ENV_MAP = 0x2;
    public static final int FLAG_TWO_SIDED = 0x10;
    public static final int FLAG_NO_DEPTH_TEST = 0x40;
    public static final int FLAG_NO_DEPTH_SET = 0x80;
    public static final int FLAG_UNLIT = 0x100;

    private MaterialEditService() {}

    /** Number of geosets bound to each material id; index = material id. */
    public static int[] geosetUsage(MdlxModel model) {
        int[] usage = new int[Math.max(1, model.materials.size())];
        for (var geoset : model.geosets) {
            if (geoset == null) continue;
            int id = (int) geoset.materialId;
            if (id >= 0 && id < usage.length) usage[id]++;
        }
        return usage;
    }

    public static void validateLayer(MdlxModel model, int materialIdx, int layerIdx) {
        if (materialIdx < 0 || materialIdx >= model.materials.size()) {
            throw new IllegalArgumentException("material " + materialIdx + " out of range");
        }
        var layers = model.materials.get(materialIdx).layers;
        if (layerIdx < 0 || layerIdx >= layers.size()) {
            throw new IllegalArgumentException("layer " + layerIdx + " out of range");
        }
    }

    public static void setLayerTextureId(MdlxModel model, int materialIdx, int layerIdx, int textureId) {
        validateLayer(model, materialIdx, layerIdx);
        if (textureId < 0 || textureId >= model.textures.size()) {
            throw new IllegalArgumentException("texture " + textureId + " out of range (0.."
                    + (model.textures.size() - 1) + ")");
        }
        model.materials.get(materialIdx).layers.get(layerIdx).textureId = textureId;
    }

    public static void setLayerFilterMode(MdlxModel model, int materialIdx, int layerIdx, FilterMode mode) {
        validateLayer(model, materialIdx, layerIdx);
        if (mode == null) throw new IllegalArgumentException("null filter mode");
        model.materials.get(materialIdx).layers.get(layerIdx).filterMode = mode;
    }

    public static void setLayerAlpha(MdlxModel model, int materialIdx, int layerIdx, float alpha) {
        validateLayer(model, materialIdx, layerIdx);
        model.materials.get(materialIdx).layers.get(layerIdx).alpha = Math.max(0f, Math.min(1f, alpha));
    }

    public static void setLayerFlags(MdlxModel model, int materialIdx, int layerIdx, int flags) {
        validateLayer(model, materialIdx, layerIdx);
        model.materials.get(materialIdx).layers.get(layerIdx).flags = flags;
    }

    /** Appends a fresh layer (FilterMode NONE, texture 0, alpha 1) to the material. */
    public static void addLayer(MdlxModel model, int materialIdx) {
        validateLayer(model, materialIdx, 0);
        MdlxLayer layer = new MdlxLayer();
        layer.filterMode = FilterMode.NONE;
        layer.textureId = 0;
        layer.alpha = 1f;
        model.materials.get(materialIdx).layers.add(layer);
    }

    /** Removes a layer; a material must always keep at least one. */
    public static void removeLayer(MdlxModel model, int materialIdx, int layerIdx) {
        validateLayer(model, materialIdx, layerIdx);
        var layers = model.materials.get(materialIdx).layers;
        if (layers.size() <= 1) throw new IllegalArgumentException("material keeps at least one layer");
        layers.remove(layerIdx);
    }
}
