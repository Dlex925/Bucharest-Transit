package ro.bucharest.transit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class Alerts {
    static final String URL = "https://maps.mo-bi.ro/api/alertdata";

    static final int SEV_MINOR = 0;
    static final int SEV_SLOW = 1;
    static final int SEV_BLOCKING = 2;

    private Alerts() {
    }

    static int severity(Alert a) {
        switch (a.subType.isEmpty() ? a.type : a.subType) {
            case "ROAD_CLOSED":
            case "ROAD_CLOSED_EVENT":
            case "ROAD_CLOSED_CONSTRUCTION":
            case "ACCIDENT":
            case "ACCIDENT_MINOR":
            case "ACCIDENT_MAJOR":
            case "JAM_STAND_STILL_TRAFFIC":
            case "HAZARD_ON_ROAD_CONSTRUCTION":
            case "HAZARD_ON_ROAD_LANE_CLOSED":
            case "HAZARD_ON_ROAD_CAR_STOPPED":
            case "HAZARD_WEATHER_FLOOD":
                return SEV_BLOCKING;
            case "HAZARD_ON_ROAD_POT_HOLE":
            case "HAZARD_ON_ROAD_ROAD_KILL":
            case "HAZARD_ON_SHOULDER":
            case "HAZARD_ON_SHOULDER_CAR_STOPPED":
            case "HAZARD_ON_SHOULDER_ANIMALS":
                return SEV_MINOR;
            default:
                return SEV_SLOW;
        }
    }

    static List<Alert> parse(String json) {
        List<Alert> out = new ArrayList<>();
        JSONObject root = objectOrNull(json);
        if (root == null) return out;
        JSONArray features = root.optJSONArray("features");
        if (features == null) return out;
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.optJSONObject(i);
            if (feature == null) continue;
            JSONObject props = feature.optJSONObject("properties");
            double lat = Double.NaN;
            double lon = Double.NaN;
            if (props != null) {
                if (props.has("lat")) lat = props.optDouble("lat", Double.NaN);
                if (props.has("lon")) lon = props.optDouble("lon", Double.NaN);
            }

            JSONArray coords = feature.optJSONObject("geometry") == null
                    ? null : feature.optJSONObject("geometry").optJSONArray("coordinates");
            if (Double.isNaN(lat) && coords != null && coords.length() >= 2)
                // GeoJSON coordinates are [lon, lat]
                lat = coords.optDouble(1, Double.NaN);
            if (Double.isNaN(lon) && coords != null && coords.length() >= 2)
                lon = coords.optDouble(0, Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
            out.add(new Alert(
                    str(props, "uuid"),
                    str(props, "type"),
                    str(props, "sub_type"),
                    str(props, "street"),
                    str(props, "city"),
                    str(props, "time"),
                    lat, lon));
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

    private static String str(JSONObject props, String key) {
        if (props == null || props.isNull(key)) return "";
        return props.optString(key, "");
    }

    static final class Alert {
        final String uuid;
        final String type;
        final String subType;
        final String street;
        final String city;
        final String time;
        final double lat;
        final double lon;

        Alert(String uuid, String type, String subType, String street, String city,
              String time, double lat, double lon) {
            this.uuid = uuid;
            this.type = type;
            this.subType = subType;
            this.street = street;
            this.city = city;
            this.time = time;
            this.lat = lat;
            this.lon = lon;
        }
    }
}
