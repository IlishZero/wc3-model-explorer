package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlLoadSave;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdxLoadSave;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdlxMdlConverterTest {

    @TempDir
    Path tempDir;

    /** Minimal but structurally complete classic MDL: 1 triangle, 1 bone, 1 sequence. */
    private static final String MINIMAL_MDL = """
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
    void mdlToMdxProducesParseableModelWithSameCounts() throws Exception {
        Path mdl = write("triangle.mdl", MINIMAL_MDL);

        MdlxMdlConverter.ConversionResult result = MdlxMdlConverter.convert(mdl);

        assertEquals(MdlxMdlConverter.Status.CONVERTED, result.status(), result.message());
        assertNotNull(result.target());
        assertTrue(Files.exists(result.target()));

        MdlxModel model = loadMdx(result.target());
        assertEquals(1, model.geosets.size());
        assertEquals(3, model.geosets.get(0).vertices.length / 3);
        assertEquals(1, model.geosets.get(0).faces.length / 3);
        assertEquals(1, model.bones.size());
        assertEquals(1, model.sequences.size());
    }

    @Test
    void mdxToMdlRoundTripKeepsCounts() throws Exception {
        Path mdl = write("triangle.mdl", MINIMAL_MDL);
        Path mdx = MdlxMdlConverter.convert(mdl).target();

        Path roundTripDir = tempDir.resolve("roundtrip");
        Files.createDirectories(roundTripDir);
        Path mdxCopy = roundTripDir.resolve("triangle.mdx");
        Files.copy(mdx, mdxCopy);

        MdlxMdlConverter.ConversionResult result = MdlxMdlConverter.convert(mdxCopy);

        assertEquals(MdlxMdlConverter.Status.CONVERTED, result.status(), result.message());
        MdlxModel model = new MdlxModel();
        MdlLoadSave.loadMdl(model, ByteBuffer.wrap(Files.readAllBytes(result.target())));
        assertEquals(3, model.geosets.get(0).vertices.length / 3);
        assertEquals(1, model.geosets.get(0).faces.length / 3);
        assertEquals(1, model.bones.size());
        assertEquals(1, model.sequences.size());
    }

    @Test
    void existingTargetIsNeverOverwritten() throws Exception {
        Path mdl = write("triangle.mdl", MINIMAL_MDL);
        Path existingMdx = tempDir.resolve("triangle.mdx");
        Files.write(existingMdx, new byte[] { 1, 2, 3 });

        MdlxMdlConverter.ConversionResult result = MdlxMdlConverter.convert(mdl);

        assertEquals(MdlxMdlConverter.Status.SKIPPED_EXISTS, result.status());
        assertEquals(existingMdx, result.target());
        assertArrayEquals(new byte[] { 1, 2, 3 }, Files.readAllBytes(existingMdx));
    }

    @Test
    void unsupportedExtensionFailsWithoutWriting() throws Exception {
        Path txt = write("notes.txt", "hello");

        MdlxMdlConverter.ConversionResult result = MdlxMdlConverter.convert(txt);

        assertEquals(MdlxMdlConverter.Status.FAILED, result.status());
        assertNull(result.target());
    }

    private Path write(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static MdlxModel loadMdx(Path path) throws Exception {
        MdlxModel model = new MdlxModel();
        MdxLoadSave.loadMdx(model, ByteBuffer.wrap(Files.readAllBytes(path)));
        return model;
    }
}
