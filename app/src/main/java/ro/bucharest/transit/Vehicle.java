package ro.bucharest.transit;

final class Vehicle {
    final String id;
    final String routeId;
    final int dir;
    final double lat;
    final double lon;
    final long seenAt;

    Vehicle(String id, String routeId, int dir, double lat, double lon, long seenAt) {
        this.id = id;
        this.routeId = routeId;
        this.dir = dir;
        this.lat = lat;
        this.lon = lon;
        this.seenAt = seenAt;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Vehicle)) return false;
        Vehicle v = (Vehicle) o;
        return id.equals(v.id) && seenAt == v.seenAt;
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + (int) seenAt;
    }

    @Override
    public String toString() {
        return id + "@" + routeId;
    }
}
