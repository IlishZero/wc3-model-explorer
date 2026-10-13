package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlLoadSave;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdxLoadSave;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Stateless MDX↔MDL converter on top of the bundled RMS parsing library.
 *
 * Every conversion round-trips the produced bytes through the parser and
 * compares element counts (vertices, faces, bones, sequences) with the
 * source before anything is written to disk, so a broken serializer can
 * never silently replace a model with a degraded one.
 */
public final class MdlxMdlConverter {

    public enum Status { CONVERTED, SKIPPED_EXISTS, FAILED }

    /**
     * @param target null when nothing was written (unsupported input, parse
     *               failure, or an existing target file that must not be touched)
     */
    public record ConversionResult(Path source, Path target, Status status, String message) {}

    private MdlxMdlConverter() {}

    /** Converts a .mdx/.mdl file to the other format, written next to the source. */
    public static ConversionResult convert(Path source) {
        String ext = extension(source);
        boolean fromMdx = "mdx".equals(ext);
        if (!fromMdx && !"mdl".equals(ext)) {
            return new ConversionResult(source, null, Status.FAILED, "Unsupported file extension ." + ext);
        }
        Path target = siblingWithFlippedExtension(source, fromMdx);
        try {
            if (Files.exists(target)) {
                return new ConversionResult(source, target, Status.SKIPPED_EXISTS,
                        "Target already exists: " + target.getFileName());
            }

            byte[] bytes = Files.readAllBytes(source);
            MdlxModel model = new MdlxModel();
            if (fromMdx) {
                MdxLoadSave.loadMdx(model, ByteBuffer.wrap(bytes));
            } else {
                MdlLoadSave.loadMdl(model, ByteBuffer.wrap(bytes));
            }
            long[] sourceCounts = counts(model);

            byte[] out = fromMdx
                    ? bytesOf(MdlLoadSave.saveMdl(model))
                    : bytesOf(MdxLoadSave.saveMdx(model));

            MdlxModel roundTrip = new MdlxModel();
            if (fromMdx) {
                MdlLoadSave.loadMdl(roundTrip, ByteBuffer.wrap(out));
            } else {
                MdxLoadSave.loadMdx(roundTrip, ByteBuffer.wrap(out));
            }
            long[] targetCounts = counts(roundTrip);
            if (!Arrays.equals(sourceCounts, targetCounts)) {
                return new ConversionResult(source, null, Status.FAILED,
                        "Consistency check failed: source " + Arrays.toString(sourceCounts)
                                + " vs converted " + Arrays.toString(targetCounts)
                                + " [vertices, faces, bones, sequences]");
            }

            Files.write(target, out);
            return new ConversionResult(source, target, Status.CONVERTED, null);
        } catch (Exception ex) {
            return new ConversionResult(source, null, Status.FAILED, ex.getMessage());
        }
    }

    static Path siblingWithFlippedExtension(Path source, boolean fromMdx) {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String baseName = dot < 0 ? name : name.substring(0, dot);
        return source.getParent() == null
                ? Path.of(baseName + (fromMdx ? ".mdl" : ".mdx"))
                : source.getParent().resolve(baseName + (fromMdx ? ".mdl" : ".mdx"));
    }

    static long[] counts(MdlxModel model) {
        long vertices = 0;
        long faces = 0;
        for (var geoset : model.geosets) {
            if (geoset == null) continue;
            if (geoset.vertices != null) vertices += geoset.vertices.length / 3;
            if (geoset.faces != null) faces += geoset.faces.length / 3;
        }
        return new long[] { vertices, faces, model.bones.size(), model.sequences.size() };
    }

    static byte[] bytesOf(ByteBuffer buffer) {
        ByteBuffer dup = buffer.duplicate();
        dup.rewind();
        byte[] out = new byte[dup.remaining()];
        dup.get(out);
        return out;
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
