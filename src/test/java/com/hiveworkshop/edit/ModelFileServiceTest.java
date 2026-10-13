package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdlxSequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelFileServiceTest {

    @TempDir
    Path tempDir;

    /** Two sequences so a delete can be exercised against real content. */
    private static final String MINIMAL_MDL = """
            Version {
                FormatVersion 800,
            }
            Sequences 2 {
                Anim "Stand" {
                    MinimumExtent { -1.000000, -1.000000, -1.000000 },
                    MaximumExtent { 1.000000, 1.000000, 1.000000 },
                    BoundsRadius 1.000000,
                }
                Anim "Walk" {
                    MinimumExtent { -1.000000, -1.000000, -1.000000 },
                    MaximumExtent { 1.000000, 1.000000, 1.000000 },
                    BoundsRadius 1.000000,
                }
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
    void writeBackAppliesSequenceEditsAndBacksUpOriginal() throws Exception {
        Path file = tempDir.resolve("model.mdl");
        Files.writeString(file, MINIMAL_MDL);

        MdlxModel model = ModelFileService.loadModel(file);
        assertEquals(2, model.sequences.size());

        model.sequences.remove(1);
        MdlxSequence stand = model.sequences.get(0);
        stand.name = "StandEdited";
        stand.rarity = 5f;
        stand.interval[0] = 0;
        stand.interval[1] = 1000;

        ModelFileService.WriteResult result = ModelFileService.writeModel(file, model);

        assertTrue(result.success(), result.message());
        assertNotNull(result.backupPath());
        assertTrue(Files.exists(result.backupPath()));
        assertTrue(result.backupPath().getFileName().toString().endsWith(".bak"));

        MdlxModel reloaded = ModelFileService.loadModel(file);
        assertEquals(1, reloaded.sequences.size());
        assertEquals("StandEdited", reloaded.sequences.get(0).name);
        assertEquals(5f, reloaded.sequences.get(0).rarity);
        assertEquals(0L, reloaded.sequences.get(0).interval[0]);
        assertEquals(1000L, reloaded.sequences.get(0).interval[1]);
        assertEquals(3, reloaded.geosets.get(0).vertices.length / 3);
        assertEquals(1, reloaded.geosets.get(0).faces.length / 3);
        assertEquals(1, reloaded.bones.size());
    }

    @Test
    void loadAndWriteRoundTripOnMdlFile() throws Exception {
        Path file = tempDir.resolve("model.mdl");
        Files.writeString(file, MINIMAL_MDL);

        MdlxModel model = ModelFileService.loadModel(file);
        ModelFileService.WriteResult result = ModelFileService.writeModel(file, model);

        assertTrue(result.success(), result.message());
        MdlxModel reloaded = ModelFileService.loadModel(file);
        assertEquals(2, reloaded.sequences.size());
        assertEquals("Stand", reloaded.sequences.get(0).name);
        assertEquals("Walk", reloaded.sequences.get(1).name);
    }
}
