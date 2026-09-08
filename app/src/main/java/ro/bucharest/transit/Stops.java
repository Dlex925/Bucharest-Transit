package ro.bucharest.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class Stops {
    private Stops() {
    }

    private static final double EARTH_RADIUS_M = 6371000.0;

    static List<Near> nearest(Map<String, Stop> stops, double lat, double lon,
                              int maxResults, double maxMeters) {
        List<Near> out = new ArrayList<>();
        if (stops == null || stops.isEmpty() || maxResults <= 0 || maxMeters <= 0) return out;
        for (Stop s : stops.values()) {
            if (s == null) continue;
            double meters = distanceMeters(lat, lon, s.lat, s.lon);
            if (meters <= maxMeters) out.add(new Near(s, meters));
        }
        Collections.sort(out, (a, b) -> {
            int c = Double.compare(a.meters, b.meters);
            return c != 0 ? c : a.stop.id.compareTo(b.stop.id);
        });
        if (out.size() > maxResults) out.subList(maxResults, out.size()).clear();
        return out;
    }

    static String formatMeters(double m) {
        if (m < 1000) return Math.round(m / 10.0) * 10 + " m";
        return String.format(java.util.Locale.US, "%.1f km", m / 1000.0);
    }

    static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double rad = Math.PI / 180.0;
        double dLat = (lat2 - lat1) * rad;
        double dLon = (lon2 - lon1) * rad;
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1 * rad) * Math.cos(lat2 * rad)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        if (a > 1) a = 1;
        if (a < 0) a = 0;
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static final class Near {
        final Stop stop;
        final double meters;

        Near(Stop stop, double meters) {
            this.stop = stop;
            this.meters = meters;
        }
    }
}
