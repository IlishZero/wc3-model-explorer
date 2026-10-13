package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTexture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureCheckServiceTest {

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
    void reportsDiskFoundMissingAndReplaceable() throws Exception {
        Files.writeString(tempDir.resolve("MyTex.blp"), "fake texture bytes");
        Path modelFile = tempDir.resolve("model.mdl");
        Files.writeString(modelFile, MINIMAL_MDL);

        MdlxModel model = ModelFileService.loadModel(modelFile);
        model.textures.get(0).path = "MyTex.blp";
        MdlxTexture missing = new MdlxTexture();
        missing.path = "Nope\\NeverThere.blp";
        model.textures.add(missing);
        MdlxTexture replaceable = new MdlxTexture();
        replaceable.path = "";
        replaceable.replaceableId = 1;
        model.textures.add(replaceable);

        var statuses = TextureCheckService.check(model, tempDir, null);

        assertEquals(3, statuses.size());
        assertEquals("DISK", statuses.get(0).source());
        assertTrue(statuses.get(0).found());
        assertEquals("MyTex.blp", statuses.get(0).path());

        assertEquals("MISSING", statuses.get(1).source());
        assertFalse(statuses.get(1).found());

        assertEquals("REPLACEABLE", statuses.get(2).source());
        assertTrue(statuses.get(2).found());
    }

    @Test
    void emptyPathWithoutReplaceableIdCountsAsMissing() throws Exception {
        Path modelFile = tempDir.resolve("model.mdl");
        Files.writeString(modelFile, MINIMAL_MDL);

        MdlxModel model = ModelFileService.loadModel(modelFile);
        model.textures.get(0).path = "";

        var statuses = TextureCheckService.check(model, tempDir, null);

        assertEquals("MISSING", statuses.get(0).source());
        assertFalse(statuses.get(0).found());
    }
}
