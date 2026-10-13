package com.hiveworkshop.edit;

import com.hiveworkshop.parser.ReterasModelParser;
import com.hiveworkshop.rms.parsers.mdlx.MdlLoadSave;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.MdxLoadSave;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Loads models into an editable {@link MdlxModel} and writes edits back with
 * the safety net from the roadmap: the serialized bytes must pass a structural
 * round-trip check before the file is touched, and the previous content is
 * backed up first (see {@link BackupManager}). Shared by every editor dialog.
 */
public final class ModelFileService {

    /** @param backupPath null when the write never happened. */
    public record WriteResult(boolean success, Path backupPath, String message) {}

    private ModelFileService() {}

    /** Parses an .mdx/.mdl file into an editable model. */
    public static MdlxModel loadModel(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        MdlxModel model = new MdlxModel();
        if (isMdx(file)) {
            MdxLoadSave.loadMdx(model, ByteBuffer.wrap(bytes));
        } else {
            MdlLoadSave.loadMdl(model, ByteBuffer.wrap(bytes));
        }
        return model;
    }

    /**
     * Serializes {@code edited} back to {@code file} in the file's original format.
     * Nothing is written unless the produced bytes reload with identical vertices,
     * faces and bones plus the expected sequence count; then the original content
     * is backed up and the file replaced.
     */
    public static WriteResult writeModel(Path file, MdlxModel edited) {
        boolean toMdx = isMdx(file);
        try {
            byte[] out = toMdx
                    ? MdlxMdlConverter.bytesOf(MdxLoadSave.saveMdx(edited))
                    : MdlxMdlConverter.bytesOf(MdlLoadSave.saveMdl(edited));

            long[] expected = MdlxMdlConverter.counts(edited);
            MdlxModel roundTrip = new MdlxModel();
            if (toMdx) {
                MdxLoadSave.loadMdx(roundTrip, ByteBuffer.wrap(out));
            } else {
                MdlLoadSave.loadMdl(roundTrip, ByteBuffer.wrap(out));
            }
            long[] written = MdlxMdlConverter.counts(roundTrip);
            if (!Arrays.equals(expected, written)) {
                return new WriteResult(false, null,
                        "Consistency check failed: expected " + Arrays.toString(expected)
                                + " got " + Arrays.toString(written)
                                + " [vertices, faces, bones, sequences]");
            }

            Path backup = BackupManager.backup(file);
            Files.write(file, out);
            ReterasModelParser.invalidate(file);
            return new WriteResult(true, backup, null);
        } catch (Exception ex) {
            return new WriteResult(false, null, ex.getMessage());
        }
    }

    static boolean isMdx(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mdx");
    }
}
