package ro.bucharest.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StopsTest {
    private static Stop stop(String id, double lat, double lon) {
        return new Stop(id, "Stop " + id, lat, lon);
    }

    private static Map<String, Stop> mapOf(Stop... stops) {
        Map<String, Stop> m = new HashMap<>();
        for (Stop s : stops) m.put(s.id, s);
        return m;
    }

    private static Map<String, Stop> ladder() {
        return mapOf(
                stop("m300", 44.4327, 26.10),
                stop("m100", 44.4309, 26.10),
                stop("m400", 44.4336, 26.10),
                stop("m200", 44.4318, 26.10));
    }

    @Test
    public void resultsAreSortedAscendingByDistance() {
        Map<String, Stop> stops = mapOf(
                stop("far", 44.4450, 26.1150),
                stop("near", 44.4330, 26.1040),
                stop("mid", 44.4380, 26.1080));
        List<Stops.Near> got = Stops.nearest(stops, 44.4325, 26.1035, 10, 2000);
        assertEquals(3, got.size());
        assertEquals("near", got.get(0).stop.id);
        assertEquals("mid", got.get(1).stop.id);
        assertEquals("far", got.get(2).stop.id);
        for (int i = 1; i < got.size(); i++)
            assertTrue("not ascending", got.get(i).meters >= got.get(i - 1).meters);
        assertEquals(68.3, got.get(0).meters, 2.0);
        assertTrue(got.get(2).meters <= 2000);
    }

    @Test
    public void stopsBeyondMaxMetersAreExcluded() {
        List<Stops.Near> got = Stops.nearest(ladder(), 44.43, 26.10, 10, 250);
        assertEquals(2, got.size());
        assertEquals("m100", got.get(0).stop.id);
        assertEquals("m200", got.get(1).stop.id);
        assertEquals(100.1, got.get(0).meters, 1.0);
        assertEquals(200.2, got.get(1).meters, 1.0);
    }

    @Test
    public void maxResultsCapsTheListToTheNearest() {
        List<Stops.Near> got = Stops.nearest(ladder(), 44.43, 26.10, 3, 1000);
        assertEquals(3, got.size());
        assertEquals("m100", got.get(0).stop.id);
        assertEquals("m200", got.get(1).stop.id);
        assertEquals("m300", got.get(2).stop.id);
    }

    @Test
    public void emptyCatalogYieldsEmptyList() {
        assertTrue(Stops.nearest(new HashMap<String, Stop>(), 44.4, 26.1, 10, 10000).isEmpty());
    }

    @Test
    public void nonPositiveLimitsYieldEmptyList() {
        Map<String, Stop> stops = mapOf(stop("a", 44.43, 26.10));
        assertTrue(Stops.nearest(stops, 44.43, 26.10, 0, 1000).isEmpty());
        assertTrue(Stops.nearest(stops, 44.43, 26.10, -1, 1000).isEmpty());
        assertTrue(Stops.nearest(stops, 44.43, 26.10, 5, 0).isEmpty());
        assertTrue(Stops.nearest(stops, 44.43, 26.10, 5, -1).isEmpty());
    }

    @Test
    public void oneDegreeOfLatitudeIsAbout111Km() {
        List<Stops.Near> got = Stops.nearest(
                mapOf(stop("north", 45.0, 26.0)), 44.0, 26.0, 5, 200000);
        assertEquals(1, got.size());
        assertEquals("north", got.get(0).stop.id);
        assertEquals(111194.9, got.get(0).meters, 2000.0);
    }

    @Test
    public void formatMetersRoundsToTenMetresBelowOneKm() {
        assertEquals("0 m", Stops.formatMeters(0));
        assertEquals("0 m", Stops.formatMeters(4));
        assertEquals("850 m", Stops.formatMeters(854));
        assertEquals("840 m", Stops.formatMeters(843));
        assertEquals("990 m", Stops.formatMeters(986));
    }

    @Test
    public void formatMetersUsesKilometresWithOneDecimalAbove() {
        assertEquals("1.0 km", Stops.formatMeters(1000));
        assertEquals("1.2 km", Stops.formatMeters(1234));
        assertEquals("12.3 km", Stops.formatMeters(12345));
        assertEquals("100.0 km", Stops.formatMeters(99999));
    }
}
