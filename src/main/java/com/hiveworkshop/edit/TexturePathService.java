package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Batch text replacement over the model's texture paths. Matching is
 * case-insensitive (texture paths are Windows paths and effectively
 * case-insensitive in-game); the replacement is literal, not a regex.
 */
public final class TexturePathService {

    private TexturePathService() {}

    /**
     * Replaces every occurrence of {@code find} inside each texture path.
     *
     * @return the number of paths that were actually changed
     */
    public static int replaceAll(MdlxModel model, String find, String replaceWith) {
        if (find == null || find.isEmpty()) return 0;
        String replacement = replaceWith == null ? "" : replaceWith;
        Pattern pattern = Pattern.compile(Pattern.quote(find), Pattern.CASE_INSENSITIVE);
        int changed = 0;
        for (var texture : model.textures) {
            if (texture.path == null || texture.path.isEmpty()) continue;
            Matcher matcher = pattern.matcher(texture.path);
            if (matcher.find()) {
                texture.path = matcher.replaceAll(Matcher.quoteReplacement(replacement));
                changed++;
            }
        }
        return changed;
    }
}
