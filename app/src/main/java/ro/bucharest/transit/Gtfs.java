package ro.bucharest.transit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Builds the on-device network database from the official TPBI GTFS archive:
 * routes joined with their per-direction shapes and ordered stop sequences
 * (one representative trip per direction), plus the stop catalog. Points are
 * decimated below 20 m spacing and coordinates rounded to 5 decimals. Rows are
 * streamed one line at a time (per-line CSV parsing, a pragmatic tradeoff:
 * no GTFS field in this feed contains an embedded newline), so memory stays
 * bounded regardless of feed size.
 */
final class Gtfs {
    static final String GTFS_URL = "https://gtfs.tpbi.ro/regional/BUCHAREST-REGION.zip";
    private static final double MIN_SPACING_M = 20;

    private interface RowHandler {
        void row(List<String> row);
    }

    static String build(File zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Map<String, List<double[]>> shapes = new HashMap<>();
            eachRow(zip, "shapes.txt", row -> shapes.computeIfAbsent(row.get(0), k -> new ArrayList<>())
                    .add(new double[]{Double.parseDouble(row.get(1)),
                            Double.parseDouble(row.get(2)), Double.parseDouble(row.get(3))}));
            for (List<double[]> pts : shapes.values()) pts.sort(Comparator.comparingDouble(p -> p[2]));

            Map<String, Map<String, String>> variantShape = new HashMap<>(); // route -> variant -> first shapeId
            Map<String, String[]> tripVariant = new HashMap<>();             // tripId -> {routeId, variant}
            Set<String> representativeTrips = new HashSet<>();
            eachRow(zip, "trips.txt", row -> {
                String variant = variantKey(row.get(5), row.size() > 3 ? row.get(3) : "");
                tripVariant.put(row.get(2), new String[]{row.get(0), variant});
                if (row.size() > 7 && !row.get(7).isEmpty()
                        && variantShape.computeIfAbsent(row.get(0), k -> new TreeMap<>())
                                .putIfAbsent(variant, row.get(7)) == null)
                    representativeTrips.add(row.get(2));
            });

            // route -> variant -> ordered stop ids (from the representative trip of that variant)
            Map<String, Map<String, List<String>>> routeStops = new HashMap<>();
            streamStopSequences(zip, tripVariant, representativeTrips, routeStops);

            Map<String, Stop> stops = new HashMap<>();
            eachRow(zip, "stops.txt", row -> stops.put(row.get(0),
                    new Stop(row.get(0), row.get(1), Double.parseDouble(row.get(3)), Double.parseDouble(row.get(4)))));

            List<List<String>> routeRows = new ArrayList<>();
            eachRow(zip, "routes.txt", routeRows::add);

            return emit(routeRows, variantShape, shapes, routeStops, stops);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A branch shares its direction_id with the trunk but ends elsewhere, so the
     * terminus is part of the key: keying on direction_id alone kept one branch
     * of M5 and dropped the other.
     */
    private static String variantKey(String directionId, String headsign) {
        return directionId + '\u0000' + headsign;
    }

    private static int directionOf(String variant) {
        try {
            return Integer.parseInt(variant.substring(0, variant.indexOf('\u0000')).trim());
        } catch (RuntimeException absentOrMalformed) {
            return 0;
        }
    }

    private static String headsignOf(String variant) {
        return variant.substring(variant.indexOf('\u0000') + 1);
    }

    /** stop_times.txt dominates the archive; consume it strictly row by row. */
    private static void streamStopSequences(ZipFile zip, Map<String, String[]> tripRoute,
                                            Set<String> representativeTrips, Map<String, Map<String, List<String>>> routeStops) throws IOException {
        final String[] curTrip = {null};
        final List<String[]>[] cur = new List[]{null};
        Runnable finish = () -> {
            String trip = curTrip[0];
            List<String[]> rows = cur[0];
            if (trip == null || rows == null || !representativeTrips.contains(trip)) return;
            rows.sort(Comparator.comparingInt(r -> Integer.parseInt(r[0])));
            List<String> ids = new ArrayList<>(rows.size());
            for (String[] r : rows) ids.add(r[1]);
            String[] rd = tripRoute.get(trip);
            routeStops.computeIfAbsent(rd[0], k -> new HashMap<>())
                    .computeIfAbsent(rd[1], k -> new ArrayList<>()).addAll(ids);
        };
        eachRow(zip, "stop_times.txt", row -> {
            if (!row.get(0).equals(curTrip[0])) {
                finish.run();
                curTrip[0] = row.get(0);
                cur[0] = new ArrayList<>();
            }
            cur[0].add(new String[]{row.get(4), row.get(3)});
        });
        finish.run();
    }

    private static String emit(List<List<String>> routeRows, Map<String, Map<String, String>> variantShape,
                               Map<String, List<double[]>> shapes,
                               Map<String, Map<String, List<String>>> routeStops, Map<String, Stop> stops) throws JSONException {
        JSONArray out = new JSONArray();
        for (List<String> row : routeRows) {
            Map<String, String> shapeOf = variantShape.get(row.get(0));
            Map<String, List<String>> stopSeq = routeStops.get(row.get(0));
            if (shapeOf == null && stopSeq == null) continue;
            Set<String> variants = new java.util.TreeSet<>();
            if (shapeOf != null) variants.addAll(shapeOf.keySet());
            if (stopSeq != null) variants.addAll(stopSeq.keySet());
            JSONArray polys = new JSONArray();
            JSONArray seqs = new JSONArray();
            JSONArray hs = new JSONArray();
            JSONArray dirs = new JSONArray();
            for (String variant : variants) { // p, q, h and d stay aligned per variant
                polys.put(shapeOf == null ? new JSONArray() : polyline(shapes.get(shapeOf.get(variant))));
                JSONArray ids = new JSONArray();
                if (stopSeq != null && stopSeq.get(variant) != null)
                    for (String sid : stopSeq.get(variant)) ids.put(sid);
                seqs.put(ids);
                hs.put(headsignOf(variant));
                dirs.put(directionOf(variant));
            }
            JSONObject r = new JSONObject();
            r.put("i", row.get(0));
            r.put("s", row.get(2));
            r.put("t", Integer.parseInt(row.get(3)));
            r.put("c", row.get(4).isEmpty() ? JSONObject.NULL : "#" + row.get(4));
            r.put("l", row.size() > 6 ? row.get(6) : "");
            r.put("p", polys);
            r.put("q", seqs);
            r.put("h", hs);
            r.put("d", dirs);
            out.put(r);
        }
        JSONObject stopsJson = new JSONObject();
        for (Stop s : stops.values()) {
            JSONArray v = new JSONArray();
            v.put(s.name);
            v.put(s.lat);
            v.put(s.lon);
            stopsJson.put(s.id, v);
        }
        JSONObject root = new JSONObject();
        root.put("routes", out);
        root.put("stops", stopsJson);
        return root.toString();
    }

    /** One decimated polyline from an ordered point list ({lat, lon, seq} rows). */
    private static JSONArray polyline(List<double[]> pts) throws JSONException {
        JSONArray poly = new JSONArray();
        if (pts == null || pts.size() < 2) return poly;
        double lastLat = 0, lastLon = 0;
        for (double[] p : pts) {
            double lat = Math.round(p[0] * 1e5) / 1e5, lon = Math.round(p[1] * 1e5) / 1e5;
            double dy = (lat - lastLat) * 111320;
            double dx = (lon - lastLon) * 111320 * Math.cos(Math.toRadians(lat));
            if (poly.length() > 0 && dx * dx + dy * dy < MIN_SPACING_M * MIN_SPACING_M) continue;
            lastLat = lat;
            lastLon = lon;
            JSONArray pt = new JSONArray();
            pt.put(lat);
            pt.put(lon);
            poly.put(pt);
        }
        return poly;
    }

    private static void eachRow(ZipFile zip, String name, RowHandler handler) throws IOException {
        ZipEntry e = zip.getEntry(name);
        if (e == null) throw new IOException("missing " + name);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(zip.getInputStream(e), StandardCharsets.UTF_8))) {
            String line;
            for (boolean header = true; (line = r.readLine()) != null; ) {
                if (header) {
                    header = false;
                    continue;
                }
                for (List<String> row : Csv.parse(line)) if (!row.isEmpty()) handler.row(row);
            }
        }
    }
}
