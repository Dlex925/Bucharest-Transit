package ro.bucharest.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TimetableTest {
    private static Calendar nowAt(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.SEPTEMBER, 7, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private static final String CALENDAR = join(
            "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date",
            "wkd,1,1,1,1,1,0,0,20260901,20261231",
            "wend,0,0,0,0,0,1,1,20260901,20261231",
            "past,1,1,1,1,1,0,0,20260101,20260906",
            "future,1,1,1,1,1,0,0,20260908,20261231");

    private static final String TRIPS = join(
            "route_id,service_id,trip_id,trip_headsign",
            "r1,wkd,t1,Up",
            "r2,wend,t2,Up",
            "r3,past,t3,Up",
            "r4,future,t4,Up");

    private static final String STOP_TIMES = join(
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence",
            "t1,10:30:00,10:30:00,s1,0",
            "t2,10:30:00,10:30:00,s1,0",
            "t3,10:30:00,10:30:00,s1,0",
            "t4,10:30:00,10:30:00,s1,0");

    @Test
    public void onlyServicesRunningTodayAreConsidered() throws IOException {
        Map<String, List<Integer>> got = departuresOf(zipOf("calendar.txt", CALENDAR, "trips.txt", TRIPS, "stop_times.txt", STOP_TIMES),
                "s1", nowAt(10, 0));

        assertEquals(1, got.size());
        assertTrue(got.containsKey("r1"));
        assertEquals(java.util.Collections.singletonList(10 * 3600 + 30 * 60), got.get("r1"));
    }

    @Test
    public void calendarDatesExceptionsAddAndRemoveForTodayOnly() throws IOException {
        String calendar = join(
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date",
                "base,1,1,1,1,1,1,1,20260901,20261231");

        String calendarDates = join(
                "service_id,date,exception_type",
                "off,20260907,2",
                "on,20260907,1",
                "base,20260906,2");
        String trips = join(
                "route_id,service_id,trip_id,trip_headsign",
                "rA,base,tA,H",
                "rB,off,tB,H",
                "rC,on,tC,H");
        String stopTimes = join(
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence",
                "tA,11:00:00,11:00:00,s1,0",
                "tB,11:00:00,11:00:00,s1,0",
                "tC,11:00:00,11:00:00,s1,0");
        Map<String, List<Integer>> got = departuresOf(zipOf("calendar.txt", calendar, "calendar_dates.txt", calendarDates,
                        "trips.txt", trips, "stop_times.txt", stopTimes),
                "s1", nowAt(10, 0));
        assertTrue(got.containsKey("rA"));
        assertTrue(got.containsKey("rC"));
        assertFalse(got.containsKey("rB"));
        assertEquals(2, got.size());
    }

    @Test
    public void departuresAreSortedAndGroupedByRoute() throws IOException {
        StringBuilder trips = new StringBuilder("route_id,service_id,trip_id,trip_headsign\n");
        StringBuilder stopTimes = new StringBuilder("trip_id,arrival_time,departure_time,stop_id,stop_sequence\n");

        String[] r1 = {"a1,09:00:00", "a2,10:00:00", "a5,10:05:00", "a6,10:10:00",
                "a7,10:15:00", "a4,10:20:00", "a8,10:25:00", "a3,11:30:00", "a9,12:00:00"};
        for (String t : r1) {
            String[] p = t.split(",");
            trips.append("r1,wkd,").append(p[0]).append(",H\n");
            stopTimes.append(p[0]).append(",00:00:00,").append(p[1]).append(",s1,0\n");
        }
        for (String t : new String[]{"b1,10:40:00", "b2,11:15:00"}) {
            String[] p = t.split(",");
            trips.append("r2,wkd,").append(p[0]).append(",H\n");
            stopTimes.append(p[0]).append(",00:00:00,").append(p[1]).append(",s1,0\n");
        }
        Map<String, List<Integer>> got = departuresOf(zipOf("calendar.txt", CALENDAR, "trips.txt", trips.toString(),
                        "stop_times.txt", stopTimes.toString()),
                "s1", nowAt(10, 0));
        assertEquals(2, got.size());

        assertEquals(java.util.Arrays.asList(32400, 36000, 36300, 36600, 36900, 37200, 37500, 41400, 43200),
                got.get("r1"));
        assertEquals(java.util.Arrays.asList(38400, 40500), got.get("r2"));
    }

    @Test
    public void unknownStopYieldsEmptyMap() throws IOException {
        Map<String, List<Integer>> got = departuresOf(zipOf("calendar.txt", CALENDAR, "trips.txt", TRIPS, "stop_times.txt", STOP_TIMES),
                "nope", nowAt(10, 0));
        assertTrue(got.isEmpty());
    }

    @Test
    public void timesBeyond24hAreKeptUnwrapped() throws IOException {
        String trips = join(
                "route_id,service_id,trip_id,trip_headsign",
                "r1,wkd,x1,H",
                "r1,wkd,x2,H");
        String stopTimes = join(
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence",
                "x1,00:00:00,25:10:00,s1,0",
                "x2,00:00:00,09:59:00,s1,0");
        Map<String, List<Integer>> got = departuresOf(zipOf("calendar.txt", CALENDAR, "trips.txt", trips, "stop_times.txt", stopTimes),
                "s1", nowAt(10, 0));

        assertEquals(java.util.Arrays.asList(35940, 90600), got.get("r1"));
    }

    @Test
    public void afterMidnightDeparturesStayComparableToLateEveningNow() throws IOException {
        String trips = join(
                "route_id,service_id,trip_id,trip_headsign",
                "r1,wkd,n1,H",
                "r1,wkd,n2,H",
                "r1,wkd,n3,H");
        String stopTimes = join(
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence",
                "n1,00:00:00,24:30:00,s1,0",
                "n2,00:00:00,23:50:00,s1,0",
                "n3,00:00:00,23:49:59,s1,0");
        Map<String, List<Integer>> got = departuresOf(zipOf("calendar.txt", CALENDAR, "trips.txt", trips, "stop_times.txt", stopTimes),
                "s1", nowAt(23, 50));

        assertEquals(java.util.Arrays.asList(85799, 85800, 88200), got.get("r1"));
    }

    @Test
    public void departuresAlreadyPastAreStillIndexed() throws IOException {
        String trips = join(
                "route_id,service_id,trip_id,trip_headsign",
                "r1,wkd,o1,H");
        String stopTimes = join(
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence",
                "o1,00:00:00,05:00:00,s1,0");
        Map<String, List<Integer>> got = departuresOf(zipOf("calendar.txt", CALENDAR, "trips.txt", trips, "stop_times.txt", stopTimes),
                "s1", nowAt(23, 0));

        assertEquals(java.util.Collections.singletonList(18000), got.get("r1"));
    }

    @Test
    public void missingEntriesYieldEmptyMap() throws IOException {
        assertTrue(departuresOf(zipOf("routes.txt", "route_id,agency_id\n"),
                "s1", nowAt(10, 0)).isEmpty());
    }

    @Test
    public void corruptedZipYieldsEmptyMapInsteadOfThrowing() throws IOException {
        File bad = File.createTempFile("bad", ".zip");
        bad.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(bad)) {
            out.write("definitely not a zip archive".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(departuresOf(bad, "s1", nowAt(10, 0)).isEmpty());
    }

    @Test
    public void realFeedSmoke() {
        File zip = new File("/tmp/gtfs/gtfs.zip");
        assumeTrue(zip.exists());
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 12);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        Map<String, List<Integer>> got = departuresOf(zip, "12067", now);
        assertFalse(got.isEmpty());
        for (List<Integer> times : got.values()) {
            assertTrue(times.size() <= 5);
            for (int i = 1; i < times.size(); i++)
                assertTrue("not ascending: " + times, times.get(i - 1) < times.get(i));
        }
    }

    @Test
    public void indexHoldsTheWholeDaySortedIncludingPast() throws IOException {
        StringBuilder trips = new StringBuilder("route_id,service_id,trip_id,trip_headsign\n");
        StringBuilder stopTimes = new StringBuilder("trip_id,arrival_time,departure_time,stop_id,stop_sequence\n");
        String[] r1 = {"d1,05:00:00", "d2,12:10:00", "d3,12:30:00", "d4,25:10:00"};
        for (String t : r1) {
            String[] p = t.split(",");
            trips.append("r1,wkd,").append(p[0]).append(",H\n");
            stopTimes.append(p[0]).append(",00:00:00,").append(p[1]).append(",s1,0\n");
        }
        File zip = zipOf("calendar.txt", CALENDAR, "trips.txt", trips.toString(),
                "stop_times.txt", stopTimes.toString());
        Map<String, List<Integer>> day = departuresOf(zip, "s1", nowAt(10, 0));

        assertEquals(java.util.Arrays.asList(18000, 43800, 45000, 90600), day.get("r1"));
    }

    @Test
    public void savedIndexReadsBackIdenticalAndOnlyCoversItsOwnArchiveAndDay() throws IOException {
        File zip = zipOf("calendar.txt", CALENDAR, "trips.txt", TRIPS, "stop_times.txt", STOP_TIMES);
        File cache = File.createTempFile("schedule", ".bin");
        cache.deleteOnExit();
        Timetable.Index built = Timetable.index(zip, nowAt(10, 0));
        Timetable.save(cache, built);
        Timetable.Index loaded = Timetable.load(cache);
        assertEquals(built.departures("s1"), loaded.departures("s1"));
        assertTrue(loaded.covers(zip, nowAt(10, 0)));
        Calendar tomorrow = nowAt(10, 0);
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        assertFalse(loaded.covers(zip, tomorrow));

        assertFalse(loaded.covers(zipOf("calendar.txt", CALENDAR), nowAt(10, 0)));
    }

    @Test
    public void corruptedCacheLoadsAsNothingInsteadOfThrowing() throws IOException {
        File cache = File.createTempFile("schedule", ".bin");
        cache.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(cache)) {
            out.write("definitely not an index".getBytes(StandardCharsets.UTF_8));
        }
        assertNull(Timetable.load(cache));
        assertNull(Timetable.load(new File("/nonexistent/schedule.bin")));
    }

    private static Map<String, List<Integer>> departuresOf(File zip, String stopId, Calendar now) {
        return Timetable.index(zip, now).departures(stopId);
    }

    private static String join(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private static File zipOf(String... nameContentPairs) throws IOException {
        File zip = File.createTempFile("gtfs-tt", ".zip");
        zip.deleteOnExit();
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            for (int i = 0; i < nameContentPairs.length; i += 2)
                put(z, nameContentPairs[i], nameContentPairs[i + 1]);
        }
        return zip;
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
