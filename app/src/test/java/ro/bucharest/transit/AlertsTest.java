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

public class AlertsTest {
    private static final String ONE_ALERT = "{\"type\":\"FeatureCollection\",\"features\":["
            + "{\"type\":\"Feature\",\"id\":\"uuid_0\",\"geometry\":{\"type\":\"Point\","
            + "\"coordinates\":[26.134644,44.289793]},"
            + "\"properties\":{\"country\":\"RO\",\"city\":\"Cretesti\",\"street\":\"Str. Tones\","
            + "\"uuid\":\"7eede074-1111-2222-3333-444455556666\",\"description\":null,"
            + "\"lat\":44.290000,\"lon\":26.130000,\"type\":\"HAZARD\","
            + "\"sub_type\":\"HAZARD_ON_ROAD_POT_HOLE\",\"time\":\"2026-09-04 15:51:03\"}}]}";

    @Test
    public void propertiesAreReadAndPropertiesLatLonWinOverGeometry() {
        List<Alerts.Alert> got = Alerts.parse(ONE_ALERT);
        assertEquals(1, got.size());
        Alerts.Alert a = got.get(0);
        assertEquals("7eede074-1111-2222-3333-444455556666", a.uuid);
        assertEquals("HAZARD", a.type);
        assertEquals("HAZARD_ON_ROAD_POT_HOLE", a.subType);
        assertEquals("Str. Tones", a.street);
        assertEquals("Cretesti", a.city);
        assertEquals("2026-09-04 15:51:03", a.time);
        assertEquals(44.29, a.lat, 1e-9);
        assertEquals(26.13, a.lon, 1e-9);
    }

    @Test
    public void geometryCoordinatesAreUsedWhenPropertiesLatLonMissingOrNull() {
        String json = "{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\","
                + "\"coordinates\":[26.134644,44.289793]},"
                + "\"properties\":{\"uuid\":\"no-coords\",\"type\":\"HAZARD\"}},"
                + "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\","
                + "\"coordinates\":[26.13,44.29]},"
                + "\"properties\":{\"uuid\":\"null-coords\",\"lat\":null,\"lon\":null}}]}";
        List<Alerts.Alert> got = Alerts.parse(json);
        assertEquals(2, got.size());

        assertEquals(44.289793, got.get(0).lat, 1e-9);
        assertEquals(26.134644, got.get(0).lon, 1e-9);
        assertEquals(44.29, got.get(1).lat, 1e-9);
        assertEquals(26.13, got.get(1).lon, 1e-9);
    }

    @Test
    public void nullPropertyStringsBecomeEmpty() {
        String json = "{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\","
                + "\"coordinates\":[26.13,44.29]},"
                + "\"properties\":{\"uuid\":null,\"type\":null,\"sub_type\":null,\"street\":null,"
                + "\"city\":null,\"time\":null,\"description\":null,\"lat\":44.29,\"lon\":26.13}}]}";
        List<Alerts.Alert> got = Alerts.parse(json);
        assertEquals(1, got.size());
        Alerts.Alert a = got.get(0);
        assertEquals("", a.uuid);
        assertEquals("", a.type);
        assertEquals("", a.subType);
        assertEquals("", a.street);
        assertEquals("", a.city);
        assertEquals("", a.time);
        assertEquals(44.29, a.lat, 1e-9);
        assertEquals(26.13, a.lon, 1e-9);
    }

    @Test
    public void malformedJsonYieldsEmptyList() {
        assertTrue(Alerts.parse("{oops").isEmpty());
        assertTrue(Alerts.parse("").isEmpty());
        assertTrue(Alerts.parse("[1,2,3]").isEmpty());
        assertTrue(Alerts.parse("{\"type\":\"FeatureCollection\"}").isEmpty());
    }

    @Test
    public void nullJsonYieldsEmptyList() {
        assertTrue(Alerts.parse(null).isEmpty());
    }

    @Test
    public void realFeedSmoke() throws IOException {
        File f = new File("/tmp/alerts.json");
        assumeTrue(f.exists());
        List<Alerts.Alert> got = Alerts.parse(slurp(f));
        assertFalse("expected alerts from the live feed", got.isEmpty());
        for (Alerts.Alert a : got) {
            assertTrue("lat=" + a.lat, Math.abs(a.lat) <= 90);
            assertTrue("lon=" + a.lon, Math.abs(a.lon) <= 180);
        }
    }

    private static String slurp(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
