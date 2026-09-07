package ro.bucharest.transit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the live traffic GeoJSON feed ({@link #URL}): a FeatureCollection
 * of LineString features carrying per-street speed and delay. Parsing is pure
 * and total: malformed, empty or null input yields an empty list and any
 * feature that cannot be interpreted is skipped instead of failing the batch.
 */
final class Traffic {
    static final String URL = "https://maps.mo-bi.ro/api/trafficdata";

    private Traffic() {
    }

    /** Parses a GeoJSON FeatureCollection; never throws, never returns null. */
    static List<Segment> parse(String json) {
        List<Segment> out = new ArrayList<>();
        JSONObject root = objectOrNull(json);
        if (root == null) return out;
        JSONArray features = root.optJSONArray("features");
        if (features == null) return out;
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.optJSONObject(i);
            JSONObject geometry = feature == null ? null : feature.optJSONObject("geometry");
            if (geometry == null || !"LineString".equals(geometry.optString("type"))) continue;
            JSONArray coordinates = geometry.optJSONArray("coordinates");
            if (coordinates == null || coordinates.length() < 2) continue;
            double[][] points = new double[coordinates.length()][2];
            boolean ok = true;
            for (int j = 0; j < coordinates.length(); j++) {
                JSONArray p = coordinates.optJSONArray(j);
                if (p == null || p.length() < 2) {
                    ok = false;
                    break;
                }
                // GeoJSON coordinates are [lon, lat]; points are stored as {lat, lon}.
                double lon = p.optDouble(0, Double.NaN);
                double lat = p.optDouble(1, Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    ok = false;
                    break;
                }
                points[j][0] = lat;
                points[j][1] = lon;
            }
            if (!ok) continue;
            JSONObject props = feature.optJSONObject("properties");
            out.add(new Segment(points,
                    props == null ? -1f : (float) props.optDouble("speed_kph", -1),
                    props == null ? -1f : (float) props.optDouble("delay_s", -1),
                    str(props, "street")));
        }
        return out;
    }

    private static JSONObject objectOrNull(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Absent or JSON-null properties become "" (optString can yield "null" on some org.json builds). */
    private static String str(JSONObject props, String key) {
        if (props == null || props.isNull(key)) return "";
        return props.optString(key, "");
    }

    /** One street segment: polyline points and live traffic values. */
    static final class Segment {
        final double[][] points;
        final float speedKph;
        final float delayS;
        final String street;

        Segment(double[][] points, float speedKph, float delayS, String street) {
            this.points = points;
            this.speedKph = speedKph;
            this.delayS = delayS;
            this.street = street;
        }
    }
}
