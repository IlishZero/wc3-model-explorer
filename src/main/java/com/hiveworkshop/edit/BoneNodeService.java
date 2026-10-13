package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlxAttachment;
import com.hiveworkshop.rms.parsers.mdlx.MdlxGenericObject;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Bone/node editing primitives (P3b) operating on the shared object-id space
 * of bones, helpers and attachments. Mutations are in-memory only; the caller
 * owns preview + write-back through {@link ModelFileService}.
 *
 * Bone deletion is deliberately NOT provided (reference cleanup across
 * geosets/matrix groups is a separate project) — attachment removal is safe
 * and offered instead.
 */
public final class BoneNodeService {

    /** Billboard bits — empirically mapped from the bundled RMS jar (MDL round-trip verified). */
    public static final int FLAG_BILLBOARDED = 0x8;
    public static final int FLAG_BILLBOARDED_LOCK_X = 0x10;
    public static final int FLAG_BILLBOARDED_LOCK_Y = 0x20;
    public static final int FLAG_BILLBOARDED_LOCK_Z = 0x40;
    /** Type bit RMS always sets on bones; must be preserved when toggling billboard flags. */
    public static final int BONE_BASE_BITS = 0x100;

    private BoneNodeService() {}

    /** One node of the shared id space. {@code object} is the live, editable instance. */
    public record NodeRef(String kind, int index, int objectId, String name, int parentId,
                          MdlxGenericObject object) {}

    public static List<NodeRef> collectNodes(MdlxModel model) {
        List<NodeRef> nodes = new ArrayList<>();
        for (int i = 0; i < model.bones.size(); i++) {
            nodes.add(ref("Bone", i, model.bones.get(i)));
        }
        for (int i = 0; i < model.helpers.size(); i++) {
            nodes.add(ref("Helper", i, model.helpers.get(i)));
        }
        for (int i = 0; i < model.attachments.size(); i++) {
            nodes.add(ref("Attachment", i, model.attachments.get(i)));
        }
        return nodes;
    }

    private static NodeRef ref(String kind, int index, MdlxGenericObject object) {
        return new NodeRef(kind, index, object.objectId, object.name, object.parentId, object);
    }

    public static NodeRef findByObjectId(MdlxModel model, int objectId) {
        for (NodeRef node : collectNodes(model)) {
            if (node.objectId() == objectId) return node;
        }
        return null;
    }

    /** True when making {@code node} a child of {@code newParentId} would create a cycle. */
    public static boolean wouldCycle(MdlxModel model, NodeRef node, int newParentId) {
        if (newParentId == node.objectId()) return true;
        NodeRef cursor = newParentId < 0 ? null : findByObjectId(model, newParentId);
        while (cursor != null) {
            if (cursor.objectId() == node.objectId()) return true;
            cursor = cursor.parentId() < 0 ? null : findByObjectId(model, cursor.parentId());
        }
        return false;
    }

    public static void setParent(MdlxModel model, NodeRef node, int newParentId) {
        if (wouldCycle(model, node, newParentId)) {
            throw new IllegalArgumentException("re-parenting would create a cycle");
        }
        node.object().parentId = newParentId;
    }

    /** Pivot points live in a model-level list indexed by object id (PIVT chunk). */
    public static float[] pivotOf(MdlxModel model, int objectId) {
        if (objectId < 0 || objectId >= model.pivotPoints.size()) return new float[] { 0f, 0f, 0f };
        return model.pivotPoints.get(objectId);
    }

    public static void setPivot(MdlxModel model, int objectId, float x, float y, float z) {
        while (model.pivotPoints.size() <= objectId) {
            model.pivotPoints.add(new float[] { 0f, 0f, 0f });
        }
        float[] pivot = model.pivotPoints.get(objectId);
        pivot[0] = x;
        pivot[1] = y;
        pivot[2] = z;
    }

    /** Next free object id across bones, helpers and attachments. */
    public static int nextObjectId(MdlxModel model) {
        int max = -1;
        for (NodeRef node : collectNodes(model)) max = Math.max(max, node.objectId());
        return max + 1;
    }

    public static MdlxAttachment addAttachment(MdlxModel model, String name, int parentId) {
        MdlxAttachment attachment = new MdlxAttachment();
        attachment.objectId = nextObjectId(model);
        attachment.name = name;
        attachment.parentId = parentId;
        attachment.path = "";
        attachment.attachmentId = 0;
        model.attachments.add(attachment);
        setPivot(model, attachment.objectId, 0f, 0f, 0f);
        return attachment;
    }

    /** Removes an attachment; its children become roots (pivot slot stays — harmless). */
    public static void removeAttachment(MdlxModel model, int index) {
        MdlxAttachment removed = model.attachments.get(index);
        model.attachments.remove(index);
        for (NodeRef node : collectNodes(model)) {
            if (node.parentId() == removed.objectId) {
                node.object().parentId = -1;
            }
        }
    }
}
