package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.InterpolationType;
import com.hiveworkshop.rms.parsers.mdlx.MdlxAnimatedObject;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxTimeline;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Keyframe editing primitives (P3c). Timelines are plain arrays inside the
 * bundled RMS structures ({@code frames[] + values[] + inTans/outTans[]}), so
 * all editing is array surgery on the in-memory model; the caller owns preview
 * and write-back through {@link ModelFileService}.
 *
 * Deduplication safety rule: a middle keyframe is removed only when the track
 * interpolates LINEARly and the previous, current and next keyframe values are
 * exactly equal — mathematically a no-op on the curve. DONT_INTERP (step keys)
 * and tangential types (HERMITE/BEZIER) are never touched.
 */
public final class KeyframeEditService {

    public static final String TRANSLATION = "KGTR";
    public static final String ROTATION = "KGRT";
    public static final String SCALING = "KGSC";

    private KeyframeEditService() {}

    public record Track(String code, MdlxTimeline<?> timeline, int valueSize) {
        @Override public String toString() {
            return code + " · " + timeline.interpolationType + " · " + timeline.frames.length;
        }
    }

    /** All transform timelines of one animated object (KGTR/KGRT/KGSC, plus any others). */
    public static List<Track> tracksOf(MdlxAnimatedObject object) {
        List<Track> tracks = new ArrayList<>();
        for (MdlxTimeline<?> timeline : object.timelines) {
            String code = timeline.name == null ? "?" : timeline.name.toString();
            tracks.add(new Track(code, timeline, valueSize(code, timeline)));
        }
        return tracks;
    }

    private static int valueSize(String code, MdlxTimeline<?> timeline) {
        if (timeline.values.length > 0 && timeline.values[0] instanceof float[] v) return v.length;
        return switch (code) {
            case ROTATION -> 4;
            case TRANSLATION, SCALING -> 3;
            default -> 1;
        };
    }

    private static void validateIndex(MdlxTimeline<?> timeline, int index) {
        if (index < 0 || index >= timeline.frames.length) {
            throw new IllegalArgumentException("keyframe " + index + " out of range");
        }
    }

    public static long getFrame(MdlxTimeline<?> timeline, int index) {
        validateIndex(timeline, index);
        return timeline.frames[index];
    }

    public static void setFrameTime(MdlxTimeline<?> timeline, int index, long frame) {
        validateIndex(timeline, index);
        timeline.frames[index] = frame;
    }

    public static float[] getValues(MdlxTimeline<?> timeline, int index) {
        validateIndex(timeline, index);
        return (float[]) timeline.values[index];
    }

    public static void setValues(MdlxTimeline<?> timeline, int index, float[] values) {
        validateIndex(timeline, index);
        assignValue(timeline, index, values.clone());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assignValue(MdlxTimeline timeline, int index, Object value) {
        timeline.values[index] = value;
    }

    public static void removeFrame(MdlxTimeline<?> timeline, int index) {
        validateIndex(timeline, index);
        int[] keep = new int[timeline.frames.length - 1];
        for (int i = 0, k = 0; i < timeline.frames.length; i++) {
            if (i != index) keep[k++] = i;
        }
        rebuild(timeline, keep);
    }

    /** Inserts a copy of {@code index} right after it, frame time shifted by +100 ms. */
    public static void insertFrameAfter(MdlxTimeline<?> timeline, int index) {
        validateIndex(timeline, index);
        int n = timeline.frames.length;
        long[] frames = new long[n + 1];
        Object[] values = newArray(timeline.values, n + 1);
        boolean hasTangents = timeline.inTans != null && timeline.inTans.length > 0;
        Object[] inTans = hasTangents ? newArray(timeline.inTans, n + 1) : null;
        Object[] outTans = timeline.outTans != null && timeline.outTans.length > 0
                ? newArray(timeline.outTans, n + 1) : null;
        for (int i = 0; i <= n; i++) {
            if (i == index + 1) {
                frames[i] = timeline.frames[index] + 100;
                values[i] = copyValue(timeline, index);
                if (inTans != null) inTans[i] = copyTangent(timeline.inTans, index);
                if (outTans != null) outTans[i] = copyTangent(timeline.outTans, index);
            } else {
                int src = i > index + 1 ? i - 1 : i;
                frames[i] = timeline.frames[src];
                values[i] = timeline.values[src];
                if (inTans != null) inTans[i] = timeline.inTans[src];
                if (outTans != null) outTans[i] = timeline.outTans[src];
            }
        }
        timeline.frames = frames;
        assign(timeline, values, inTans, outTans);
    }

    /** Removes LINEAR middle keys whose neighbours and own value are identical. Returns removed count. */
    public static int dedupeLinear(MdlxTimeline<?> timeline) {
        if (timeline.interpolationType != InterpolationType.LINEAR || timeline.frames.length < 3) return 0;
        boolean[] remove = new boolean[timeline.frames.length];
        int removed = 0;
        for (int i = 1; i < timeline.frames.length - 1; i++) {
            if (timeline.frames[i - 1] == timeline.frames[i] || timeline.frames[i] == timeline.frames[i + 1]) continue;
            if (sameValues(timeline, i - 1, i) && sameValues(timeline, i, i + 1)) {
                remove[i] = true;
                removed++;
            }
        }
        if (removed > 0) rebuild(timeline, compactMask(remove));
        return removed;
    }

    private static int[] compactMask(boolean[] remove) {
        int[] keep = new int[remove.length - countTrue(remove)];
        for (int i = 0, k = 0; i < remove.length; i++) {
            if (!remove[i]) keep[k++] = i;
        }
        return keep;
    }

    private static int countTrue(boolean[] flags) {
        int count = 0;
        for (boolean flag : flags) if (flag) count++;
        return count;
    }

    private static boolean sameValues(MdlxTimeline<?> timeline, int a, int b) {
        return Arrays.equals((float[]) timeline.values[a], (float[]) timeline.values[b]);
    }

    private static void rebuild(MdlxTimeline<?> timeline, int[] keep) {
        long[] frames = new long[keep.length];
        for (int i = 0; i < keep.length; i++) frames[i] = timeline.frames[keep[i]];
        timeline.frames = frames;
        Object[] values = copySelected(timeline.values, keep);
        // LINEAR/DONT_INTERP tracks carry null tangent arrays — keep them null.
        Object[] inTans = timeline.inTans == null ? null : copySelected(timeline.inTans, keep);
        Object[] outTans = timeline.outTans == null ? null : copySelected(timeline.outTans, keep);
        assign(timeline, values, inTans, outTans);
    }

    private static Object[] copySelected(Object[] source, int[] keep) {
        Object[] out = (Object[]) Array.newInstance(source.getClass().getComponentType(), keep.length);
        for (int i = 0; i < keep.length; i++) out[i] = source[keep[i]];
        return out;
    }

    private static Object[] newArray(Object[] template, int length) {
        return (Object[]) Array.newInstance(template.getClass().getComponentType(), length);
    }

    private static Object copyValue(MdlxTimeline<?> timeline, int index) {
        float[] source = (float[]) timeline.values[index];
        return Arrays.copyOf(source, source.length);
    }

    private static Object copyTangent(Object[] tangents, int index) {
        float[] source = (float[]) tangents[index];
        return Arrays.copyOf(source, source.length);
    }

    private static void assign(MdlxTimeline<?> timeline, Object[] values, Object[] inTans, Object[] outTans) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        MdlxTimeline raw = timeline;
        raw.values = values;
        raw.inTans = inTans;
        raw.outTans = outTans;
    }
}
