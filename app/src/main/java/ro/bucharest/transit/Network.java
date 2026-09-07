package ro.bucharest.transit;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The static network model: routes with geometry and the stop catalog. */
final class Network {
    final List<Route> routes;
    final Map<String, Stop> stops;

    Network(List<Route> routes, Map<String, Stop> stops) {
        this.routes = Collections.unmodifiableList(routes);
        this.stops = Collections.unmodifiableMap(stops);
    }
}
