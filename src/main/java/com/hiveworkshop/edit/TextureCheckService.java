package com.hiveworkshop.edit;

import com.hiveworkshop.parser.GameDataSource;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Texture health check: resolves every texture reference of a model through
 * the same {@link GameDataSource} lookup the renderer uses, so "found" here
 * means "will render". Purely read-only.
 */
public final class TextureCheckService {

    /** {@link #source} is one of DISK / CASC / MPQ / MAP / REPLACEABLE / MISSING. */
    public record TextureStatus(String path, int replaceableId, String source, boolean found) {}

    private TextureCheckService() {}

    public static List<TextureStatus> check(MdlxModel model, Path modelDir, Path rootDir) {
        GameDataSource dataSource = GameDataSource.getInstance();
        List<TextureStatus> statuses = new ArrayList<>();
        for (var texture : model.textures) {
            String source = dataSource.resolveTextureSource(
                    texture.path, texture.replaceableId, modelDir, rootDir);
            statuses.add(new TextureStatus(
                    texture.path == null ? "" : texture.path,
                    texture.replaceableId,
                    source,
                    !"MISSING".equals(source)));
        }
        return statuses;
    }
}
