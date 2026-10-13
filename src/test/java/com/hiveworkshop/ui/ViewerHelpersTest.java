package com.hiveworkshop.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerHelpersTest {

    // ── Loop range wrap (P1-7) ──────────────────────────────────────────────

    @Test
    void timeInsideRangePassesThrough() {
        assertEquals(1500L, GlPreviewCanvas.wrapLoopRange(1500L, 1000L, 3000L));
    }

    @Test
    void timeBeyondRangeWrapsRelativeToInPoint() {
        // 1000..3000 (len 2000): 3500 → 1500, 5000 → 1000, 7000 → 3000-? = 1000+1000
        assertEquals(1500L, GlPreviewCanvas.wrapLoopRange(3500L, 1000L, 3000L));
        assertEquals(1000L, GlPreviewCanvas.wrapLoopRange(5000L, 1000L, 3000L));
        assertEquals(3000L - 1000L, GlPreviewCanvas.wrapLoopRange(5000L + 1000L, 1000L, 3000L));
    }

    @Test
    void rangeNotAlignedToZeroStillWrapsFromIn() {
        // 500..900 (len 400): 1300 → 500+((1300-500)%400)=900-... 800%400=0 → 500
        assertEquals(500L, GlPreviewCanvas.wrapLoopRange(1300L, 500L, 900L));
        assertEquals(600L, GlPreviewCanvas.wrapLoopRange(1400L, 500L, 900L));
    }

    // ── Thumbnail disk cache key (P1-6) ─────────────────────────────────────

    @Test
    void cacheKeyIsDeterministicAndSensitiveToPoseInputs() {
        String base = ThumbnailRenderer.diskCacheKey("C:\\m\\x.mdx", 100, 5000L, 0, "Stand", 512, 256, 0L);
        assertEquals(base, ThumbnailRenderer.diskCacheKey("C:\\m\\x.mdx", 100, 5000L, 0, "Stand", 512, 256, 0L));

        // Any input that changes the rendered pose must change the key.
        assertNotEquals(base, ThumbnailRenderer.diskCacheKey("C:\\m\\x.mdx", 100, 5000L, 1, "Stand", 512, 256, 0L));
        assertNotEquals(base, ThumbnailRenderer.diskCacheKey("C:\\m\\x.mdx", 100, 5000L, 0, "Walk", 512, 256, 0L));
        assertNotEquals(base, ThumbnailRenderer.diskCacheKey("C:\\m\\x.mdx", 100, 5000L, 0, "Stand", 1024, 256, 0L));
        assertNotEquals(base, ThumbnailRenderer.diskCacheKey("C:\\m\\x.mdx", 100, 5000L, 0, "Stand", 512, 128, 0L));
        assertNotEquals(base, ThumbnailRenderer.diskCacheKey("C:\\m\\x.mdx", 101, 5000L, 0, "Stand", 512, 256, 0L));
        assertNotEquals(base, ThumbnailRenderer.diskCacheKey("C:\\m\\x.mdx", 100, 6000L, 0, "Stand", 512, 256, 0L));
        // Flipbook frames at different pose times must not collide.
        assertNotEquals(base, ThumbnailRenderer.diskCacheKey("C:\\m\\x.mdx", 100, 5000L, 0, "Stand", 512, 256, 500L));
    }

    // ── Flipbook (plan A) ────────────────────────────────────────────────────

    @Test
    void flipbookTimesSpreadEvenlyAndRespectBounds() {
        long[] times = FlipbookManager.frameTimes(1000L, 3000L, 4);
        assertEquals(4, times.length);
        assertEquals(1000L, times[0]);
        assertEquals(1500L, times[1]);
        assertEquals(2000L, times[2]);
        assertEquals(2500L, times[3]);

        assertEquals(0, FlipbookManager.frameTimes(5, 5, 12).length);
        assertEquals(0, FlipbookManager.frameTimes(10, 5, 12).length);
    }

    @Test
    void lruCacheEvictsEldestBeyondCapacity() {
        var cache = ThumbnailRenderer.newLruCache(3);
        cache.put(Path.of("a"), null);
        cache.put(Path.of("b"), null);
        cache.put(Path.of("c"), null);
        assertEquals(3, cache.size());
        cache.get(Path.of("a")); // touch a → b becomes eldest
        cache.put(Path.of("d"), null);
        assertEquals(3, cache.size());
        assertTrue(cache.containsKey(Path.of("a")),
                () -> "keys=" + cache.keySet() + " contains=" + cache.containsKey(Path.of("a")));
        assertTrue(!cache.containsKey(Path.of("b")),
                () -> "keys=" + cache.keySet() + " containsB=" + cache.containsKey(Path.of("b")));
    }
}
