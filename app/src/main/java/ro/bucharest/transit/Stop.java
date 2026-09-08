package ro.bucharest.transit;

final class Stop {
    final String id;
    final String name;
    final double lat;
    final double lon;

    Stop(String id, String name, double lat, double lon) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }
}
