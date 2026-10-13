package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlxGeosetAnimation;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Geoset removal with reference cleanup. Deleting a geoset invalidates three
 * index spaces that point at geosets (or at geoset animations bound to them):
 *
 * - `MdlxGeosetAnimation.geosetId` → geosets list: animations bound to the
 *   removed geoset are deleted, later ones are re-indexed;
 * - `MdlxBone.geosetId` → geosets list: bones bound to the removed geoset get
 *   -1 (no geoset), later ones are re-indexed;
 * - `MdlxBone.geosetAnimationId` → geosetAnimations list: bones bound to a
 *   deleted geoset animation get -1, later ones are re-indexed.
 *
 * Materials are intentionally left in place — they may still be shared by
 * other geosets, and an unreferenced material is harmless in-game.
 */
public final class GeosetEditService {

    private GeosetEditService() {}

    public static void removeGeosetAt(MdlxModel model, int index) {
        model.geosets.remove(index);

        List<Integer> removedAnimations = new ArrayList<>();
        for (int i = model.geosetAnimations.size() - 1; i >= 0; i--) {
            MdlxGeosetAnimation ga = model.geosetAnimations.get(i);
            if (ga.geosetId == index) {
                removedAnimations.add(0, i);
                model.geosetAnimations.remove(i);
            } else if (ga.geosetId > index) {
                ga.geosetId--;
            }
        }
        for (var bone : model.bones) {
            if (bone.geosetId == index) bone.geosetId = -1;
            else if (bone.geosetId > index) bone.geosetId--;
            for (int removedAt : removedAnimations) {
                if (bone.geosetAnimationId == removedAt) bone.geosetAnimationId = -1;
                else if (bone.geosetAnimationId > removedAt) bone.geosetAnimationId--;
            }
        }
    }
}
