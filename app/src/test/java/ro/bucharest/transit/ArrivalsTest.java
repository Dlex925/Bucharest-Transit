package ro.bucharest.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArrivalsTest {
    private static final int NOON = 12 * 3600;

    private static Station.Line line(String name, String id, int arrivingTime) {
        return new Station.Line(name, id, "#000000", "", arrivingTime);
    }

    private static Map<String, List<Integer>> day(String routeId, Integer... times) {
        Map<String, List<Integer>> m = new LinkedHashMap<>();
        m.put(routeId, Arrays.asList(times));
        return m;
    }

    private static List<String> names(List<Arrivals.Event> events) {
        List<String> out = new ArrayList<>();
        for (Arrivals.Event e : events) out.add(e.line.name);
        return out;
    }

    @Test
    public void lineWithoutLiveVehicleStillAppearsWithItsSchedule() {
        List<Station.Line> api = Arrays.asList(
                line("1", "r1", 120), line("2", "r2", 300), line("3", "r3", -1));
        List<Arrivals.Event> got = Arrivals.timeline(api,
                day("r3", NOON + 600, NOON + 1800), null, NOON, 12);
        assertTrue(names(got).contains("3"));
        Arrivals.Event three = got.stream().filter(e -> e.line.name.equals("3")).findFirst().get();
        assertEquals(NOON + 600, three.sec);
        assertFalse(three.live);
    }

    @Test
    public void routeMissingFromTheFeedIsTakenFromTheSchedule() {
        List<Arrivals.Event> got = Arrivals.timeline(
                Collections.singletonList(line("1", "r1", 120)),
                day("r9", NOON + 900), null, NOON, 12);
        assertEquals(Arrays.asList("1", "r9"), names(got));
    }

    @Test
    public void lineWithNothingLeftTodayIsListedWithAMarker() {
        List<Arrivals.Event> got = Arrivals.timeline(
                Collections.singletonList(line("3", "r3", -1)),
                day("r3", NOON - 3600), null, NOON, 12);
        assertEquals(1, got.size());
        assertEquals(Arrivals.NO_TIME_NONE, got.get(0).sec);
    }

    @Test
    public void unreadScheduleIsMarkedPendingNotEmpty() {
        List<Arrivals.Event> got = Arrivals.timeline(
                Collections.singletonList(line("3", "r3", -1)), null, null, NOON, 12);
        assertEquals(1, got.size());
        assertEquals(Arrivals.NO_TIME_PENDING, got.get(0).sec);
    }

    @Test
    public void everyLineKeepsARowEvenPastTheCap() {
        List<Station.Line> api = new ArrayList<>();
        Map<String, List<Integer>> schedule = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            api.add(line("L" + i, "r" + i, -1));
            schedule.put("r" + i, Arrays.asList(NOON + 60 * (i + 1), NOON + 3600 + i));
        }
        List<Arrivals.Event> got = Arrivals.timeline(api, schedule, null, NOON, 12);
        assertEquals(20, got.size());
        assertEquals(20, new java.util.HashSet<>(names(got)).size());
    }

    @Test
    public void eventsAreChronologicalAndUndatedRowsSortLast() {
        List<Station.Line> api = Arrays.asList(
                line("late", "r1", 900), line("none", "r2", -1), line("soon", "r3", 60));
        List<Arrivals.Event> got = Arrivals.timeline(api, day("r2"), null, NOON, 12);
        assertEquals(Arrays.asList("soon", "late", "none"), names(got));
    }

    @Test
    public void liveTimesComeBeforeTimetableOnes() {
        List<Arrivals.Event> got = Arrivals.timeline(
                Arrays.asList(line("live", "r1", 600), line("scheduled", "r2", -1)),
                day("r2", NOON + 60), null, NOON, 12);
        assertEquals(Arrays.asList("live", "scheduled"), names(got));
    }

    @Test
    public void scheduleDoesNotDuplicateTheLiveArrival() {
        List<Arrivals.Event> got = Arrivals.timeline(
                Collections.singletonList(line("1", "r1", 300)),
                day("r1", NOON + 320, NOON + 1800), null, NOON, 12);
        assertEquals(2, got.size());
        assertTrue(got.get(0).live);
        assertEquals(NOON + 1800, got.get(1).sec);
    }

    @Test
    public void atMostTwoScheduledRowsPerLine() {
        List<Arrivals.Event> got = Arrivals.timeline(
                Collections.singletonList(line("1", "r1", -1)),
                day("r1", NOON + 60, NOON + 120, NOON + 180, NOON + 240), null, NOON, 12);
        assertEquals(2, got.size());
    }
}
