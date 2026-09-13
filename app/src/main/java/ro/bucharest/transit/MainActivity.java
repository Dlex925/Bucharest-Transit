package ro.bucharest.transit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import org.json.JSONException;
import org.json.JSONObject;

import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String FEED_URL = "https://maps.mo-bi.ro/api/busData";
    private static final String ARRIVALS_URL = "https://maps.mo-bi.ro/api/nextArrivals/";
    private static final long REFRESH_MS = 30000;
    private static final long STALE_SEC = 600;
    private static final long NETWORK_TTL_MS = 3600000;
    private static final int NEARBY_STOPS_MIN_ZOOM = 13;
    private static final int NEARBY_STOPS_MAX = 150;
    private static final int ARRIVALS_MAX = 12;
    private static final int ALERTS_MAX = 200;

    private MapView map;
    private ViewFlipper flipper;
    private ListView linesList;
    private Button tabMap, tabLines;
    private TextView exitChip;
    private ImageView vehiclesBtn, trafficBtn, alertsBtn;
    private TextView trafficStamp;
    private LinearLayout dirChips;
    private View dirChipsBox;
    private MyLocationNewOverlay location;
    private SharedPreferences prefs;

    private final FolderOverlay trafficLayer = new FolderOverlay();
    private final FolderOverlay routeLayer = new FolderOverlay();
    private final FolderOverlay arrowLayer = new FolderOverlay();
    private final FolderOverlay stopLayer = new FolderOverlay();
    private final FolderOverlay alertLayer = new FolderOverlay();
    private final FolderOverlay vehicleLayer = new FolderOverlay();

    private final Map<String, Marker> vehicleMarkers = new HashMap<>();
    private final List<Marker> lineStopMarkers = new ArrayList<>();
    private final List<String> lineStopIds = new ArrayList<>();
    private final List<Marker> nearbyStopMarkers = new ArrayList<>();
    private final Map<String, Bitmap> iconCache = new HashMap<>();
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final ExecutorService feedPool = Executors.newSingleThreadExecutor();
    private final ExecutorService ioPool = Executors.newSingleThreadExecutor();
    private final ExecutorService schedulePool = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean refreshing =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean reading =
            new java.util.concurrent.atomic.AtomicBoolean();

    private Network network = new Network(Collections.emptyList(), Collections.emptyMap());
    private final Map<String, Route> routesById = new HashMap<>();
    private final Map<String, String> liveLineNames = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Entry> lineEntries = new ArrayList<>();
    private final List<Entry> visibleEntries = new ArrayList<>();
    private LinesAdapter linesAdapter;
    private EditText searchBox;
    private List<Route> sortedRoutes = new ArrayList<>();
    private List<Vehicle> lastVehicles = Collections.emptyList();
    private List<Traffic.Segment> lastTraffic = Collections.emptyList();
    private List<Alerts.Alert> lastAlerts = Collections.emptyList();
    private long trafficFetchedAt;
    private String selectedRouteId;
    private Route selectedRoute;
    private int selectedLineColor;
    private int selectedVariant = -1;
    private String selectedStopId;
    private Station lastStation;
    private boolean timetableView;
    private String timetableRouteId;
    private volatile Timetable.Index schedule = Timetable.Index.EMPTY;
    private volatile boolean scheduleRead;
    private volatile boolean scheduleStale;
    private Runnable pendingViewport;
    private AlertDialog stationDialog;
    private LinearLayout stationBody;
    private Stop shownStop;

    private static final class Entry {
        final Route route;
        final String label;

        Entry(Route route, String label) {
            this.route = route;
            this.label = label;
        }
    }

    private final class LinesAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return visibleEntries.size();
        }

        @Override
        public Entry getItem(int position) {
            return visibleEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convertView;
            if (row == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                int pad = dp(6);
                row.setPadding(pad, pad, pad, pad);
                TextView badge = new TextView(MainActivity.this);
                badge.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
                badge.setGravity(android.view.Gravity.CENTER);
                badge.setTextColor(Color.WHITE);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setTextSize(14);
                row.addView(badge);
                TextView name = new TextView(MainActivity.this);
                name.setTextSize(15);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.leftMargin = dp(12);
                name.setLayoutParams(lp);
                row.addView(name);
                row.setTag(new TextView[]{badge, name});
            }
            TextView[] views = (TextView[]) row.getTag();
            Entry e = getItem(position);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(10));
            bg.setColor(colorOf(e.route));
            views[0].setBackground(bg);
            views[0].setText(e.route.shortName);
            views[1].setText(e.label);
            return row;
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshNow();
            timer.postDelayed(this, REFRESH_MS);
        }
    };
    private final Runnable stationTick = new Runnable() {
        @Override
        public void run() {
            if (shownStop != null && !timetableView) loadStation(shownStop);
            timer.postDelayed(this, REFRESH_MS);
        }
    };

    private final Runnable overlayTick = new Runnable() {
        @Override
        public void run() {
            if (prefs.getBoolean("show_traffic", false) || prefs.getBoolean("show_alerts", false))
                loadOverlays();
            timer.postDelayed(this, 300000);
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(wrap(base));
    }

    private static Context wrap(Context base) {
        String lang = base.getSharedPreferences("prefs", MODE_PRIVATE).getString("lang", defaultLang());
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(new Locale(lang));
        return base.createConfigurationContext(cfg);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        loadLiveLineNames();
        IConfigurationProvider cfg = org.osmdroid.config.Configuration.getInstance();
        cfg.setUserAgentValue(getPackageName());
        cfg.setOsmdroidBasePath(new File(getCacheDir(), "osmdroid"));
        cfg.setOsmdroidTileCache(new File(getCacheDir(), "tiles"));
        setContentView(R.layout.activity_main);

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        for (FolderOverlay layer : new FolderOverlay[]{
                trafficLayer, routeLayer, arrowLayer, stopLayer, alertLayer, vehicleLayer})
            map.getOverlays().add(layer);
        map.getOverlays().add(new CopyrightOverlay(this));
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        if (savedInstanceState != null) {
            map.getController().setZoom(savedInstanceState.getDouble("map.zoom"));
            map.getController().setCenter(new GeoPoint(
                    savedInstanceState.getDouble("map.lat"), savedInstanceState.getDouble("map.lon")));
            selectedRouteId = savedInstanceState.getString("sel.route");
            selectedVariant = savedInstanceState.getInt("sel.variant", -1);
            selectedStopId = savedInstanceState.getString("sel.stop");
        } else {
            map.getController().setZoom(12.5d);
            map.getController().setCenter(new GeoPoint(44.4268, 26.1025));
        }
        map.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                scheduleViewportStops();
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                scheduleViewportStops();
                return false;
            }
        });

        flipper = findViewById(R.id.flipper);
        linesList = findViewById(R.id.lines);
        linesAdapter = new LinesAdapter();
        linesList.setAdapter(linesAdapter);
        linesList.setOnItemClickListener((parent, view, pos, id) -> selectRoute(visibleEntries.get(pos)));
        searchBox = findViewById(R.id.search);
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                applyFilter();
            }
        });
        tabMap = findViewById(R.id.tab_map);
        tabLines = findViewById(R.id.tab_lines);
        exitChip = findViewById(R.id.exit_chip);
        exitChip.setOnClickListener(v -> clearSelection());
        dirChips = findViewById(R.id.dir_chips);
        dirChipsBox = findViewById(R.id.dir_chips_box);
        tabMap.setOnClickListener(v -> showTab(0));
        tabLines.setOnClickListener(v -> showTab(1));
        trafficStamp = findViewById(R.id.traffic_stamp);
        buildMapTools();

        showTab(0);
        loadRoutes();
        ensureSchedule();
        tick.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        timer.removeCallbacks(tick);
        tick.run();
        timer.removeCallbacks(overlayTick);
        overlayTick.run();
        if (location != null) location.enableMyLocation();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        org.osmdroid.api.IGeoPoint c = map.getMapCenter();
        outState.putDouble("map.zoom", map.getZoomLevelDouble());
        outState.putDouble("map.lat", c.getLatitude());
        outState.putDouble("map.lon", c.getLongitude());
        outState.putString("sel.route", selectedRouteId);
        outState.putInt("sel.variant", selectedVariant);
        outState.putString("sel.stop", selectedStopId);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        timer.removeCallbacks(tick);
        timer.removeCallbacks(overlayTick);
        timer.removeCallbacks(stationTick);
        map.onPause();
        if (location != null) location.disableMyLocation();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (stationDialog != null) stationDialog.dismiss();
        feedPool.shutdownNow();
        ioPool.shutdownNow();
        schedulePool.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (location == null) {
                location = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
                map.getOverlays().add(location);
            }
            location.enableMyLocation();
            location.enableFollowLocation();
        } else {
            Toast.makeText(this, R.string.location_denied, Toast.LENGTH_SHORT).show();
        }
    }

    private void onLocateClicked() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        if (location == null) {
            location = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
            map.getOverlays().add(location);
        }
        location.enableMyLocation();
        location.enableFollowLocation();
        showNearest();
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void showNearest() {
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        android.location.Location best = null;
        if (lm != null)
            for (String provider : lm.getProviders(true)) {
                android.location.Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        if (best == null) {
            Toast.makeText(this, R.string.no_location, Toast.LENGTH_SHORT).show();
            return;
        }
        List<Stops.Near> near = Stops.nearest(network.stops, best.getLatitude(), best.getLongitude(), 8, 1000);
        if (near.isEmpty()) {
            Toast.makeText(this, R.string.no_nearby, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        box.setPadding(pad, pad, pad, pad);
        ScrollView nearScroll = new ScrollView(this);
        nearScroll.addView(box);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.nearest_title)
                .setView(nearScroll)
                .setPositiveButton(R.string.close, null)
                .create();
        for (Stops.Near n : near) {
            TextView row = text(n.stop.name + "  ·  " + Stops.formatMeters(n.meters), 15, 0, false);
            row.setOnClickListener(v -> {
                dlg.dismiss();
                map.getController().setCenter(new GeoPoint(n.stop.lat, n.stop.lon));
                map.getController().setZoom(16.0);
                showStation(n.stop);
            });
            box.addView(row);
        }
        dlg.show();
    }

    private void showTab(int child) {
        flipper.setDisplayedChild(child);
        boolean mapActive = child == 0;
        if (mapActive) {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
            searchBox.clearFocus();
        }
        tabMap.setTextColor(mapActive ? 0xFF00695C : 0xFF888888);
        tabMap.setTypeface(null, mapActive ? Typeface.BOLD : Typeface.NORMAL);
        tabLines.setTextColor(mapActive ? 0xFF888888 : 0xFF00695C);
        tabLines.setTypeface(null, mapActive ? Typeface.NORMAL : Typeface.BOLD);
    }

    // ---------- static network database: bundled snapshot + self-update ----------

    private void loadRoutes() {
        ioPool.execute(() -> {
            File f = new File(getFilesDir(), "routes.json");
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) {
                    Network n = RoutesDb.parse(Http.readFully(in));
                    if (!n.routes.isEmpty()) {
                        onUi(() -> applyNetwork(n));
                        if (System.currentTimeMillis() - f.lastModified() > NETWORK_TTL_MS) updateRoutes();
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
            try (InputStream in = getAssets().open("routes.json")) {
                Network n = RoutesDb.parse(Http.readFully(in));
                if (!n.routes.isEmpty()) onUi(() -> applyNetwork(n));
            } catch (Exception ignored) {
            }
            updateRoutes();
        });
    }

    private void updateRoutes() {
        if (!refreshing.compareAndSet(false, true)) return;
        ioPool.execute(() -> {
            try {
                File partial = new File(getCacheDir(), "gtfs.zip.part");
                File zip = new File(getCacheDir(), "gtfs.zip");
                File routes = new File(getFilesDir(), "routes.json");
                InputStream feed = Http.stream(Gtfs.GTFS_URL,
                        routes.exists() && zip.exists() ? routes.lastModified() : 0);
                if (feed == null) {
                    routes.setLastModified(System.currentTimeMillis());
                    return;
                }
                try (InputStream in = feed;
                     FileOutputStream out = new FileOutputStream(partial)) {
                    byte[] buf = new byte[8192];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                }
                String json = Gtfs.build(partial);
                if (!zip.delete() && zip.exists()) throw new IOException("stale archive");
                if (!partial.renameTo(zip)) throw new IOException("rename failed");
                File tmp = new File(getFilesDir(), "routes.json.tmp");
                try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                    w.write(json);
                }
                if (!tmp.renameTo(routes)) throw new IOException("rename failed");
                final Network n = RoutesDb.parse(json);
                readSchedule();
                onUi(() -> {
                    applyNetwork(n);
                    if (shownStop != null) {
                        if (timetableView) showTimetable(timetableRouteId);
                        else loadStation(shownStop);
                    }
                });
            } catch (Exception e) {
                onUi(() -> {
                    if (timetableView && shownStop != null && stationBody != null)
                        renderTimetableGrid(shownStop, timetableRouteId, null);
                    Toast.makeText(this, R.string.feed_error, Toast.LENGTH_LONG).show();
                });
            } finally {
                refreshing.set(false);
            }
        });
    }

    private void applyNetwork(Network n) {
        network = n;
        routesById.clear();
        sortedRoutes = new ArrayList<>(n.routes);
        sortedRoutes.sort((a, b) -> {
            int d = rank(a.type) - rank(b.type);
            if (d != 0) return d;
            d = leadingInt(a.shortName) - leadingInt(b.shortName);
            return d != 0 ? d : a.shortName.compareTo(b.shortName);
        });
        lineEntries.clear();
        for (Route r : sortedRoutes) {
            routesById.put(r.id, r);
            lineEntries.add(new Entry(r, label(r)));
        }
        applyFilter();
        if (selectedRouteId != null) {
            Route r = routesById.get(selectedRouteId);
            if (r == null) clearSelection();
            else drawRoute(r);
        }
        scheduleViewportStops();
    }

    private String label(Route r) {
        StringBuilder sb = new StringBuilder(kind(r)).append(' ').append(r.shortName);
        List<String> termini = new ArrayList<>();
        for (String h : r.headsigns) if (h != null && !h.isEmpty()) termini.add(h);
        if (termini.size() == 1) sb.append(' ').append(getString(R.string.to_terminus, termini.get(0)));
        else if (termini.size() >= 2) sb.append(" ⇄ ").append(termini.get(0)).append(" / ").append(termini.get(1));
        return sb.toString();
    }

    private void applyFilter() {
        String q = searchBox == null || searchBox.getText() == null
                ? "" : searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleEntries.clear();
        for (Entry e : lineEntries)
            if (q.isEmpty() || (e.route.shortName + " " + e.label).toLowerCase(Locale.ROOT).contains(q))
                visibleEntries.add(e);
        if (linesAdapter != null) linesAdapter.notifyDataSetChanged();
    }

    private void selectRoute(Entry e) {
        if (e.route.id.equals(selectedRouteId)) {
            clearSelection();
            return;
        }
        selectedRouteId = e.route.id;
        selectedRoute = e.route;
        drawRoute(e.route);
        showTab(0);
        renderVehicles(lastVehicles);
        refreshNow();
    }

    private void drawRoute(Route r) {
        selectedRoute = r; // also valid on restore-from-rotation, where selectRoute() never ran
        routeLayer.getItems().clear();
        arrowLayer.getItems().clear();
        for (Marker m : lineStopMarkers) stopLayer.remove(m);
        lineStopMarkers.clear();
        lineStopIds.clear();
        selectedLineColor = colorOf(r);
        if (selectedVariant >= r.variantCount()) selectedVariant = -1;
        List<Integer> drawn = new ArrayList<>();
        if (selectedVariant < 0) for (int i = 0; i < r.variantCount(); i++) drawn.add(i);
        else drawn.add(selectedVariant);
        List<GeoPoint> all = new ArrayList<>();
        for (int d : drawn) {
            if (d >= r.paths.length) continue;
            List<GeoPoint> pts = new ArrayList<>(r.paths[d].length);
            for (double[] pt : r.paths[d]) pts.add(new GeoPoint(pt[0], pt[1]));
            if (pts.size() < 2) continue;
            all.addAll(pts);
            Polyline line = new Polyline();
            line.setPoints(pts);
            line.getOutlinePaint().setStrokeWidth(7 * getResources().getDisplayMetrics().density);
            line.getOutlinePaint().setColor(selectedLineColor);
            routeLayer.add(line);
            arrowLayer.add(new DirectionArrows(pts, selectedLineColor, dp(70), dp(14),
                    drawn.size() > 1 ? (directionOf(r, d) == 0 ? dp(3) : -dp(3)) : 0));
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (int d : drawn) {
            if (d < r.stopIds.length) Collections.addAll(ids, r.stopIds[d]);
        }
        for (String sid : ids) {
            Stop s = network.stops.get(sid);
            if (s == null) continue;
            lineStopIds.add(sid);
            lineStopMarkers.add(stopMarker(s, selectedLineColor));
        }
        exitChip.setText(getString(R.string.exit_line, r.shortName));
        exitChip.setVisibility(View.VISIBLE);
        updateDirChips(r);
        if (!all.isEmpty()) map.zoomToBoundingBox(BoundingBox.fromGeoPoints(all), false);
        map.invalidate();
    }

    private void updateDirChips(Route r) {
        dirChips.removeAllViews();
        int variants = r == null ? 0 : r.variantCount();
        if (variants < 2) {
            dirChipsBox.setVisibility(View.GONE);
            return;
        }
        dirChipsBox.setVisibility(View.VISIBLE);
        for (int i = 0; i < variants; i++) {
            final int variant = i;
            String terminus = terminusLabel(r, i);
            TextView chip = new TextView(this);
            chip.setText(getString(R.string.to_terminus,
                    terminus.isEmpty() ? String.valueOf(i + 1) : terminus));
            chip.setTypeface(null, Typeface.BOLD);
            chip.setPadding(dp(12), dp(7), dp(12), dp(7));
            boolean active = selectedVariant == i;
            chip.setBackground(getResources().getDrawable(active
                    ? R.drawable.chip_bg_active : R.drawable.chip_bg, getTheme()));
            chip.setTextColor(active ? Color.WHITE : 0xFF00695C);
            chip.setOnClickListener(v -> toggleVariant(variant));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            boolean land = getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            if (i > 0) {
                if (land) lp.leftMargin = dp(6);
                else lp.topMargin = dp(6);
            }
            dirChips.addView(chip, lp);
        }
    }

    private String terminusLabel(Route r, int variant) {
        if (variant < r.headsigns.length && !r.headsigns[variant].isEmpty()) return r.headsigns[variant];
        if (variant < r.stopIds.length && r.stopIds[variant].length > 0) {
            Stop last = network.stops.get(r.stopIds[variant][r.stopIds[variant].length - 1]);
            if (last != null) return last.name;
        }
        return "";
    }

    private static int directionOf(Route r, int variant) {
        return variant < r.dirIds.length ? r.dirIds[variant] : variant;
    }

    private void toggleVariant(int variant) {
        selectedVariant = selectedVariant == variant ? -1 : variant;
        if (selectedRoute != null) drawRoute(selectedRoute);
        renderVehicles(lastVehicles);
    }

    private void clearSelection() {
        selectedRouteId = null;
        selectedRoute = null;
        selectedVariant = -1;
        routeLayer.getItems().clear();
        arrowLayer.getItems().clear();
        for (Marker m : lineStopMarkers) stopLayer.remove(m);
        lineStopMarkers.clear();
        lineStopIds.clear();
        exitChip.setVisibility(View.GONE);
        dirChipsBox.setVisibility(View.GONE);
        renderVehicles(lastVehicles);
        scheduleViewportStops();
    }

    // ---------- live vehicles ----------

    private void refreshNow() {
        feedPool.execute(() -> {
            try {
                List<Vehicle> vehicles = FeedParser.parse(Http.get(FEED_URL));
                onUi(() -> {
                    lastVehicles = vehicles;
                    renderVehicles(vehicles);
                });
            } catch (Exception e) {
                onUi(() -> Toast.makeText(this, R.string.feed_error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void renderVehicles(List<Vehicle> vehicles) {
        List<Vehicle> live = Vehicles.fresh(vehicles, System.currentTimeMillis() / 1000, STALE_SEC);
        boolean showAll = prefs.getBoolean("show_all_vehicles", true);
        Map<String, Vehicle> visible = new LinkedHashMap<>();
        for (Vehicle v : live) {
            if (v.id.isEmpty()) continue;
            boolean on = onSelection(v);
            if (!on && !showAll) continue;
            visible.put(v.id, v);
        }
        int dim = 0xFF9E9E9E;
        for (Vehicle v : visible.values()) {
            Marker m = vehicleMarkers.get(v.id);
            if (m == null) {
                m = new Marker(map);
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                vehicleLayer.add(m);
                vehicleMarkers.put(v.id, m);
            }
            Route r = v.routeId == null ? null : routesById.get(v.routeId);
            String name = r != null ? r.shortName
                    : v.routeId == null ? null : liveLineNames.get(v.routeId);
            boolean on = onSelection(v);
            Bitmap icon;
            if (r != null) icon = badge(on ? colorOf(r) : dim, r.shortName);
            else if (name != null) icon = badge(dim, name);
            else icon = dot(dim, Color.WHITE, dp(11));
            m.setIcon(new BitmapDrawable(getResources(), icon));
            m.setTitle(name == null ? getString(R.string.unknown_line) : name);
            long age = v.seenAt > 0 ? Math.max(0, System.currentTimeMillis() / 1000 - v.seenAt) : -1;
            m.setSnippet(v.id + (age >= 0 ? " · " + age + "s" : ""));
            m.setPosition(new GeoPoint(v.lat, v.lon));
        }
        for (String gone : new ArrayList<>(vehicleMarkers.keySet())) {
            if (!visible.containsKey(gone)) {
                vehicleLayer.remove(vehicleMarkers.get(gone));
                vehicleMarkers.remove(gone);
            }
        }
        map.invalidate();
    }

    private boolean onSelection(Vehicle v) {
        return selectedRoute != null && selectedRoute.id.equals(v.routeId)
                && (selectedVariant < 0 || v.dir < 0 || v.dir == directionOf(selectedRoute, selectedVariant));
    }

    @Override
    public void onBackPressed() {
        if (flipper.getDisplayedChild() == 1) {
            showTab(0);
        } else if (selectedRoute != null) {
            clearSelection();
        } else {
            moveTaskToBack(true);
        }
    }

    // ---------- stops ----------

    private Marker stopMarker(Stop s, int ringColor) {
        boolean selected = s.id.equals(selectedStopId);
        Marker m = new Marker(map);
        m.setPosition(new GeoPoint(s.lat, s.lon));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        m.setIcon(new BitmapDrawable(getResources(), selected
                ? dot(0xFF00695C, Color.WHITE, dp(10)) : dot(Color.WHITE, ringColor, dp(7))));
        m.setTitle(s.name);
        m.setOnMarkerClickListener((mk, mv) -> {
            showStation(s);
            return true;
        });
        stopLayer.add(m);
        return m;
    }

    private void scheduleViewportStops() {
        if (pendingViewport != null) return;
        pendingViewport = () -> {
            pendingViewport = null;
            rebuildNearbyStops();
        };
        timer.postDelayed(pendingViewport, 400);
    }

    private void rebuildNearbyStops() {
        for (Marker m : nearbyStopMarkers) stopLayer.remove(m);
        nearbyStopMarkers.clear();
        if (selectedRouteId != null || map.getZoomLevelDouble() < NEARBY_STOPS_MIN_ZOOM) {
            map.invalidate();
            return;
        }
        BoundingBox bb = map.getBoundingBox();
        int added = 0;
        for (Stop s : network.stops.values()) {
            if (bb.contains(s.lat, s.lon)) {
                nearbyStopMarkers.add(stopMarker(s, 0xFF607D8B));
                if (++added >= NEARBY_STOPS_MAX) break;
            }
        }
        map.invalidate();
    }

    private void setSelectedStop(Stop s) {
        boolean changed = !s.id.equals(selectedStopId);
        selectedStopId = s.id;
        if (changed) {
            rebuildNearbyStops();
            for (int i = 0; i < lineStopIds.size(); i++) {
                boolean sel = lineStopIds.get(i).equals(selectedStopId);
                lineStopMarkers.get(i).setIcon(new BitmapDrawable(getResources(), sel
                        ? dot(0xFF00695C, Color.WHITE, dp(10))
                        : dot(Color.WHITE, selectedLineColor, dp(7))));
            }
        }
        map.invalidate();
    }

    // ---------- next arrivals ----------

    private void showStation(Stop s) {
        setSelectedStop(s);
        if (stationDialog != null) stationDialog.dismiss();
        shownStop = s;
        timetableView = false;
        stationBody = new LinearLayout(this);
        stationBody.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        stationBody.setPadding(pad, pad, pad, pad);
        TextView loading = new TextView(this);
        loading.setText(getString(R.string.station_loading));
        stationBody.addView(loading);
        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.addView(stationBody);
        stationDialog = new AlertDialog.Builder(this)
                .setTitle(s.name)
                .setView(dialogScroll)
                .setPositiveButton(R.string.close, null)
                .create();
        stationDialog.setOnDismissListener(d -> {
            timer.removeCallbacks(stationTick);
            stationDialog = null;
            stationBody = null;
            shownStop = null;
        });
        stationDialog.show();
        loadStation(s);
        timer.postDelayed(stationTick, REFRESH_MS);
    }

    private void loadLiveLineNames() {
        try {
            JSONObject saved = new JSONObject(prefs.getString("live_lines", "{}"));
            for (Iterator<String> it = saved.keys(); it.hasNext(); ) {
                String id = it.next();
                liveLineNames.put(id, saved.getString(id));
            }
        } catch (JSONException ignored) {
        }
    }

    private void learnLineNames(Station st) {
        boolean added = false;
        for (Station.Line l : st.lines)
            if (l.id != null && !l.id.isEmpty() && l.name != null && !l.name.isEmpty()
                    && !l.name.equals(liveLineNames.put(l.id, l.name))) added = true;
        if (added) prefs.edit().putString("live_lines", new JSONObject(liveLineNames).toString()).apply();
    }

    private void loadStation(Stop s) {
        feedPool.execute(() -> {
            Station st = null;
            try {
                st = FeedParser.parseStation(Http.get(ARRIVALS_URL + s.id));
            } catch (Exception ignored) {
            }
            final Station parsed = st;
            if (parsed != null) learnLineNames(parsed);
            onUi(() -> {
                if (stationBody != null && shownStop == s && !timetableView) renderStation(parsed, s);
            });
            ensureSchedule();
        });
    }

    private void renderStation(Station st, Stop s) {
        if (timetableView) {
            renderTimetableGrid(s, timetableRouteId, dayTimetable(s));
            return;
        }
        lastStation = st;
        renderArrivals(st, s);
    }

    private void renderArrivals(Station st, Stop s) {
        stationBody.removeAllViews();
        if (st != null && !st.address.isEmpty() && !st.address.equals(st.name))
            stationBody.addView(text(st.address, 13, 0xFF888888, false));
        int nowSec = nowSeconds();
        List<Arrivals.Event> timeline = Arrivals.timeline(
                st == null ? Collections.emptyList() : st.lines,
                dayTimetable(s), routesById, nowSec, ARRIVALS_MAX);
        if (timeline.isEmpty()) {
            stationBody.addView(text(getString(st == null
                    ? R.string.station_unavailable : R.string.no_lines), 15, 0, false));
            return;
        }
        for (Arrivals.Event ev : timeline) stationBody.addView(arrivalRow(ev, nowSec));
    }

    private static int nowSeconds() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60;
    }

    private LinearLayout arrivalRow(Arrivals.Event ev, int nowSec) {
        Station.Line l = ev.line;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        StringBuilder head = new StringBuilder(l.name);
        if (!l.directionName.isEmpty()) head.append(" → ").append(l.directionName);
        TextView title = new TextView(this);
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        title.setText(head.toString());
        row.addView(title);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.HORIZONTAL);
        details.setGravity(android.view.Gravity.CENTER_VERTICAL);
        details.setPadding(0, dp(4), 0, 0);
        TextView when = new TextView(this);
        when.setTextSize(14);
        if (ev.sec < 0)
            when.setText(getString(ev.sec == Arrivals.NO_TIME_PENDING
                    ? R.string.schedule_loading : R.string.no_departures));
        else if (ev.live && ev.sec - nowSec < 60)
            when.setText(getString(R.string.in_station) + " · " + getString(R.string.live));
        else {
            int min = Math.max(1, (ev.sec - nowSec + 59) / 60);
            when.setText(getString(R.string.eta_min, min) + " · "
                    + getString(ev.live ? R.string.live : R.string.from_timetable));
        }
        details.addView(when, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (l.id != null && routesById.containsKey(l.id)) {
            details.addView(chip(R.string.chip_timetable, v -> showTimetable(l.id)));
            details.addView(chip(R.string.chip_map, v -> {
                if (stationDialog != null) stationDialog.dismiss();
                Route r = routesById.get(l.id);
                selectRoute(new Entry(r, label(r)));
            }));
        }
        row.addView(details);
        return row;
    }

    private TextView chip(int label, View.OnClickListener onClick) {
        TextView c = new TextView(this);
        c.setText(label);
        c.setTextSize(13);
        c.setTypeface(null, Typeface.BOLD);
        c.setTextColor(0xFF00695C);
        c.setBackgroundResource(R.drawable.chip_bg);
        c.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        c.setLayoutParams(lp);
        c.setOnClickListener(onClick);
        return c;
    }

    private void showTimetable(String routeId) {
        timetableView = true;
        timetableRouteId = routeId;
        final Stop s = shownStop;
        stationBody.removeAllViews();
        stationBody.addView(text(getString(R.string.station_loading), 15, 0, false));
        stationBody.addView(text(getString(R.string.timetable_loading_hint), 12, 0xFF999999, false));
        if (!scheduleRead) {
            ensureSchedule();
            return;
        }
        renderTimetableGrid(s, routeId, dayTimetable(s));
    }

    private Map<String, List<Integer>> dayTimetable(Stop s) {
        return scheduleRead ? schedule.departures(s.id) : null;
    }

    private void ensureSchedule() {
        if (scheduleRead) return;
        if (!new File(getCacheDir(), "gtfs.zip").exists()) {
            updateRoutes();
            return;
        }
        if (reading.compareAndSet(false, true))
            schedulePool.execute(() -> {
                try {
                    readSchedule();
                } finally {
                    reading.set(false);
                }
            });
    }

    private synchronized void readSchedule() {
        File zip = new File(getCacheDir(), "gtfs.zip");
        File cache = new File(getCacheDir(), "schedule.bin");
        Calendar now = Calendar.getInstance();
        Timetable.Index cached = Timetable.load(cache);
        boolean current = cached != null && cached.covers(zip, now);
        if (cached != null) publishSchedule(cached, !current);
        if (current || !zip.exists()) return;
        Timetable.Index read = Timetable.index(zip, now);
        if (read.isEmpty()) return;
        Timetable.save(cache, read);
        publishSchedule(read, false);
    }

    private void publishSchedule(Timetable.Index read, boolean stale) {
        schedule = read;
        scheduleStale = stale;
        scheduleRead = true;
        onUi(() -> {
            if (stationBody == null || shownStop == null) return;
            if (timetableView) renderTimetableGrid(shownStop, timetableRouteId, dayTimetable(shownStop));
            else renderStation(lastStation, shownStop);
        });
    }

    private void renderTimetableGrid(Stop s, String routeId, Map<String, List<Integer>> day) {
        stationBody.removeAllViews();
        TextView back = text("← " + getString(R.string.tab_back), 14, 0xFF00695C, true);
        back.setOnClickListener(v -> {
            timetableView = false;
            renderStation(lastStation, s);
        });
        stationBody.addView(back);
        Route r = routesById.get(routeId);
        stationBody.addView(text(r == null ? routeId : kind(r) + " " + r.shortName, 16, 0, true));
        if (scheduleStale)
            stationBody.addView(text(getString(R.string.timetable_stale), 12, 0xFF999999, false));
        List<Integer> times = day == null ? null : day.get(routeId);
        if (times == null || times.isEmpty()) {
            stationBody.addView(text(getString(R.string.timetable_unavailable), 14, 0, false));
            return;
        }
        TreeMap<Integer, StringBuilder> rows = new TreeMap<>();
        for (int sec : times) {
            StringBuilder row = rows.computeIfAbsent(sec / 3600, k -> new StringBuilder());
            row.append(String.format(Locale.US, " %02d", (sec % 3600) / 60));
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (Map.Entry<Integer, StringBuilder> e : rows.entrySet()) {
            int hourStart = sb.length();
            sb.append(String.format(Locale.US, "%02d│", e.getKey()));
            sb.setSpan(new StyleSpan(Typeface.BOLD), hourStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(0xFF00695C), hourStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(e.getValue()).append('\n');
        }
        TextView grid = new TextView(this);
        grid.setTypeface(Typeface.MONOSPACE);
        grid.setTextSize(15);
        grid.setText(sb);
        HorizontalScrollView across = new HorizontalScrollView(this);
        across.addView(grid);
        ScrollView sc = new ScrollView(this);
        sc.addView(across);
        stationBody.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320)));
        stationBody.addView(text(getString(R.string.from_timetable), 12, 0xFF888888, false));
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        if (color != 0) tv.setTextColor(color);
        tv.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setPadding(0, dp(6), 0, dp(6));
        return tv;
    }

    // ---------- traffic & incident overlays ----------

    private void loadOverlays() {
        final boolean wantTraffic = prefs.getBoolean("show_traffic", false);
        final boolean wantAlerts = prefs.getBoolean("show_alerts", false);
        ioPool.execute(() -> {
            List<Traffic.Segment> traffic = Collections.emptyList();
            List<Alerts.Alert> alerts = Collections.emptyList();
            try {
                if (wantTraffic) traffic = Traffic.parse(Http.get(Traffic.URL));
            } catch (Exception ignored) {
            }
            try {
                if (wantAlerts) alerts = Alerts.parse(Http.get(Alerts.URL));
            } catch (Exception ignored) {
            }
            final List<Traffic.Segment> t = traffic;
            final List<Alerts.Alert> a = alerts;
            onUi(() -> {
                if (wantTraffic && !t.isEmpty()) {
                    lastTraffic = t;
                    trafficFetchedAt = System.currentTimeMillis();
                }
                if (wantAlerts && !a.isEmpty()) lastAlerts = a;
                renderOverlays();
            });
        });
    }

    private void renderOverlays() {
        clearOverlays();
        boolean traffic = prefs.getBoolean("show_traffic", false);
        if (traffic)
            for (Traffic.Segment seg : lastTraffic) {
                if (seg.points.length < 2) continue;
                List<GeoPoint> pts = new ArrayList<>(seg.points.length);
                for (double[] pt : seg.points) pts.add(new GeoPoint(pt[0], pt[1]));
                Polyline line = new Polyline();
                line.setPoints(pts);
                line.getOutlinePaint().setStrokeWidth(4 * getResources().getDisplayMetrics().density);
                line.getOutlinePaint().setColor(trafficColor(seg.delayS));
                trafficLayer.add(line);
            }
        if (traffic && trafficFetchedAt > 0) {
            trafficStamp.setText(getString(R.string.traffic_stamp,
                    DateFormat.getTimeFormat(this).format(new Date(trafficFetchedAt))));
            trafficStamp.setVisibility(View.VISIBLE);
        } else {
            trafficStamp.setVisibility(View.GONE);
        }
        if (prefs.getBoolean("show_alerts", false)) {
            int shown = 0;
            for (Alerts.Alert a : lastAlerts) {
                if (shown >= ALERTS_MAX) break;
                int severity = Alerts.severity(a);
                if (!prefs.getBoolean("alerts_sev" + severity, true)) continue;
                Marker m = new Marker(map);
                m.setPosition(new GeoPoint(a.lat, a.lon));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                m.setIcon(new BitmapDrawable(getResources(),
                        dot(alertColor(severity), Color.WHITE, dp(9))));
                m.setTitle(alertLabel(a));
                m.setSnippet(a.street + (a.time.isEmpty() ? "" : " · " + a.time));
                alertLayer.add(m);
                shown++;
            }
        }
        map.invalidate();
    }

    private static int alertColor(int severity) {
        return severity == Alerts.SEV_BLOCKING ? 0xFFC62828
                : severity == Alerts.SEV_SLOW ? 0xFFEF6C00 : 0xFFFBC02D;
    }

    private void clearOverlays() {
        alertLayer.closeAllInfoWindows();
        trafficLayer.getItems().clear();
        alertLayer.getItems().clear();
        map.invalidate();
    }

    private void onUi(Runnable r) {
        runOnUiThread(() -> {
            if (!isDestroyed() && !isFinishing()) r.run();
        });
    }

    private String alertLabel(Alerts.Alert a) {
        String key = a.subType.isEmpty() ? a.type : a.subType;
        switch (key) {
            case "ACCIDENT":
            case "ACCIDENT_MINOR":
            case "ACCIDENT_MAJOR":
                return getString(R.string.alert_accident);
            case "JAM":
            case "JAM_HEAVY_TRAFFIC":
                return getString(R.string.alert_jam_heavy);
            case "JAM_STAND_STILL_TRAFFIC":
                return getString(R.string.alert_jam_standstill);
            case "JAM_MODERATE_TRAFFIC":
            case "JAM_LIGHT_TRAFFIC":
                return getString(R.string.alert_jam_moderate);
            case "ROAD_CLOSED":
                return getString(R.string.alert_road_closed);
            case "ROAD_CLOSED_EVENT":
                return getString(R.string.alert_road_closed_event);
            case "ROAD_CLOSED_CONSTRUCTION":
            case "HAZARD_ON_ROAD_CONSTRUCTION":
                return getString(R.string.alert_construction);
            case "HAZARD":
            case "HAZARD_ON_ROAD":
                return getString(R.string.alert_hazard);
            case "HAZARD_ON_ROAD_POT_HOLE":
                return getString(R.string.alert_pot_hole);
            case "HAZARD_ON_ROAD_LANE_CLOSED":
                return getString(R.string.alert_lane_closed);
            case "HAZARD_ON_ROAD_TRAFFIC_LIGHT_FAULT":
                return getString(R.string.alert_traffic_light);
            case "HAZARD_ON_ROAD_OBJECT":
                return getString(R.string.alert_object);
            case "HAZARD_ON_ROAD_CAR_STOPPED":
                return getString(R.string.alert_car_stopped);
            case "HAZARD_ON_SHOULDER_CAR_STOPPED":
                return getString(R.string.alert_car_stopped_shoulder);
            case "HAZARD_ON_SHOULDER_ANIMALS":
                return getString(R.string.alert_animals);
            case "HAZARD_WEATHER_FLOOD":
                return getString(R.string.alert_flood);
            case "HAZARD_WEATHER_HEAVY_SNOW":
                return getString(R.string.alert_snow);
            case "HAZARD_WEATHER":
                return getString(R.string.alert_weather);
            default:
                return key.isEmpty() ? "" : key.charAt(0)
                        + key.substring(1).toLowerCase(Locale.US).replace('_', ' ');
        }
    }

    private static int trafficColor(float delayS) {
        if (delayS < 0) return 0xFFBDBDBD;
        if (delayS < 30) return 0xFF2E7D32;
        if (delayS < 90) return 0xFFEF6C00;
        return 0xFFC62828;
    }

    // ---------- map tools ----------

    private static final int TOOL_IDLE = 0xFF424242;
    private static final int TOOL_ON = 0xFF00695C;
    private static final int TOOL_OFF = 0xFF9E9E9E;

    private void buildMapTools() {
        LinearLayout tools = findViewById(R.id.map_tools);
        tool(tools, R.drawable.ic_refresh, R.string.refresh, v -> {
            v.animate().rotationBy(3600f).setDuration(1200).start();
            refreshNow();
            updateRoutes();
        });
        vehiclesBtn = tool(tools, R.drawable.ic_bus, R.string.setting_show_all, v -> {
            boolean on = !prefs.getBoolean("show_all_vehicles", true);
            prefs.edit().putBoolean("show_all_vehicles", on).apply();
            toolState(vehiclesBtn, R.drawable.ic_bus, on);
            renderVehicles(lastVehicles);
        });
        trafficBtn = tool(tools, R.drawable.ic_traffic, R.string.setting_show_traffic,
                v -> toggleLayer("show_traffic", trafficBtn, R.drawable.ic_traffic));
        alertsBtn = tool(tools, R.drawable.ic_warning, R.string.setting_show_alerts,
                v -> toggleLayer("show_alerts", alertsBtn, R.drawable.ic_warning));
        toolState(vehiclesBtn, R.drawable.ic_bus, prefs.getBoolean("show_all_vehicles", true));
        toolState(trafficBtn, R.drawable.ic_traffic, prefs.getBoolean("show_traffic", false));
        toolState(alertsBtn, R.drawable.ic_warning, prefs.getBoolean("show_alerts", false));
        tool(tools, R.drawable.ic_settings, R.string.settings, v -> showSettings());

        LinearLayout zoom = findViewById(R.id.map_zoom);
        tool(zoom, R.drawable.ic_zoom_in, R.string.zoom_in, v -> map.getController().zoomIn());
        tool(zoom, R.drawable.ic_zoom_out, R.string.zoom_out, v -> map.getController().zoomOut());
        tool(zoom, R.drawable.ic_my_location, R.string.locate, v -> onLocateClicked());
    }

    private ImageView tool(LinearLayout column, int icon, int description, View.OnClickListener click) {
        ImageView button = new ImageView(this);
        int pad = dp(10);
        button.setPadding(pad, pad, pad, pad);
        button.setContentDescription(getString(description));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(10));
        bg.setColor(0xF2FFFFFF);
        bg.setStroke(dp(1), 0x22000000);
        button.setBackground(bg);
        button.setElevation(dp(2));
        button.setOnClickListener(click);
        setToolIcon(button, icon, TOOL_IDLE, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        if (column.getChildCount() > 0) lp.topMargin = dp(6);
        column.addView(button, lp);
        return button;
    }

    private void toolState(ImageView button, int icon, boolean on) {
        setToolIcon(button, icon, on ? TOOL_ON : TOOL_OFF, !on);
    }

    private void setToolIcon(ImageView button, int icon, int color, boolean slashed) {
        Drawable d = getDrawable(icon).mutate();
        d.setTint(color);
        button.setImageDrawable(slashed
                ? new LayerDrawable(new Drawable[]{d, getDrawable(R.drawable.ic_slash)}) : d);
    }

    private void toggleLayer(String key, ImageView button, int icon) {
        boolean on = !prefs.getBoolean(key, false);
        prefs.edit().putBoolean(key, on).apply();
        toolState(button, icon, on);
        if (on) loadOverlays();
        else renderOverlays();
    }

    // ---------- settings ----------

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad, pad, pad);
        box.addView(text(getString(R.string.incidents_shown), 14, 0xFF888888, true));
        int[] labels = {R.string.filter_blocking, R.string.filter_slow, R.string.filter_minor};
        int[] severities = {Alerts.SEV_BLOCKING, Alerts.SEV_SLOW, Alerts.SEV_MINOR};
        for (int i = 0; i < labels.length; i++) {
            final String key = "alerts_sev" + severities[i];
            CheckBox filter = new CheckBox(this);
            filter.setText(labels[i]);
            filter.setChecked(prefs.getBoolean(key, true));
            filter.setOnCheckedChangeListener((b, checked) -> {
                prefs.edit().putBoolean(key, checked).apply();
                renderOverlays();
            });
            box.addView(filter);
        }
        boolean romanian = "ro".equals(prefs.getString("lang", defaultLang()));
        Button lang = new Button(this);
        lang.setText(romanian ? "English" : "Rom\u00e2n\u0103");
        box.addView(lang);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setView(box)
                .setPositiveButton(R.string.close, null)
                .create();
        lang.setOnClickListener(v -> {
            prefs.edit().putString("lang", romanian ? "en" : "ro").apply();
            dialog.dismiss();
            recreate();
        });
        dialog.show();
    }

    // ---------- helpers ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String defaultLang() {
        return "ro".equals(Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage())
                ? "ro" : "en";
    }

    private int colorOf(Route r) {
        if (r != null && r.color != null) {
            try {
                return Color.parseColor(r.color);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Color.GRAY;
    }

    private Bitmap badge(int fill, String text) {
        String key = "n" + fill + "|" + text;
        Bitmap b = iconCache.get(key);
        if (b != null) return b;
        int size = dp(30);
        b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(fill);
        c.drawCircle(size / 2f, size / 2f, size / 2f - dp(1), p);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        c.drawCircle(size / 2f, size / 2f, size / 2f - dp(1), p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(size * (text.length() <= 3 ? 0.40f : 0.30f));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(text, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
        iconCache.put(key, b);
        return b;
    }

    private Bitmap dot(int fill, int ring, int radiusPx) {
        String key = "d" + fill + "|" + ring + "|" + radiusPx;
        Bitmap b = iconCache.get(key);
        if (b != null) return b;
        int size = radiusPx * 2 + 4;
        b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(fill);
        c.drawCircle(size / 2f, size / 2f, radiusPx, p);
        p.setColor(ring);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2, radiusPx / 4));
        c.drawCircle(size / 2f, size / 2f, radiusPx - 1, p);
        iconCache.put(key, b);
        return b;
    }

    private static int rank(int type) {
        return type == 0 ? 0 : type == 11 ? 1 : type == 3 ? 2 : type == 1 ? 3 : 4;
    }

    private static int leadingInt(String s) {
        int n = 0, i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) n = n * 10 + (s.charAt(i++) - '0');
        return n;
    }

    private String kind(Route r) {
        return getString(r.type == 0 ? R.string.type_tram
                : r.type == 11 ? R.string.type_trolley
                : r.type == 3 ? R.string.type_bus : R.string.type_metro);
    }
}
