package ro.bucharest.transit;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Today's departures for every stop, read once from the GTFS archive.
 * calendar.txt + calendar_dates.txt decide which services run "today";
 * trips.txt is reduced to trip -> route for those services only; stop_times.txt
 * (the dominant entry, over a million rows) is then streamed a single time for
 * all stops at once, because scanning it again per stop took minutes on a phone.
 * Times may exceed 24h (trips after midnight) and are kept as plain seconds of
 * day, never wrapped.
 */
final class Timetable {
    private Timetable() {
    }

    /**
     * Reads the whole archive once and returns today's departures for every
     * stop. A missing or unreadable archive yields an empty index, never a throw.
     */
    static Index index(File gtfsZip, Calendar now) {
        if (gtfsZip == null || now == null) return Index.EMPTY;
        try (ZipFile zip = new ZipFile(gtfsZip)) {
            Set<String> running = servicesRunningToday(zip, todayNumber(now), dayColumn(now));
            Map<String, String> tripRoute = tripsOfRunningServices(zip, running);
            return collectDepartures(zip, tripRoute, stamp(gtfsZip, now));
        } catch (Exception missingOrCorrupted) {
            return Index.EMPTY;
        }
    }

    private interface RowHandler {
        void row(List<String> row);
    }

    /** Streams one archive entry row by row, skipping the header. A missing or broken entry is not an error. */
    private static void eachRow(ZipFile zip, String name, RowHandler handler) {
        ZipEntry e = zip.getEntry(name);
        if (e == null) return;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(zip.getInputStream(e), StandardCharsets.UTF_8))) {
            String line;
            for (boolean header = true; (line = r.readLine()) != null; ) {
                if (header) {
                    header = false;
                    continue;
                }
                try {
                    for (List<String> row : Csv.parse(line)) if (!row.isEmpty()) handler.row(row);
                } catch (RuntimeException malformedRow) {
                    // skip the bad row, keep streaming
                }
            }
        } catch (IOException unreadableEntry) {
            // tolerate a truncated entry: use whatever was read so far
        }
    }

    /** calendar.txt weekday/date validity, overlaid with today's calendar_dates.txt exceptions. */
    private static Set<String> servicesRunningToday(ZipFile zip, int today, int dayCol) {
        Set<String> services = new HashSet<>();
        eachRow(zip, "calendar.txt", row -> {
            if (row.size() < 10) return;
            if (dayCol >= 1 && dayCol < 8 && "1".equals(row.get(dayCol).trim())
                    && between(today, parseInt(row.get(8), -1), parseInt(row.get(9), -1)))
                services.add(row.get(0));
        });
        eachRow(zip, "calendar_dates.txt", row -> { // optional file: absent in many feeds
            if (row.size() < 3 || parseInt(row.get(1), -1) != today) return;
            String type = row.get(2).trim();
            if (type.equals("1")) services.add(row.get(0));
            else if (type.equals("2")) services.remove(row.get(0));
        });
        return services;
    }

    /** trip_id -> route_id, but only for trips whose service runs today (keeps the map small). */
    private static Map<String, String> tripsOfRunningServices(ZipFile zip, Set<String> running) {
        Map<String, String> tripRoute = new HashMap<>();
        eachRow(zip, "trips.txt", row -> {
            if (row.size() >= 3 && running.contains(row.get(1))) tripRoute.put(row.get(2), row.get(0));
        });
        return tripRoute;
    }

    /**
     * The single pass over stop_times.txt. Departures are packed as
     * {@code seconds << ROUTE_BITS | routeIndex} so a stop costs one int array
     * instead of boxed lists, and sorting the packed values orders them by time.
     */
    private static Index collectDepartures(ZipFile zip, Map<String, String> tripRoute, long stamp) {
        Map<String, Integer> routeIndex = new HashMap<>();
        List<String> routeIds = new ArrayList<>();
        Map<String, IntList> byStop = new HashMap<>();
        eachRow(zip, "stop_times.txt", row -> {
            if (row.size() < 4) return;
            String route = tripRoute.get(row.get(0));
            if (route == null) return;
            int sec = parseTime(row.get(2)); // departure_time; hours may exceed 24
            if (sec < 0 || sec > MAX_SECONDS) return;
            Integer idx = routeIndex.get(route);
            if (idx == null) {
                if (routeIds.size() >= MAX_ROUTES) return;
                idx = routeIds.size();
                routeIndex.put(route, idx);
                routeIds.add(route);
            }
            byStop.computeIfAbsent(row.get(3), k -> new IntList()).add(sec << ROUTE_BITS | idx);
        });
        Map<String, int[]> packed = new HashMap<>(byStop.size() * 2);
        for (Map.Entry<String, IntList> e : byStop.entrySet()) packed.put(e.getKey(), e.getValue().sorted());
        return new Index(routeIds.toArray(new String[0]), packed, stamp);
    }

    /**
     * Which archive and which day an index belongs to. The size identifies the
     * archive: the feed is re-downloaded daily and lands on a new file even when
     * its contents did not change.
     */
    private static long stamp(File gtfsZip, Calendar now) {
        return (long) todayNumber(now) << 32 | (gtfsZip.length() & 0xffffffffL);
    }

    /**
     * The index of the last read, kept so that a restart shows timetables at
     * once instead of re-reading the archive for minutes. Null when there is
     * none, or when the file cannot be read back.
     */
    static Index load(File cache) {
        if (cache == null) return null;
        long limit = cache.length();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cache)))) {
            if (in.readInt() != CACHE_VERSION) return null;
            long stamp = in.readLong();
            String[] routeIds = new String[count(in, limit)];
            for (int i = 0; i < routeIds.length; i++) routeIds[i] = in.readUTF();
            int stops = count(in, limit);
            Map<String, int[]> byStop = new HashMap<>(stops * 2);
            for (int i = 0; i < stops; i++) {
                String stopId = in.readUTF();
                int[] rows = new int[count(in, limit)];
                for (int j = 0; j < rows.length; j++) rows[j] = in.readInt();
                byStop.put(stopId, rows);
            }
            return new Index(routeIds, byStop, stamp);
        } catch (Exception missingOrCorrupted) {
            return null;
        }
    }

    /** Writes the index for the next start; a failed write only costs the next read. */
    static void save(File cache, Index index) {
        if (cache == null) return;
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cache)))) {
            out.writeInt(CACHE_VERSION);
            out.writeLong(index.stamp);
            out.writeInt(index.routeIds.length);
            for (String id : index.routeIds) out.writeUTF(id);
            out.writeInt(index.byStop.size());
            for (Map.Entry<String, int[]> e : index.byStop.entrySet()) {
                out.writeUTF(e.getKey());
                out.writeInt(e.getValue().length);
                for (int row : e.getValue()) out.writeInt(row);
            }
        } catch (IOException unwritable) {
            cache.delete();
        }
    }

    /** An array length read from the cache: four bytes each, so it cannot exceed the file. */
    private static int count(DataInputStream in, long fileLength) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > fileLength) throw new IOException("corrupt cache");
        return n;
    }

    private static final int CACHE_VERSION = 1;

    private static final int ROUTE_BITS = 12;
    private static final int MAX_ROUTES = 1 << ROUTE_BITS;
    private static final int MAX_SECONDS = Integer.MAX_VALUE >>> ROUTE_BITS;

    /** Today's departures for every stop, ready to answer one stop instantly. */
    static final class Index {
        static final Index EMPTY = new Index(new String[0], Collections.emptyMap(), 0);

        private final String[] routeIds;
        private final Map<String, int[]> byStop;
        private final long stamp;

        private Index(String[] routeIds, Map<String, int[]> byStop, long stamp) {
            this.routeIds = routeIds;
            this.byStop = byStop;
            this.stamp = stamp;
        }

        /** True when this index was built from this archive, for this day. */
        boolean covers(File gtfsZip, Calendar now) {
            return gtfsZip != null && now != null && stamp != 0 && stamp == Timetable.stamp(gtfsZip, now);
        }

        /** Route id -> today's departure seconds, ascending. Unknown stop: empty. */
        Map<String, List<Integer>> departures(String stopId) {
            Map<String, List<Integer>> out = new TreeMap<>();
            int[] rows = stopId == null ? null : byStop.get(stopId);
            if (rows == null) return out;
            for (int row : rows)
                out.computeIfAbsent(routeIds[row & (MAX_ROUTES - 1)], k -> new ArrayList<>())
                        .add(row >>> ROUTE_BITS);
            return out;
        }

        boolean isEmpty() {
            return byStop.isEmpty();
        }
    }

    /** Growable int array: 600k departures as boxed Integers is not worth the memory. */
    private static final class IntList {
        private int[] a = new int[8];
        private int n;

        void add(int v) {
            if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }

        int[] sorted() {
            int[] out = java.util.Arrays.copyOf(a, n);
            java.util.Arrays.sort(out);
            return out;
        }
    }

    /** yyyyMMdd number for the given calendar, e.g. 20260907. */
    private static int todayNumber(Calendar now) {
        return now.get(Calendar.YEAR) * 10000 + (now.get(Calendar.MONTH) + 1) * 100
                + now.get(Calendar.DAY_OF_MONTH);
    }

    /** Calendar.DAY_OF_WEEK mapped to the calendar.txt flag column: monday=1 .. sunday=7. */
    private static int dayColumn(Calendar now) {
        int dow = now.get(Calendar.DAY_OF_WEEK); // SUNDAY=1 .. SATURDAY=7
        return dow == Calendar.SUNDAY ? 7 : dow - 1;
    }

    private static boolean between(int value, int from, int to) {
        return from >= 0 && to >= 0 && from <= value && value <= to;
    }

    /** "H:MM:SS" or "HH:MM:SS" (hours may exceed 24) to seconds of day; -1 if malformed. */
    private static int parseTime(String hhmmss) {
        String[] p = hhmmss.trim().split(":");
        if (p.length < 3) return -1;
        try {
            int h = Integer.parseInt(p[0].trim());
            int m = Integer.parseInt(p[1].trim());
            int s = Integer.parseInt(p[2].trim());
            if (h < 0 || m < 0 || m > 59 || s < 0 || s > 59) return -1;
            return h * 3600 + m * 60 + s;
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException malformed) {
            return fallback;
        }
    }
}
