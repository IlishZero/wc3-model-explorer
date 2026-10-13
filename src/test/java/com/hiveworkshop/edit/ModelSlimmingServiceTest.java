package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlxCamera;
import com.hiveworkshop.rms.parsers.mdlx.MdlxCollisionShape;
import com.hiveworkshop.rms.parsers.mdlx.MdlxEventObject;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdlxTexture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSlimmingServiceTest {

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
    void slimRemovesUnreferencedTexturesAndStripsExtras() throws Exception {
        Path file = tempDir.resolve("model.mdl");
        Files.writeString(file, MINIMAL_MDL);

        MdlxModel model = ModelFileService.loadModel(file);
        MdlxTexture unused = new MdlxTexture();
        unused.path = "Unused\\NeverReferenced.blp";
        model.textures.add(unused);
        model.cameras.add(new MdlxCamera());
        model.eventObjects.add(new MdlxEventObject());
        model.collisionShapes.add(new MdlxCollisionShape());
        Files.write(file, MdlxMdlConverter.bytesOf(
                com.hiveworkshop.rms.parsers.mdlx.MdlLoadSave.saveMdl(model)));
        long sizeBefore = Files.size(file);

        var result = ModelSlimmingService.slim(file, new ModelSlimmingService.Options(
                true, true, true, true));

        assertTrue(result.success(), result.message());
        var report = result.report();
        assertEquals(2, report.texturesBefore());
        assertEquals(1, report.texturesAfter());
        assertEquals(1, report.removedTextures());
        assertEquals(1, report.camerasRemoved());
        assertEquals(1, report.eventObjectsRemoved());
        assertEquals(1, report.collisionShapesRemoved());
        assertTrue(report.backupPath().getFileName().toString().endsWith(".bak"));

        MdlxModel reloaded = ModelFileService.loadModel(file);
        assertEquals(1, reloaded.textures.size());
        assertEquals(0, reloaded.cameras.size());
        assertEquals(0, reloaded.eventObjects.size());
        assertEquals(0, reloaded.collisionShapes.size());
        assertEquals(3, reloaded.geosets.get(0).vertices.length / 3);
        assertTrue(Files.size(file) <= sizeBefore);
    }

    @Test
    void slimReportsNothingToDoWhenAlreadyClean() throws Exception {
        Path file = tempDir.resolve("model.mdl");
        Files.writeString(file, MINIMAL_MDL);

        var result = ModelSlimmingService.slim(file, new ModelSlimmingService.Options(
                true, true, true, true));

        assertTrue(result.success() || "nothing-to-do".equals(result.message()));
        if ("nothing-to-do".equals(result.message())) {
            assertEquals(MINIMAL_MDL, Files.readString(file));
        }
    }
}
