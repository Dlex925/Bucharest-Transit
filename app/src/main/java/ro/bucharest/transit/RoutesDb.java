package ro.bucharest.transit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parses the network database built on device by {@link Gtfs} (routes with
 * per-direction geometry and stop sequences, plus the stop catalog). Also
 * tolerant of the legacy schema without stops.
 */
final class RoutesDb {
    private RoutesDb() {
    }

    static Network parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("routes");
            if (arr == null) return new Network(Collections.emptyList(), Collections.emptyMap());
            List<Route> routes = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject r = arr.getJSONObject(i);
                JSONArray polys = r.optJSONArray("p");
                double[][][] paths = new double[polys == null ? 0 : polys.length()][][];
                for (int j = 0; j < paths.length; j++) {
                    JSONArray pts = polys.getJSONArray(j);
                    paths[j] = new double[pts.length()][2];
                    for (int k = 0; k < pts.length(); k++) {
                        paths[j][k][0] = pts.getJSONArray(k).getDouble(0);
                        paths[j][k][1] = pts.getJSONArray(k).getDouble(1);
                    }
                }
                JSONArray seqs = r.optJSONArray("q");
                String[][] stopIds = new String[seqs == null ? 0 : seqs.length()][];
                for (int j = 0; j < stopIds.length; j++) {
                    JSONArray ids = seqs.getJSONArray(j);
                    stopIds[j] = new String[ids.length()];
                    for (int k = 0; k < ids.length(); k++) stopIds[j][k] = ids.getString(k);
                }
                JSONArray hs = r.optJSONArray("h");
                String[] headsigns = new String[hs == null ? 0 : hs.length()];
                for (int j = 0; j < headsigns.length; j++) headsigns[j] = hs.optString(j, "");
                JSONArray ds = r.optJSONArray("d");
                int[] dirIds = new int[Math.max(headsigns.length, ds == null ? 0 : ds.length())];
                // a snapshot without "d" predates branch variants: index doubles as direction
                for (int j = 0; j < dirIds.length; j++) dirIds[j] = ds == null ? j : ds.optInt(j, j);
                routes.add(new Route(r.getString("i"), r.optString("s"), r.optString("l"),
                        r.isNull("c") ? null : r.optString("c"), r.optInt("t"), paths, stopIds, headsigns, dirIds));
            }
            Map<String, Stop> stops = new HashMap<>();
            JSONObject st = root.optJSONObject("stops");
            if (st != null) {
                for (Iterator<String> it = st.keys(); it.hasNext(); ) {
                    String id = it.next();
                    JSONArray v = st.optJSONArray(id);
                    if (v == null || v.length() < 3) continue;
                    stops.put(id, new Stop(id, v.getString(0), v.getDouble(1), v.getDouble(2)));
                }
            }
            return new Network(routes, stops);
        } catch (Exception e) {
            return new Network(Collections.emptyList(), Collections.emptyMap());
        }
    }
}
