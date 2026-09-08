package ro.bucharest.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class TrafficTest {
    private static final String TWO_FEATURES = "{\"type\":\"FeatureCollection\",\"features\":["
            + "{\"type\":\"Feature\",\"id\":\"639524382\",\"geometry\":{\"type\":\"LineString\","
            + "\"coordinates\":[[26.075537,44.444753],[26.076704,44.443977],[26.078100,44.443000]]},"
            + "\"properties\":{\"speed_ms\":9.4,\"speed_kph\":34,\"length\":300,\"delay_s\":12,"
            + "\"street\":\"Bd. Dinicu Golescu\",\"start_node\":\"\",\"end_node\":\"\"}},"
            + "{\"type\":\"Feature\",\"id\":\"42\",\"geometry\":{\"type\":\"LineString\","
            + "\"coordinates\":[[26.10,44.40],[26.101,44.401]]},"
            + "\"properties\":{\"speed_kph\":57,\"delay_s\":-1,\"street\":\"Str. X\"}}]}";

    @Test
    public void coordinatesAreConvertedToLatLonAndPropertiesAreRead() {
        List<Traffic.Segment> got = Traffic.parse(TWO_FEATURES);
        assertEquals(2, got.size());
        Traffic.Segment a = got.get(0);
        assertEquals(3, a.points.length);

        assertEquals(44.444753, a.points[0][0], 1e-9);
        assertEquals(26.075537, a.points[0][1], 1e-9);
        assertEquals(44.443977, a.points[1][0], 1e-9);
        assertEquals(26.076704, a.points[1][1], 1e-9);
        assertEquals(44.443000, a.points[2][0], 1e-9);
        assertEquals(26.078100, a.points[2][1], 1e-9);
        assertEquals(34.0, a.speedKph, 0.0);
        assertEquals(12.0, a.delayS, 0.0);
        assertEquals("Bd. Dinicu Golescu", a.street);
        Traffic.Segment b = got.get(1);
        assertEquals(2, b.points.length);
        assertEquals(44.40, b.points[0][0], 1e-9);
        assertEquals(26.10, b.points[0][1], 1e-9);
        assertEquals(57.0, b.speedKph, 0.0);
        assertEquals(-1.0, b.delayS, 0.0);
        assertEquals("Str. X", b.street);
    }

    @Test
    public void missingPropertiesFallBackToDefaults() {
        String json = "{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\","
                + "\"coordinates\":[[26.0,44.0],[26.001,44.001]]}},"
                + "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\","
                + "\"coordinates\":[[26.1,44.1],[26.101,44.101]]},\"properties\":{}}]}";
        List<Traffic.Segment> got = Traffic.parse(json);
        assertEquals(2, got.size());
        for (Traffic.Segment s : got) {
            assertEquals(-1.0, s.speedKph, 0.0);
            assertEquals(-1.0, s.delayS, 0.0);
            assertEquals("", s.street);
        }
    }

    @Test
    public void nonLineStringOrIncompleteGeometryIsSkipped() {
        String json = "{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[26.1,44.4]}},"
                + "{\"type\":\"Feature\",\"id\":\"no-geometry\"},"
                + "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\"}},"
                + "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\","
                + "\"coordinates\":[[26.0,44.0]]}},"
                + "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\","
                + "\"coordinates\":[[26.0,44.0],[26.001,44.001]]},\"properties\":{\"street\":\"ok\"}}"
                + "]}";
        List<Traffic.Segment> got = Traffic.parse(json);
        assertEquals(1, got.size());
        assertEquals("ok", got.get(0).street);
        assertEquals(2, got.get(0).points.length);
    }

    @Test
    public void malformedJsonYieldsEmptyList() {
        assertTrue(Traffic.parse("{oops").isEmpty());
        assertTrue(Traffic.parse("").isEmpty());
        assertTrue(Traffic.parse("   ").isEmpty());
        assertTrue(Traffic.parse("[1,2,3]").isEmpty());
        assertTrue(Traffic.parse("{\"features\":42}").isEmpty());
    }

    @Test
    public void nullJsonYieldsEmptyList() {
        assertTrue(Traffic.parse(null).isEmpty());
    }

    @Test
    public void realFeedSmoke() throws IOException {
        File f = new File("/tmp/traffic.json");
        assumeTrue(f.exists());
        List<Traffic.Segment> got = Traffic.parse(slurp(f));
        assertFalse("expected segments from the live feed", got.isEmpty());
        for (Traffic.Segment s : got)
            assertTrue("points.length=" + s.points.length, s.points.length >= 2);
    }

    private static String slurp(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
