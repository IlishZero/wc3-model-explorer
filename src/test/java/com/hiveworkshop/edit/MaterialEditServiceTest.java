package com.hiveworkshop.edit;

import com.hiveworkshop.rms.editor.model.util.FilterMode;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialEditServiceTest {

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
            Bone "Bone_Root" {
                ObjectId 0,
                GeosetId 0,
                GeosetAnimId None,
            }
            """;

    @Test
    void geosetUsageCountsPerMaterial() throws Exception {
        Path file = writeModel();
        MdlxModel model = ModelFileService.loadModel(file);
        assertEquals(1, model.materials.size());

        int[] usage = MaterialEditService.geosetUsage(model);
        assertEquals(1, usage.length);
        assertEquals(1, usage[0]);
    }

    @Test
    void layerEditsRoundTripThroughWriteBack() throws Exception {
        Path file = writeModel();
        MdlxModel model = ModelFileService.loadModel(file);

        MaterialEditService.addLayer(model, 0);
        MaterialEditService.setLayerTextureId(model, 0, 1, 0);
        MaterialEditService.setLayerFilterMode(model, 0, 1, FilterMode.ADDITIVE);
        MaterialEditService.setLayerAlpha(model, 0, 1, 0.5f);

        assertTrue(ModelFileService.writeModel(file, model).success(), "write-back failed");
        MdlxModel reloaded = ModelFileService.loadModel(file);
        assertEquals(2, reloaded.materials.get(0).layers.size());
        assertEquals(FilterMode.ADDITIVE, reloaded.materials.get(0).layers.get(1).filterMode);
        assertEquals(0, reloaded.materials.get(0).layers.get(1).textureId);
        assertEquals(0.5f, reloaded.materials.get(0).layers.get(1).alpha);
    }

    @Test
    void mdxFlagsRoundTripWithAllBits() throws Exception {
        Path file = writeMdxModel();
        MdlxModel model = ModelFileService.loadModel(file);
        int allBits = MaterialEditService.FLAG_UNSHADED | MaterialEditService.FLAG_SPHERE_ENV_MAP
                | MaterialEditService.FLAG_TWO_SIDED | MaterialEditService.FLAG_UNLIT
                | MaterialEditService.FLAG_NO_DEPTH_TEST | MaterialEditService.FLAG_NO_DEPTH_SET;

        MaterialEditService.setLayerFlags(model, 0, 0, allBits);
        assertTrue(ModelFileService.writeModel(file, model).success(), "write-back failed");

        MdlxModel reloaded = ModelFileService.loadModel(file);
        assertEquals(allBits, reloaded.materials.get(0).layers.get(0).flags);
    }

    @Test
    void mdlFlagsRoundTripWithSafeBits() throws Exception {
        // MDL text: upstream jar writer is inconsistent for Unlit/NoDepthSet —
        // the UI disables those two on .mdl; here we pin the safe subset.
        Path file = writeModel();
        MdlxModel model = ModelFileService.loadModel(file);
        int safeBits = MaterialEditService.FLAG_UNSHADED | MaterialEditService.FLAG_TWO_SIDED;

        MaterialEditService.setLayerFlags(model, 0, 0, safeBits);
        assertTrue(ModelFileService.writeModel(file, model).success(), "write-back failed");

        MdlxModel reloaded = ModelFileService.loadModel(file);
        assertEquals(safeBits, reloaded.materials.get(0).layers.get(0).flags);
    }

    @Test
    void invalidEditsAreRejected() throws Exception {
        Path file = writeModel();
        MdlxModel model = ModelFileService.loadModel(file);

        assertThrows(IllegalArgumentException.class,
                () -> MaterialEditService.setLayerTextureId(model, 0, 0, 5));
        assertThrows(IllegalArgumentException.class,
                () -> MaterialEditService.setLayerTextureId(model, 9, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MaterialEditService.setLayerAlpha(model, 0, 7, 0.5f));
        // a material always keeps at least one layer
        assertThrows(IllegalArgumentException.class,
                () -> MaterialEditService.removeLayer(model, 0, 0));
    }

    @Test
    void alphaIsClamped() throws Exception {
        Path file = writeModel();
        MdlxModel model = ModelFileService.loadModel(file);
        MaterialEditService.setLayerAlpha(model, 0, 0, 42f);
        assertEquals(1f, model.materials.get(0).layers.get(0).alpha);
    }

    private Path writeModel() throws Exception {
        Path file = tempDir.resolve("model.mdl");
        Files.writeString(file, MINIMAL_MDL);
        return file;
    }

    private Path writeMdxModel() throws Exception {
        Path mdl = writeModel();
        MdlxModel model = ModelFileService.loadModel(mdl);
        Path mdx = tempDir.resolve("model.mdx");
        Files.write(mdx, MdlxMdlConverter.bytesOf(
                com.hiveworkshop.rms.parsers.mdlx.MdxLoadSave.saveMdx(model)));
        return mdx;
    }
}
