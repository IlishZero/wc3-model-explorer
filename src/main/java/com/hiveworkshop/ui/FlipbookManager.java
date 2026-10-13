package com.hiveworkshop.ui;

import com.hiveworkshop.model.ModelAsset;
import com.hiveworkshop.model.ModelMetadata;
import com.hiveworkshop.model.SequenceInfo;
import com.hiveworkshop.parser.ReterasModelParser;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * Selected-model flipbook animation (roadmap addendum, plan A): the selected
 * model's pose animation is rendered once as {@value #FRAME_COUNT} keyframes
 * through the shared thumbnail GL queue, then cycled on a single Swing timer.
 *
 * Guardrails (memory-leak safe by construction):
 * <ul>
 *   <li>only ONE model animates at a time — frames are held for the current
 *       selection only and dropped on {@link #stop()};</li>
 *   <li>nothing renders during playback (pure image swapping on the EDT);</li>
 *   <li>no extra GL context, no particle/ribbon emitters running;</li>
 *   <li>stale runs (rapid re-selection) are ignored via a run counter;</li>
 *   <li>{@link #pause()}/{@link #resume()} for window focus/minimize.</li>
 * </ul>
 */
final class FlipbookManager {

    static final int FRAME_COUNT = 12;
    static final int FRAME_PERIOD_MS = 90; // ≈11 fps flip rate

    private final ThumbnailRenderer renderer;
    private final Runnable onFrameChanged;
    private final javax.swing.Timer timer;

    private BufferedImage[] frames;
    private Path target;
    private int ready;
    private int tick;
    private int runCounter;

    FlipbookManager(ThumbnailRenderer renderer, Runnable onFrameChanged) {
        this.renderer = renderer;
        this.onFrameChanged = onFrameChanged;
        timer = new javax.swing.Timer(FRAME_PERIOD_MS, e -> advance());
    }

    /** Pose times spread evenly over [start, end); pure math for tests. */
    static long[] frameTimes(long start, long end, int count) {
        if (end <= start || count <= 0) return new long[0];
        long[] times = new long[count];
        for (int i = 0; i < count; i++) {
            times[i] = start + (end - start) * i / count;
        }
        return times;
    }

    /**
     * Starts (or restarts) the flipbook for one model. Validates first that the
     * model actually has animatable geometry, then queues {@value #FRAME_COUNT}
     * keyframes of its "Stand" sequence (or the first sequence).
     */
    void startFor(ModelAsset asset, Path rootDir) {
        stop();
        var parsed = ReterasModelParser.parse(asset.path());
        if (parsed == null || parsed.metadata() == null || parsed.metadata() == ModelMetadata.EMPTY) return;
        var mesh = parsed.mesh();
        var animData = parsed.animData();
        if (mesh == null || mesh.isEmpty() || animData == null || !animData.hasAnimation()) return;
        SequenceInfo seq = findSequence(animData.sequences());
        if (seq == null || seq.end() <= seq.start()) return;

        long[] times = frameTimes(seq.start(), seq.end(), FRAME_COUNT);
        frames = new BufferedImage[times.length];
        target = asset.path();
        ready = 0;
        final int run = ++runCounter;
        for (int i = 0; i < times.length; i++) {
            final int index = i;
            // Frames live in their own slot (and disk cache via their explicit
            // pose time) — the per-path static thumbnail is left untouched.
            renderer.requestFrame(asset, rootDir, times[i], img -> {
                if (run != runCounter || frames == null) return; // stale run
                if (img != null) {
                    frames[index] = img;
                    ready++;
                    if (ready == 1) timer.start();
                }
            });
        }
    }

    /** Stops everything and drops all frames. */
    void stop() {
        timer.stop();
        runCounter++;
        frames = null;
        target = null;
        ready = 0;
        tick = 0;
    }

    /** Window lost focus / minimized: stop flipping, keep the frames. */
    void pause() {
        timer.stop();
    }

    void resume() {
        if (frames != null && ready > 0) timer.start();
    }

    /** Current cycle frame for the animated model, or null → caller uses the static thumbnail. */
    BufferedImage currentFrame(Path forPath) {
        if (frames == null || target == null || ready == 0 || !target.equals(forPath)) return null;
        return frames[tick % ready];
    }

    private void advance() {
        if (frames == null || ready == 0) {
            timer.stop();
            return;
        }
        tick++;
        onFrameChanged.run();
    }

    private SequenceInfo findSequence(List<SequenceInfo> sequences) {
        for (SequenceInfo s : sequences) {
            if (s != null && "Stand".equalsIgnoreCase(s.name())) return s;
        }
        return sequences.isEmpty() ? null : sequences.get(0);
    }
}
