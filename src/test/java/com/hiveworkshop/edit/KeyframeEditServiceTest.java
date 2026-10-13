package com.hiveworkshop.edit;

import com.hiveworkshop.rms.parsers.mdlx.InterpolationType;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxTimeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyframeEditServiceTest {

    @TempDir
    Path tempDir;

    /** Bone B0 with a 3-key LINEAR Translation, 1-key Rotation and 1-key Scaling. */
    private static final String MINIMAL_MDL = """
            Version {
                FormatVersion 800,
            }
            Sequences 1 {
                Anim "Stand" {
                    MinimumExtent { -1.000000, -1.000000, -1.000000 },
                    MaximumExtent { 1.000000, 1.000000, 1.000000 },
                    BoundsRadius 1.000000,
                }
            }
            Materials 1 {
                Material {
                    Layer {
                        FilterMode None,
                        static TextureID 0,
                    }
                }
            }
            Textures 1 {
                Bitmap {
                    Image "",
                }
            }
            Geoset {
                Vertices 3 {
                    { -1.000000, -1.000000, 0.000000 },
                    { 1.000000, -1.000000, 0.000000 },
                    { 0.000000, 1.000000, 0.000000 },
                }
                Normals 3 {
                    { 0.000000, 0.000000, 1.000000 },
                    { 0.000000, 0.000000, 1.000000 },
                    { 0.000000, 0.000000, 1.000000 },
                }
                Faces 1 3 {
                    Triangles {
                        { 0, 1, 2 },
                    }
                }
                Groups 1 1 {
                    Matrices { 0 },
                }
                MinimumExtent { -1.000000, -1.000000, 0.000000 },
                MaximumExtent { 1.000000, 1.000000, 0.000000 },
                BoundsRadius 1.414214,
                MaterialID 0,
                SelectionGroup 0,
            }
            Bone "B0" {
                ObjectId 0,
                GeosetId 0,
                GeosetAnimId None,
                Translation 3 {
                    Linear,
                    0: { 0.000000, 0.000000, 0.000000 },
                    500: { 1.000000, 2.000000, 3.000000 },
                    1000: { 0.000000, 0.000000, 0.000000 },
                }
                Rotation 1 {
                    Linear,
                    0: { 0.000000, 0.000000, 0.000000, 1.000000 },
                }
                Scaling 1 {
                    Linear,
                    0: { 1.000000, 1.000000, 1.000000 },
                }
            }
            PivotPoints 1 {
                { 0.000000, 0.000000, 0.000000 },
            }
            """;

    private MdlxModel loadModel() throws Exception {
        Path file = tempDir.resolve("model.mdl");
        Files.writeString(file, MINIMAL_MDL);
        return ModelFileService.loadModel(file);
    }

    private KeyframeEditService.Track translationTrack(MdlxModel model) {
        return KeyframeEditService.tracksOf(model.bones.get(0)).stream()
                .filter(t -> t.code().equals(KeyframeEditService.TRANSLATION))
                .findFirst().orElseThrow();
    }

    @Test
    void collectsTransformTracks() throws Exception {
        MdlxModel model = loadModel();
        List<KeyframeEditService.Track> tracks = KeyframeEditService.tracksOf(model.bones.get(0));
        assertEquals(3, tracks.size());
        assertEquals("KGTR", tracks.get(0).code());
        assertEquals("KGRT", tracks.get(1).code());
        assertEquals("KGSC", tracks.get(2).code());
        assertEquals(3, translationTrack(model).valueSize());
    }

    @Test
    void removeAndInsertFramesRebuildArrays() throws Exception {
        MdlxModel model = loadModel();
        MdlxTimeline<?> timeline = translationTrack(model).timeline();

        assertEquals(3, timeline.frames.length);
        KeyframeEditService.removeFrame(timeline, 1);
        assertArrayEquals(new long[] { 0, 1000 }, timeline.frames);
        assertEquals(2, timeline.values.length);

        KeyframeEditService.insertFrameAfter(timeline, 0);
        assertArrayEquals(new long[] { 0, 100, 1000 }, timeline.frames);
        assertEquals(3, timeline.values.length);
        assertArrayEquals(new float[] { 0f, 0f, 0f }, (float[]) timeline.values[1]);
    }

    @Test
    void dedupeRemovesOnlyLinearTripleDuplicates() throws Exception {
        MdlxModel model = loadModel();
        MdlxTimeline<?> timeline = translationTrack(model).timeline();
        // 0, 100 and 1000 all share {0,0,0} — the middle key at 100 is redundant
        KeyframeEditService.setFrameTime(timeline, 1, 100);
        KeyframeEditService.setValues(timeline, 1, new float[] { 0f, 0f, 0f });

        assertEquals(1, KeyframeEditService.dedupeLinear(timeline));
        assertArrayEquals(new long[] { 0, 1000 }, timeline.frames);

        // second pass finds nothing
        assertEquals(0, KeyframeEditService.dedupeLinear(timeline));
    }

    @Test
    void dedupeKeepsDifferingMiddleValues() throws Exception {
        MdlxModel model = loadModel();
        MdlxTimeline<?> timeline = translationTrack(model).timeline();
        // middle value differs — real animation, must survive
        assertEquals(0, KeyframeEditService.dedupeLinear(timeline));
        assertArrayEquals(new long[] { 0, 500, 1000 }, timeline.frames);
    }

    @Test
    void dedupeNeverTouchesNonLinearTracks() throws Exception {
        MdlxModel model = loadModel();
        MdlxTimeline<?> timeline = translationTrack(model).timeline();
        KeyframeEditService.setFrameTime(timeline, 1, 100);
        KeyframeEditService.setValues(timeline, 1, new float[] { 0f, 0f, 0f });
        timeline.interpolationType = InterpolationType.DONT_INTERP;

        assertEquals(0, KeyframeEditService.dedupeLinear(timeline));
        assertArrayEquals(new long[] { 0, 100, 1000 }, timeline.frames);
    }

    @Test
    void editsRoundTripThroughMdxWriteBack() throws Exception {
        Path mdl = tempDir.resolve("model.mdl");
        Files.writeString(mdl, MINIMAL_MDL);
        MdlxModel model = ModelFileService.loadModel(mdl);
        MdlxTimeline<?> timeline = translationTrack(model).timeline();

        KeyframeEditService.removeFrame(timeline, 1);
        KeyframeEditService.setValues(timeline, 0, new float[] { 7f, 8f, 9f });
        timeline.interpolationType = InterpolationType.DONT_INTERP;
        Path mdx = tempDir.resolve("model.mdx");
        Files.write(mdx, MdlxMdlConverter.bytesOf(
                com.hiveworkshop.rms.parsers.mdlx.MdxLoadSave.saveMdx(model)));
        assertTrue(ModelFileService.writeModel(mdx, model).success(), "write-back failed");

        MdlxModel reloaded = ModelFileService.loadModel(mdx);
        MdlxTimeline<?> rt = KeyframeEditService.tracksOf(reloaded.bones.get(0))
                .stream().filter(t -> t.code().equals(KeyframeEditService.TRANSLATION))
                .findFirst().orElseThrow().timeline();
        assertArrayEquals(new long[] { 0, 1000 }, rt.frames);
        assertArrayEquals(new float[] { 7f, 8f, 9f }, (float[]) rt.values[0]);
        assertEquals(InterpolationType.DONT_INTERP, rt.interpolationType);
    }
}
