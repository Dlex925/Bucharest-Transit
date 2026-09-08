package ro.bucharest.transit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FeedParser {
    private FeedParser() {
    }

    static List<Vehicle> parse(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            List<Vehicle> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject root = arr.getJSONObject(i);
                JSONObject v = root.optJSONObject("vehicle");
                if (v == null) continue;
                JSONObject pos = v.optJSONObject("position");
                JSONObject veh = v.optJSONObject("vehicle");
                if (pos == null) continue;
                Double lat = optDouble(pos, "latitude"), lon = optDouble(pos, "longitude");
                if (lat == null || lon == null) continue;
                String id = veh != null && veh.optString("licensePlate", "").length() > 0
                        ? veh.optString("licensePlate")
                        : veh != null && veh.optString("id", "").length() > 0
                                ? veh.optString("id") : root.optString("id");
                String routeId = v.optJSONObject("trip") != null
                        ? v.optJSONObject("trip").optString("routeId", null) : null;
                int dir = v.optJSONObject("trip") == null ? -1
                        : v.optJSONObject("trip").optInt("directionId", -1);
                long ts = v.optLong("timestamp");
                out.add(new Vehicle(id, routeId, dir, lat, lon, ts));
            }
            return Collections.unmodifiableList(out);
        } catch (JSONException | NullPointerException e) {
            return Collections.emptyList();
        }
    }

    private static Double optDouble(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optDouble(key);
    }

    static Station parseStation(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!o.has("name")) return null;
            JSONArray lines = o.optJSONArray("lines");
            List<Station.Line> out = new ArrayList<>();
            if (lines != null)
                for (int i = 0; i < lines.length(); i++) {
                    JSONObject l = lines.getJSONObject(i);

                    // arrivingTime comes as a string ("720"); 0 = at stop
                    String t = l.optString("arrivingTime", "").trim();
                    int seconds = t.matches("\\d+") ? Integer.parseInt(t) : -1;
                    out.add(new Station.Line(l.optString("name"), l.optString("id", null),
                            l.optString("color"), l.optString("directionName"), seconds));
                }
            out.sort((a, b) -> {
                if (a.arrivingTime < 0 || b.arrivingTime < 0) return a.arrivingTime < 0 ? 1 : -1;
                return Integer.compare(a.arrivingTime, b.arrivingTime);
            });
            return new Station(o.getString("name"), o.optString("address"), out);
        } catch (Exception e) {
            return null;
        }
    }
}
