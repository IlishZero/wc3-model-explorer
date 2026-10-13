package com.hiveworkshop.edit;

import com.hiveworkshop.model.ModelAsset;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports the currently visible (filtered) asset list as an Excel-friendly
 * CSV: UTF-8 with BOM, CRLF lines, RFC-4180 quoting. The missing-texture
 * column uses the same resolution logic as the renderer (see
 * {@link TextureCheckService}), so "missing" means "will render blank".
 */
public final class CsvExportService {

    /** One flattened model row; texture/animation lists join with ";". */
    public record Row(String fileName, String path, long sizeBytes,
                      int vertices, int polygons, int bones, int sequences,
                      List<String> animations, List<String> textures, List<String> missingTextures) {}

    private CsvExportService() {}

    /**
     * Builds rows for the given assets. Counts and names come from the asset's
     * cached metadata; the missing-texture column needs a model parse per file,
     * so expect this to take a moment on large lists.
     */
    public static List<Row> collect(List<ModelAsset> assets, Path rootDir) {
        List<Row> rows = new ArrayList<>(assets.size());
        for (ModelAsset asset : assets) {
            List<String> missing = List.of();
            try {
                MdlxModel model = ModelFileService.loadModel(asset.path());
                Path modelDir = asset.path().toAbsolutePath().getParent();
                missing = TextureCheckService.check(model, modelDir, rootDir).stream()
                        .filter(s -> !s.found())
                        .map(TextureCheckService.TextureStatus::path)
                        .toList();
            } catch (Exception ignored) {
                // unreadable model: report empty missing list, metadata still exported
            }
            var meta = asset.metadata();
            rows.add(new Row(
                    asset.fileName(),
                    asset.path().toAbsolutePath().toString(),
                    asset.fileSizeBytes(),
                    meta.vertexCount(),
                    meta.polygonCount(),
                    meta.boneCount(),
                    meta.sequenceCount(),
                    meta.animationNames(),
                    meta.texturePaths(),
                    missing));
        }
        return rows;
    }

    /** Writes header + rows as UTF-8 (with BOM) CSV, CRLF-terminated. */
    public static void writeCsv(Path target, String[] header, List<Row> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write('\ufeff'); // BOM so Excel detects UTF-8
            writer.write(encodeRow(header));
            for (Row row : rows) {
                writer.write(encodeRow(new String[] {
                        row.fileName(),
                        row.path(),
                        String.valueOf(row.sizeBytes() / 1024),
                        String.valueOf(row.vertices()),
                        String.valueOf(row.polygons()),
                        String.valueOf(row.bones()),
                        String.valueOf(row.sequences()),
                        String.join(";", row.animations()),
                        String.join(";", row.textures()),
                        String.join(";", row.missingTextures()),
                }));
            }
        }
    }

    static String encodeRow(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(encodeField(fields[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    static String encodeField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
