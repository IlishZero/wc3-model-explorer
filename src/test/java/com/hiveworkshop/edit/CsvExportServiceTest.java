package com.hiveworkshop.edit;

import com.hiveworkshop.model.ModelAsset;
import com.hiveworkshop.model.ModelMetadata;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExportServiceTest {

    @TempDir
    Path tempDir;

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
    void collectFlagsMissingTexturesAgainstTheRealResolutionLogic() throws Exception {
        Files.writeString(tempDir.resolve("MyTex.blp"), "fake");
        Path modelFile = tempDir.resolve("model.mdl");
        Files.writeString(modelFile, MINIMAL_MDL);
        MdlxModel model = ModelFileService.loadModel(modelFile);
        model.textures.get(0).path = "MyTex.blp";
        com.hiveworkshop.rms.parsers.mdlx.MdlxTexture missingTex = new com.hiveworkshop.rms.parsers.mdlx.MdlxTexture();
        missingTex.path = "Nope\\Never.blp";
        model.textures.add(missingTex);
        Files.write(modelFile, MdlxMdlConverter.bytesOf(
                com.hiveworkshop.rms.parsers.mdlx.MdlLoadSave.saveMdl(model)));

        ModelMetadata metadata = com.hiveworkshop.parser.ModelMetadataExtractor.extract(modelFile);
        ModelAsset asset = new ModelAsset(modelFile, Files.size(modelFile), metadata);

        List<CsvExportService.Row> rows = CsvExportService.collect(List.of(asset), tempDir);

        assertEquals(1, rows.size());
        assertEquals(List.of("Nope\\Never.blp"), rows.get(0).missingTextures());
        assertTrue(rows.get(0).textures().contains("MyTex.blp"));
        assertEquals(1, rows.get(0).sequences());
        assertEquals(1, rows.get(0).bones());
    }

    @Test
    void writeCsvUsesBomCrlfAndRfc4180Quoting() throws Exception {
        Path target = tempDir.resolve("out.csv");
        CsvExportService.Row row = new CsvExportService.Row(
                "hero, v2.mdx", "C:\\lib\\hero, v2.mdx", 2048L, 3, 1, 1, 1,
                List.of("Stand", "Death"), List.of("Textures\\a.blp"), List.of());
        String[] header = {"Name", "Path", "KB", "V", "F", "B", "S", "A", "T", "M"};

        CsvExportService.writeCsv(target, header, row == null ? List.of() : List.of(row));

        byte[] bytes = Files.readAllBytes(target);
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);

        String content = new String(bytes, StandardCharsets.UTF_8).substring(1); // strip BOM
        String[] lines = content.split("\r\n");
        assertEquals(2, lines.length);
        assertTrue(lines[1].startsWith("\"hero, v2.mdx\""));
        assertTrue(lines[1].contains("\"C:\\lib\\hero, v2.mdx\""));
        assertTrue(lines[1].endsWith(","), "empty last field still emits its separator");
    }

    @Test
    void encodeFieldQuotesOnlyWhenNecessary() {
        assertEquals("plain", CsvExportService.encodeField("plain"));
        assertEquals("\"a,b\"", CsvExportService.encodeField("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExportService.encodeField("say \"hi\""));
        assertEquals("\"line\nbreak\"", CsvExportService.encodeField("line\nbreak"));
        assertEquals("", CsvExportService.encodeField(null));
    }
}
