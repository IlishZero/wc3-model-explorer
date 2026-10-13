package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter2;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTexture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One-stop model slimming (roadmap P2-15): remove texture entries nothing
 * references, and optionally strip cameras / event objects / collision shapes.
 * Materials are intentionally never removed (shared, and harmless when
 * unreferenced — same ruling as the Geoset manager). Writes back through the
 * shared {@link ModelFileService} safety net.
 *
 * <p>Not included by design: keyframe deduplication — the generic timeline
 * structure makes a safe "identical consecutive keys" merge error-prone, and
 * the size wins above cover the common cases. Documented as roadmap leftover.
 */
public final class ModelSlimmingService {

    public record Options(boolean removeUnusedTextures, boolean stripCameras,
                          boolean stripEventObjects, boolean stripCollisionShapes) {}

    public record Report(int texturesBefore, int texturesAfter, int removedTextures,
                         int camerasRemoved, int eventObjectsRemoved, int collisionShapesRemoved,
                         long sizeBeforeBytes, long sizeAfterBytes, Path backupPath) {}

    public record SlimResult(boolean success, String message, Report report) {}

    /** In-memory mutation result: counts are final, {@code sizeAfterBytes} is not known yet. */
    public record Plan(MdlxModel model, int texturesBefore, int texturesAfter, int removedTextures,
                       int camerasRemoved, int eventObjectsRemoved, int collisionShapesRemoved,
                       long sizeBeforeBytes) {}

    private ModelSlimmingService() {}

    /**
     * Loads the model and applies the options in memory only — no backup, no
     * write. Returns {@code null} when nothing would change ("nothing-to-do").
     * Pair with {@link ModelFileService#writeModel} (usually behind a
     * {@link com.hiveworkshop.edit.ui.WritePreview} confirmation).
     */
    public static Plan plan(Path file, Options options) throws IOException {
        MdlxModel model = ModelFileService.loadModel(file);

        int texturesBefore = model.textures.size();
        int camerasBefore = model.cameras.size();
        int eventsBefore = model.eventObjects.size();
        int collisionsBefore = model.collisionShapes.size();

        int removedTextures = 0;
        if (options.removeUnusedTextures()) {
            keepOnlyReferencedTextures(model);
            removedTextures = texturesBefore - model.textures.size();
        }
        if (options.stripCameras()) model.cameras.clear();
        if (options.stripEventObjects()) model.eventObjects.clear();
        if (options.stripCollisionShapes()) model.collisionShapes.clear();

        boolean nothingDone = model.textures.size() == texturesBefore
                && model.cameras.size() == camerasBefore
                && model.eventObjects.size() == eventsBefore
                && model.collisionShapes.size() == collisionsBefore;
        if (nothingDone) return null;

        return new Plan(model, texturesBefore, model.textures.size(), removedTextures,
                camerasBefore - model.cameras.size(),
                eventsBefore - model.eventObjects.size(),
                collisionsBefore - model.collisionShapes.size(),
                Files.size(file));
    }

    public static SlimResult slim(Path file, Options options) {
        long sizeBefore;
        try {
            sizeBefore = Files.size(file);
        } catch (IOException ex) {
            return new SlimResult(false, ex.getMessage(), null);
        }
        try {
            Plan plan = plan(file, options);
            if (plan == null) {
                return new SlimResult(false, "nothing-to-do", null);
            }

            ModelFileService.WriteResult write = ModelFileService.writeModel(file, plan.model());
            if (!write.success()) {
                return new SlimResult(false, write.message(), null);
            }
            Report report = new Report(plan.texturesBefore(), plan.texturesAfter(), plan.removedTextures(),
                    plan.camerasRemoved(), plan.eventObjectsRemoved(), plan.collisionShapesRemoved(),
                    sizeBefore, Files.size(file), write.backupPath());
            return new SlimResult(true, null, report);
        } catch (Exception ex) {
            return new SlimResult(false, ex.getMessage(), null);
        }
    }

    /**
     * Texture ids referenced by any material layer or particle-2 emitter stay;
     * ribbon and classic particle emitters point at materials, which are all
     * kept, so they add no texture references.
     */
    private static void keepOnlyReferencedTextures(MdlxModel model) {
        Set<Integer> referenced = new HashSet<>();
        for (var material : model.materials) {
            if (material == null) continue;
            for (var layer : material.layers) {
                if (layer != null) referenced.add(layer.textureId);
            }
        }
        for (MdlxParticleEmitter2 emitter : model.particleEmitters2) {
            if (emitter != null) referenced.add(emitter.textureId);
        }
        List<MdlxTexture> kept = new ArrayList<>();
        for (int i = 0; i < model.textures.size(); i++) {
            if (referenced.contains(i)) kept.add(model.textures.get(i));
        }
        model.textures.clear();
        model.textures.addAll(kept);
    }
}
