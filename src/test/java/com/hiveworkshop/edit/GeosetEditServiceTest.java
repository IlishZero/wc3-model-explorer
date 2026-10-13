package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeosetEditServiceTest {

    @TempDir
    Path tempDir;

    /** Two geosets, two materials, one geoset animation bound to geoset 0, two bones. */
    private static final String TWO_GEOSET_MDL = """
            Version {
                FormatVersion 800,
            }
            Sequences 1 {
                Anim "Stand" {
                    MinimumExtent { -1.000000, -1.000000, -1.000000 },
                    MaximumExtent { 1.000000, 1.000000, 1.000000 },
                    BoundsRadius 1.000000,
                }
            }
            Materials 2 {
                Material {
                    Layer {
                        FilterMode None,
                        static TextureID 0,
                    }
                }
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
            Geoset {
                Vertices 3 {
                    { -1.000000, -1.000000, 1.000000 },
                    { 1.000000, -1.000000, 1.000000 },
                    { 0.000000, 1.000000, 1.000000 },
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
                    Matrices { 1 },
                }
                MinimumExtent { -1.000000, -1.000000, 1.000000 },
                MaximumExtent { 1.000000, 1.000000, 1.000000 },
                BoundsRadius 1.414214,
                MaterialID 1,
                SelectionGroup 0,
            }
            GeosetAnim {
                GeosetId 0,
                static Alpha 1.000000,
            }
            Bone "B0" {
                ObjectId 0,
                GeosetId 0,
                GeosetAnimId None,
            }
            Bone "B1" {
                ObjectId 1,
                GeosetId 1,
                GeosetAnimId 0,
            }
            PivotPoints 2 {
                { 0.000000, 0.000000, 0.000000 },
                { 0.000000, 0.000000, 0.000000 },
            }
            """;

    @Test
    void removeGeosetCleansUpReferencesAndWritesBack() throws Exception {
        Path file = tempDir.resolve("model.mdl");
        Files.writeString(file, TWO_GEOSET_MDL);

        MdlxModel model = ModelFileService.loadModel(file);
        assertEquals(2, model.geosets.size());
        assertEquals(2, model.bones.size());
        assertEquals(1, model.geosetAnimations.size());

        GeosetEditService.removeGeosetAt(model, 0);

        assertEquals(1, model.geosets.size());
        assertEquals(0, model.geosetAnimations.size());
        assertEquals(2, model.bones.size());
        assertEquals(-1, model.bones.get(0).geosetId);
        assertEquals(0, model.bones.get(1).geosetId);
        assertEquals(-1, model.bones.get(1).geosetAnimationId);
        assertEquals(1, model.geosets.get(0).materialId);

        ModelFileService.WriteResult result = ModelFileService.writeModel(file, model);
        assertTrue(result.success(), result.message());

        MdlxModel reloaded = ModelFileService.loadModel(file);
        assertEquals(1, reloaded.geosets.size());
        assertEquals(0, reloaded.geosetAnimations.size());
        assertEquals(2, reloaded.bones.size());
        assertEquals(-1, reloaded.bones.get(0).geosetId);
        assertEquals(0, reloaded.bones.get(1).geosetId);
        assertEquals(-1, reloaded.bones.get(1).geosetAnimationId);
        assertEquals(3, reloaded.geosets.get(0).vertices.length / 3);
        assertEquals(1, reloaded.geosets.get(0).faces.length / 3);
    }
}
