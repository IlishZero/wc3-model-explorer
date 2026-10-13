package com.hiveworkshop.edit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineDiffTest {

    private long count(List<LineDiff.Line> lines, LineDiff.Type type) {
        return lines.stream().filter(l -> l.type() == type).count();
    }

    @Test
    void identicalInputsProduceOnlySame() {
        var lines = LineDiff.diff(List.of("a", "b", "c"), List.of("a", "b", "c"));
        assertEquals(3, lines.size());
        assertEquals(0, count(lines, LineDiff.Type.ADDED));
        assertEquals(0, count(lines, LineDiff.Type.REMOVED));
    }

    @Test
    void singleLineReplacementIsOneRemovePlusOneAdd() {
        var lines = LineDiff.diff(
                List.of("header", "Anim \"Stand\" {", "  BoundsRadius 1,", "}"),
                List.of("header", "Anim \"Walk\" {", "  BoundsRadius 1,", "}"));
        assertEquals(1, count(lines, LineDiff.Type.REMOVED));
        assertEquals(1, count(lines, LineDiff.Type.ADDED));
        assertEquals(3, count(lines, LineDiff.Type.SAME));
        assertTrue(lines.stream().anyMatch(l ->
                l.type() == LineDiff.Type.REMOVED && l.text().contains("Stand")));
        assertTrue(lines.stream().anyMatch(l ->
                l.type() == LineDiff.Type.ADDED && l.text().contains("Walk")));
    }

    @Test
    void insertionInTheMiddleKeepsContext() {
        var lines = LineDiff.diff(List.of("a", "c"), List.of("a", "b", "c"));
        assertEquals(1, count(lines, LineDiff.Type.ADDED));
        assertEquals(0, count(lines, LineDiff.Type.REMOVED));
        assertEquals(2, count(lines, LineDiff.Type.SAME));
    }

    @Test
    void movedBlockIsReportedWithoutCorruption() {
        var lines = LineDiff.diff(List.of("x", "y", "z"), List.of("z", "x", "y"));
        // patience keeps x,y anchored; z is removed at the tail and added at the head
        assertEquals(2, count(lines, LineDiff.Type.SAME));
        assertEquals(1, count(lines, LineDiff.Type.REMOVED));
        assertEquals(1, count(lines, LineDiff.Type.ADDED));
    }

    @Test
    void reorderedEventsInTwoBlocksStayLocal() {
        var a = List.of("Geoset 1 {", "  A", "}", "Geoset 2 {", "  B", "}");
        var b = List.of("Geoset 1 {", "  A2", "}", "Geoset 2 {", "  B2", "}");
        var lines = LineDiff.diff(a, b);
        assertEquals(2, count(lines, LineDiff.Type.REMOVED));
        assertEquals(2, count(lines, LineDiff.Type.ADDED));
        assertEquals(4, count(lines, LineDiff.Type.SAME));
    }

    @Test
    void emptySideMeansPureAddOrRemove() {
        assertEquals(3, count(LineDiff.diff(List.of("a", "b", "c"), List.of()), LineDiff.Type.REMOVED));
        assertEquals(3, count(LineDiff.diff(List.of(), List.of("a", "b", "c")), LineDiff.Type.ADDED));
    }
}
