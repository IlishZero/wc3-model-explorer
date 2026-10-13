package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoneNodeServiceTest {

    @TempDir
    Path tempDir;

    private static final String MINIMAL_MDL = """
            Version {
                FormatVersion 800,
            }
            Materials 1 {
                Material {
                    Layer {
                        FilterMode None,
                        static TextureID 0,
                    }
                }
            }
            Textures 1 {
                Bitmap {
                    Image "",
                }
            }
            Geoset {
                Vertices 3 {
                    { -1.000000, -1.000000, 0.000000 },
                    { 1.000000, -1.000000, 0.000000 },
                    { 0.000000, 1.000000, 0.000000 },
                }
                Normals 3 {
                    { 0.000000, 0.000000, 1.000000 },
                    { 0.000000, 0.000000, 1.000000 },
                    { 0.000000, 0.000000, 1.000000 },
                }
                Faces 1 3 {
                    Triangles {
                        { 0, 1, 2 },
                    }
                }
                Groups 1 1 {
                    Matrices { 0 },
                }
                MinimumExtent { -1.000000, -1.000000, 0.000000 },
                MaximumExtent { 1.000000, 1.000000, 0.000000 },
                BoundsRadius 1.414214,
                MaterialID 0,
                SelectionGroup 0,
            }
            Bone "B0" {
                ObjectId 0,
                GeosetId 0,
                GeosetAnimId None,
            }
            Bone "B1" {
                ObjectId 1,
                Parent 0,
                GeosetId 0,
                GeosetAnimId None,
            }
            PivotPoints 2 {
                { 0.000000, 0.000000, 0.000000 },
                { 1.000000, 1.000000, 1.000000 },
            }
            """;

    private Path writeMdxModel() throws Exception {
        Path mdl = tempDir.resolve("model.mdl");
        Files.writeString(mdl, MINIMAL_MDL);
        MdlxModel model = ModelFileService.loadModel(mdl);
        Path mdx = tempDir.resolve("model.mdx");
        Files.write(mdx, MdlxMdlConverter.bytesOf(
                com.hiveworkshop.rms.parsers.mdlx.MdxLoadSave.saveMdx(model)));
        return mdx;
    }

    @Test
    void cycleDetectionBlocksReParentingIntoOwnSubtree() throws Exception {
        Path file = writeMdxModel();
        MdlxModel model = ModelFileService.loadModel(file);
        var b0 = BoneNodeService.findByObjectId(model, 0);
        var b1 = BoneNodeService.findByObjectId(model, 1);

        // B0 under B1 would make B1's parent its own descendant
        assertTrue(BoneNodeService.wouldCycle(model, b0, 1));
        assertTrue(BoneNodeService.wouldCycle(model, b0, 0)); // itself
        assertTrue(!BoneNodeService.wouldCycle(model, b1, 0)); // B1 under B0 is already the shape

        assertThrows(IllegalArgumentException.class, () -> BoneNodeService.setParent(model, b0, 1));
    }

    @Test
    void reParentAndPivotRoundTripThroughMdx() throws Exception {
        Path file = writeMdxModel();
        MdlxModel model = ModelFileService.loadModel(file);
        var b1 = BoneNodeService.findByObjectId(model, 1);

        BoneNodeService.setParent(model, b1, -1); // detach from B0
        BoneNodeService.setPivot(model, 1, 5.5f, -2f, 3.25f);
        assertTrue(ModelFileService.writeModel(file, model).success(), "write-back failed");

        MdlxModel reloaded = ModelFileService.loadModel(file);
        assertEquals(-1, reloaded.bones.get(1).parentId);
        float[] pivot = BoneNodeService.pivotOf(reloaded, 1);
        assertEquals(5.5f, pivot[0]);
        assertEquals(-2f, pivot[1]);
        assertEquals(3.25f, pivot[2]);
    }

    @Test
    void billboardFlagsPreserveBoneBaseBits() throws Exception {
        Path file = writeMdxModel();
        MdlxModel model = ModelFileService.loadModel(file);
        var b0 = BoneNodeService.findByObjectId(model, 0);

        b0.object().flags = BoneNodeService.BONE_BASE_BITS;
        b0.object().flags |= BoneNodeService.FLAG_BILLBOARDED | BoneNodeService.FLAG_BILLBOARDED_LOCK_Z;
        assertTrue(ModelFileService.writeModel(file, model).success(), "write-back failed");

        MdlxModel reloaded = ModelFileService.loadModel(file);
        int flags = reloaded.bones.get(0).flags;
        assertTrue((flags & BoneNodeService.FLAG_BILLBOARDED) != 0);
        assertTrue((flags & BoneNodeService.FLAG_BILLBOARDED_LOCK_Z) != 0);
        assertTrue((flags & BoneNodeService.BONE_BASE_BITS) != 0);
        assertTrue((flags & BoneNodeService.FLAG_BILLBOARDED_LOCK_X) == 0);
    }

    @Test
    void attachmentAddRemoveRoundTripsAndReRootsChildren() throws Exception {
        Path file = writeMdxModel();
        MdlxModel model = ModelFileService.loadModel(file);

        var attachment = BoneNodeService.addAttachment(model, "WeaponRef", 0);
        assertEquals(2, attachment.objectId); // max id (1) + 1
        assertEquals(3, BoneNodeService.pivotOf(model, 2).length);

        assertTrue(ModelFileService.writeModel(file, model).success(), "write-back failed");
        MdlxModel reloaded = ModelFileService.loadModel(file);
        assertEquals(1, reloaded.attachments.size());
        assertEquals("WeaponRef", reloaded.attachments.get(0).name);
        assertEquals(0, reloaded.attachments.get(0).parentId);

        // remove → gone; re-adding reuses the freed object id (max+1 = 2)
        BoneNodeService.removeAttachment(reloaded, 0);
        assertNull(BoneNodeService.findByObjectId(reloaded, 2));
        var again = BoneNodeService.addAttachment(reloaded, "Again", -1);
        assertEquals(2, again.objectId);
        assertNotNull(BoneNodeService.findByObjectId(reloaded, 2));
    }
}
