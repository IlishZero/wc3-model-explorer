package com.hiveworkshop.edit;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Animation export helpers (roadmap P1-9): timeline generation, scaling and
 * GIF / PNG-sequence writing. Pure functions so the timeline math and scaling
 * are unit-testable; the viewer only supplies frames via its screenshot hook.
 */
public final class AnimationExportService {

    /** Hard frame cap so a long animation can't silently produce a gigabyte GIF. */
    public static final int MAX_FRAMES = 600;

    private AnimationExportService() {}

    /**
     * Frame capture times (ms) from {@code start} (inclusive) toward {@code end}
     * (exclusive) at {@code framesPerSecond}, capped at {@value #MAX_FRAMES} frames.
     */
    public static List<Long> buildTimeline(long start, long end, int framesPerSecond) {
        List<Long> times = new ArrayList<>();
        if (end <= start || framesPerSecond <= 0) return times;
        long interval = Math.max(1, 1000L / framesPerSecond);
        for (long t = start; t < end && times.size() < MAX_FRAMES; t += interval) {
            times.add(t);
        }
        return times;
    }

    /** Nearest-neighbour-ish scaling via bilinear draw; factor 1.0 returns the input. */
    public static BufferedImage scale(BufferedImage source, double factor) {
        if (factor >= 0.999 && factor <= 1.001) return source;
        int w = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int h = Math.max(1, (int) Math.round(source.getHeight() * factor));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Writes frames as one looping animated GIF (1000/{@code fps} ms per frame). */
    public static void exportGif(List<BufferedImage> frames, int framesPerSecond, Path target) throws IOException {
        if (frames.isEmpty()) throw new IOException("No frames to write");
        int delay = Math.max(33, 1000 / Math.max(1, framesPerSecond));
        try (GifSequenceWriter gif = new GifSequenceWriter(frames.get(0), true, target)) {
            for (BufferedImage frame : frames) {
                gif.writeFrame(frame, delay);
            }
        }
    }

    /** Writes frames as {@code baseName_0001.png}, ... into {@code dir} (created if needed). */
    public static List<Path> exportPngSequence(List<BufferedImage> frames, Path dir, String baseName) throws IOException {
        Files.createDirectories(dir);
        List<Path> written = new ArrayList<>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            Path file = dir.resolve(String.format("%s_%04d.png", baseName, i + 1));
            ImageIO.write(frames.get(i), "png", file.toFile());
            written.add(file);
        }
        return written;
    }
}
