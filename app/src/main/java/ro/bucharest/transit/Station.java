package ro.bucharest.transit;

import java.util.List;

final class Station {
    final String name;
    final String address;
    final List<Line> lines;

    Station(String name, String address, List<Line> lines) {
        this.name = name;
        this.address = address;
        this.lines = lines;
    }

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
