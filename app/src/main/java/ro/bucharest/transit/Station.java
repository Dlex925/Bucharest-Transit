package ro.bucharest.transit;

import java.util.List;

/** The next-arrivals response for one stop (mo-bi /api/nextArrivals/{stopId}). */
final class Station {
    final String name;
    final String address;
    final List<Line> lines;

    Station(String name, String address, List<Line> lines) {
        this.name = name;
        this.address = address;
        this.lines = lines;
    }

    /** One transit line serving the stop; arrivingTime in seconds, -1 if timetable-only. */
    static final class Line {
        final String name;
        final String id;
        final String color;
        final String directionName;
        final int arrivingTime;

        Line(String name, String id, String color, String directionName, int arrivingTime) {
            this.name = name;
            this.id = id;
            this.color = color;
            this.directionName = directionName;
            this.arrivingTime = arrivingTime;
        }
    }
}
