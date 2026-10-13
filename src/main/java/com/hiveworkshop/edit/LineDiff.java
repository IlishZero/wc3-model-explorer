package com.hiveworkshop.edit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Patience line diff — no external dependencies. Matches lines that are
 * unique on both sides as anchors (longest increasing subsequence), recurses
 * between them and falls back to trim + bulk replace when nothing unique
 * remains. Good output quality for structured text like MDL, where block
 * headers act as natural anchors.
 */
public final class LineDiff {

    public enum Type { SAME, ADDED, REMOVED }

    public record Line(Type type, String text) {}

    private LineDiff() {}

    public static List<Line> diff(List<String> a, List<String> b) {
        List<Line> out = new ArrayList<>();
        diffInto(out, a, 0, a.size(), b, 0, b.size());
        return out;
    }

    private static void diffInto(List<Line> out, List<String> a, int a0, int a1,
                                 List<String> b, int b0, int b1) {
        while (a0 < a1 && b0 < b1 && a.get(a0).equals(b.get(b0))) {
            out.add(new Line(Type.SAME, a.get(a0)));
            a0++;
            b0++;
        }
        int commonTail = 0;
        while (a1 > a0 && b1 > b0 && a.get(a1 - 1).equals(b.get(b1 - 1))) {
            a1--;
            b1--;
            commonTail++;
        }
        middle(out, a, a0, a1, b, b0, b1);
        for (int i = a1; i < a1 + commonTail; i++) {
            out.add(new Line(Type.SAME, a.get(i)));
        }
    }

    private static void middle(List<Line> out, List<String> a, int a0, int a1,
                               List<String> b, int b0, int b1) {
        if (a0 == a1) {
            for (int i = b0; i < b1; i++) out.add(new Line(Type.ADDED, b.get(i)));
            return;
        }
        if (b0 == b1) {
            for (int i = a0; i < a1; i++) out.add(new Line(Type.REMOVED, a.get(i)));
            return;
        }

        List<int[]> anchors = uniqueAnchors(a, a0, a1, b, b0, b1);
        if (anchors.isEmpty()) {
            for (int i = a0; i < a1; i++) out.add(new Line(Type.REMOVED, a.get(i)));
            for (int i = b0; i < b1; i++) out.add(new Line(Type.ADDED, b.get(i)));
            return;
        }

        int pa = a0;
        int pb = b0;
        for (int[] anchor : anchors) {
            diffInto(out, a, pa, anchor[0], b, pb, anchor[1]);
            out.add(new Line(Type.SAME, a.get(anchor[0])));
            pa = anchor[0] + 1;
            pb = anchor[1] + 1;
        }
        diffInto(out, a, pa, a1, b, pb, b1);
    }

    /** Patience anchors: lines appearing exactly once in both ranges, as
     *  (indexInA, indexInB) pairs reduced to the longest increasing subsequence
     *  over the B indices. */
    private static List<int[]> uniqueAnchors(List<String> a, int a0, int a1,
                                             List<String> b, int b0, int b1) {
        Map<String, Integer> aCount = new HashMap<>();
        for (int i = a0; i < a1; i++) aCount.merge(a.get(i), 1, Integer::sum);
        Map<String, Integer> bCount = new HashMap<>();
        for (int i = b0; i < b1; i++) bCount.merge(b.get(i), 1, Integer::sum);

        Map<String, Integer> aIndexOf = new HashMap<>();
        for (int i = a0; i < a1; i++) {
            String line = a.get(i);
            if (aCount.get(line) == 1 && bCount.getOrDefault(line, 0) == 1) {
                aIndexOf.putIfAbsent(line, i);
            }
        }
        List<int[]> pairs = new ArrayList<>();
        for (int j = b0; j < b1; j++) {
            Integer ai = aIndexOf.get(b.get(j));
            if (ai != null) pairs.add(new int[] { ai, j });
        }
        if (pairs.isEmpty()) return pairs;

        // pairs were collected in B order — sort by A index, then the longest
        // in-order chain (by B index) is the LIS over the B indices.
        pairs.sort(java.util.Comparator.comparingInt(p -> p[0]));
        int n = pairs.size();
        int[] tails = new int[n];       // pair index of smallest tail per LIS length
        int[] prev = new int[n];
        int[] bIdx = new int[n];
        for (int i = 0; i < n; i++) bIdx[i] = pairs.get(i)[1];
        int length = 0;
        for (int i = 0; i < n; i++) {
            int lo = 0;
            int hi = length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (bIdx[tails[mid]] < bIdx[i]) lo = mid + 1;
                else hi = mid;
            }
            tails[lo] = i;
            prev[i] = lo > 0 ? tails[lo - 1] : -1;
            if (lo == length) length++;
        }
        List<int[]> result = new ArrayList<>(length);
        for (int i = tails[length - 1]; i >= 0; i = prev[i]) {
            result.add(pairs.get(i));
        }
        return result.reversed();
    }
}
