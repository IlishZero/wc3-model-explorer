package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTexture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexturePathServiceTest {

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
    void batchReplaceRewritesMatchingPathsCaseInsensitively() throws Exception {
        Path file = tempDir.resolve("model.mdl");
        Files.writeString(file, MINIMAL_MDL);

        MdlxModel model = ModelFileService.loadModel(file);
        model.textures.get(0).path = "Textures\\BuildingA.blp";
        MdlxTexture imported = new MdlxTexture();
        imported.path = "war3mapImported\\buildingA_old.blp";
        model.textures.add(imported);

        int changed = TexturePathService.replaceAll(model, "textures", "MyPack");

        assertEquals(1, changed);
        assertEquals("MyPack\\BuildingA.blp", model.textures.get(0).path);
        assertEquals("war3mapImported\\buildingA_old.blp", model.textures.get(1).path);

        assertTrue(ModelFileService.writeModel(file, model).success(), "write-back failed");
        MdlxModel reloaded = ModelFileService.loadModel(file);
        assertEquals(2, reloaded.textures.size());
        assertEquals("MyPack\\BuildingA.blp", reloaded.textures.get(0).path);
        assertEquals("war3mapImported\\buildingA_old.blp", reloaded.textures.get(1).path);
    }

    @Test
    void emptyFindChangesNothing() throws Exception {
        Path file = tempDir.resolve("model.mdl");
        Files.writeString(file, MINIMAL_MDL);

        MdlxModel model = ModelFileService.loadModel(file);
        model.textures.get(0).path = "Textures\\A.blp";

        assertEquals(0, TexturePathService.replaceAll(model, "", "X"));
        assertEquals(0, TexturePathService.replaceAll(model, null, "X"));
        assertEquals("Textures\\A.blp", model.textures.get(0).path);
    }
}
