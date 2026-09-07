package ro.bucharest.transit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the chronological arrivals timeline of one stop, from the live feed
 * and the on-device schedule. Pure: no Android, no I/O.
 *
 * <p>Every line that serves the stop gets a row, even with nothing running: its
 * live ETA when the feed has one, its next scheduled departures otherwise, and
 * a marker when the schedule has nothing left today. Lines the live feed leaves
 * out but the schedule knows about are added, and the cap only ever drops a
 * line's later departures, never its first one.
 */
final class Arrivals {
    /** The schedule was read and has nothing left today. */
    static final int NO_TIME_NONE = -1;
    /** The schedule has not been read yet, so no claim is made. */
    static final int NO_TIME_PENDING = -2;
    private static final int SCHEDULED_PER_LINE = 2;
    /** A scheduled departure this close to the live one is the same vehicle. */
    private static final int SAME_VEHICLE_SEC = 60;

    private Arrivals() {
    }

    /**
     * @param day route id -> all of today's departure seconds, ascending; null
     *            when the schedule has not been read yet
     * @param max soft cap on rows; exceeded only to keep one row per line
     */
    static List<Event> timeline(List<Station.Line> apiLines, Map<String, List<Integer>> day,
                                Map<String, Route> routesById, int nowSec, int max) {
        Map<String, Station.Line> lines = new LinkedHashMap<>();
        for (Station.Line l : apiLines) lines.put(l.id == null ? l.name : l.id, l);
        if (day != null)
            for (String routeId : day.keySet())
                if (!lines.containsKey(routeId)) {
                    Route r = routesById == null ? null : routesById.get(routeId);
                    // unknown routes still appear (raw id, no map chip) rather than disappearing
                    lines.put(routeId, new Station.Line(r == null ? routeId : r.shortName, routeId,
                            r == null ? null : r.color, "", -1));
                }

        List<Event> first = new ArrayList<>();
        List<Event> later = new ArrayList<>();
        for (Station.Line l : lines.values()) {
            List<Event> own = of(l, day, nowSec);
            first.add(own.get(0));
            later.addAll(own.subList(1, own.size()));
        }
        later.sort(Arrivals::byTime);
        List<Event> out = new ArrayList<>(first);
        for (Event e : later) {
            if (out.size() >= Math.max(first.size(), max)) break;
            out.add(e);
        }
        out.sort(Arrivals::byTime);
        return out;
    }

    /** The rows of one line, its soonest departure first; never empty. */
    private static List<Event> of(Station.Line l, Map<String, List<Integer>> day, int nowSec) {
        List<Event> own = new ArrayList<>();
        if (l.arrivingTime >= 0) own.add(new Event(l, nowSec + l.arrivingTime, true));
        List<Integer> scheduled = day == null || l.id == null ? null : day.get(l.id);
        if (scheduled != null) {
            int added = 0;
            for (int t : scheduled) {
                if (added >= SCHEDULED_PER_LINE) break;
                if (t <= nowSec) continue;
                if (l.arrivingTime >= 0 && Math.abs(t - (nowSec + l.arrivingTime)) <= SAME_VEHICLE_SEC) continue;
                own.add(new Event(l, t, false));
                added++;
            }
        }
        if (own.isEmpty()) own.add(new Event(l, day == null ? NO_TIME_PENDING : NO_TIME_NONE, false));
        return own;
    }

    /** Rows with no departure time sort to the bottom of the timeline. */
    private static int byTime(Event a, Event b) {
        if ((a.sec < 0) != (b.sec < 0)) return a.sec < 0 ? 1 : -1;
        return Integer.compare(a.sec, b.sec);
    }

    /** One departure in the timeline; sec is a time of day, or one of the NO_TIME markers. */
    static final class Event {
        final Station.Line line;
        final int sec;
        final boolean live;

        Event(Station.Line line, int sec, boolean live) {
            this.line = line;
            this.sec = sec;
            this.live = live;
        }
    }
}
