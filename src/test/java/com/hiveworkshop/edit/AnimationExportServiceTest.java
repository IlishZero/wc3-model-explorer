package com.hiveworkshop.edit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationExportServiceTest {

    @TempDir
    Path tempDir;

    private static BufferedImage frame(int argb) {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) img.setRGB(x, y, argb);
        return img;
    }

    @Test
    void timelineStepsAtFpsAndCapsFrameCount() {
        var times = AnimationExportService.buildTimeline(1000, 2000, 10);
        assertEquals(10, times.size());
        assertEquals(1000L, times.get(0));
        assertEquals(1900L, times.get(9));

        // 0..100000 at 10fps = 1000 frames — capped at MAX_FRAMES
        var capped = AnimationExportService.buildTimeline(0, 100_000, 10);
        assertEquals(AnimationExportService.MAX_FRAMES, capped.size());
        assertTrue(AnimationExportService.buildTimeline(5, 5, 10).isEmpty());
    }

    @Test
    void scaleChangesDimensionsAndKeepsIdentity() {
        BufferedImage src = frame(0xFF0000);
        assertEquals(src, AnimationExportService.scale(src, 1.0));
        BufferedImage half = AnimationExportService.scale(src, 0.5);
        assertEquals(4, half.getWidth());
        assertEquals(4, half.getHeight());
    }

    @Test
    void gifWriterProducesMultiFrameImage() throws Exception {
        Path gif = tempDir.resolve("out.gif");
        try (var writer = new GifSequenceWriter(frame(0xFF0000), true, gif)) {
            writer.writeFrame(frame(0xFF0000), 40);
            writer.writeFrame(frame(0x00FF00), 40);
            writer.writeFrame(frame(0x0000FF), 40);
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        ImageReader reader = readers.next();
        try (var in = ImageIO.createImageInputStream(gif.toFile())) {
            reader.setInput(in);
            assertEquals(3, reader.getNumImages(true));
        } finally {
            reader.dispose();
        }
    }

    @Test
    void pngSequenceWritesNumberedFiles() throws Exception {
        Path dir = tempDir.resolve("frames");
        var written = AnimationExportService.exportPngSequence(
                List.of(frame(0xFF0000), frame(0x00FF00)), dir, "anim");
        assertEquals(2, written.size());
        assertEquals(dir.resolve("anim_0001.png"), written.get(0));
        assertTrue(Files.exists(written.get(1)));
    }
}
