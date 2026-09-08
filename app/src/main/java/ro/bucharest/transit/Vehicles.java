package ro.bucharest.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Vehicles {
    private Vehicles() {
    }

    static List<Vehicle> fresh(List<Vehicle> all, long now, long maxAgeSec) {
        List<Vehicle> out = new ArrayList<>();
        for (Vehicle v : all) if (v.seenAt == 0 || now - v.seenAt <= maxAgeSec) out.add(v);
        return Collections.unmodifiableList(out);
    }
}
