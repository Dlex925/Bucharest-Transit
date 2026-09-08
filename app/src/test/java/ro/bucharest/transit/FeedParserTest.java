package ro.bucharest.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class FeedParserTest {
    private static final String SAMPLE =
            "[{\"id\":\"121\",\"vehicle\":{\"trip\":{\"agencyId\":\"1\",\"routeId\":\"882\","
            + "\"directionId\":0,\"startTime\":\"05:00:00\"},"
            + "\"vehicle\":{\"id\":\"10\",\"label\":\"10\",\"licensePlate\":\"B02213\"},"
            + "\"position\":{\"latitude\":44.4541,\"longitude\":26.0911},"
            + "\"timestamp\":1788724201}},"
            + "{\"id\":\"122\",\"vehicle\":{\"trip\":{\"directionId\":1},"
            + "\"vehicle\":{\"id\":\"11\"},"
            + "\"position\":{\"latitude\":44.45,\"longitude\":26.09}}},"
            + "{\"id\":\"123\",\"vehicle\":{\"trip\":{},\"vehicle\":{\"id\":\"12\"}}}]";

    @Test
    public void parsesRoutePositionPlateAndTimestamp() {
        List<Vehicle> vs = FeedParser.parse(SAMPLE);
        assertEquals(2, vs.size());
        Vehicle v = vs.get(0);
        assertEquals("B02213", v.id);
        assertEquals("882", v.routeId);
        assertEquals(0, v.dir);
        assertEquals(44.4541, v.lat, 1e-9);
        assertEquals(26.0911, v.lon, 1e-9);
        assertEquals(1788724201L, v.seenAt);
    }

    @Test
    public void fallsBackToVehicleIdWhenPlateMissing() {
        List<Vehicle> vs = FeedParser.parse(SAMPLE);
        assertEquals("11", vs.get(1).id);
        assertEquals(1, vs.get(1).dir);
    }

    @Test
    public void toleratesInvalidAndEmptyFeeds() {
        assertTrue(FeedParser.parse("not json").isEmpty());
        assertTrue(FeedParser.parse("[]").isEmpty());
        assertTrue(FeedParser.parse("").isEmpty());
        assertTrue(FeedParser.parse(null).isEmpty());
    }

    @Test
    public void parsesStationArrivalsSortedByTime() {
        Station s = FeedParser.parseStation("{\"name\":\"Piata Unirii\",\"address\":\"Bucuresti\",\"lines\":["
                + "{\"name\":\"7\",\"id\":\"740\",\"type\":\"TRAM\",\"color\":\"#BE1622\",\"directionName\":\"Retie\",\"arrivingTime\":\"240\"},"
                + "{\"name\":\"32\",\"id\":\"701\",\"type\":\"BUS\",\"color\":\"#1976D2\",\"directionName\":\"Unirii\",\"isTimetable\":true},"
                + "{\"name\":\"7\",\"id\":\"740\",\"type\":\"TRAM\",\"color\":\"#BE1622\",\"directionName\":\"Retie\",\"arrivingTime\":30}]}");
        assertEquals("Piata Unirii", s.name);
        assertEquals(3, s.lines.size());
        assertEquals(30, s.lines.get(0).arrivingTime);
        assertEquals(240, s.lines.get(1).arrivingTime);
        assertEquals("740", s.lines.get(0).id);
        assertEquals(-1, s.lines.get(2).arrivingTime);
    }

    @Test
    public void unknownStationAndMalformedReturnNull() {
        assertNull(FeedParser.parseStation("{}"));
        assertNull(FeedParser.parseStation("oops"));
        assertNull(FeedParser.parseStation(null));
    }
}
