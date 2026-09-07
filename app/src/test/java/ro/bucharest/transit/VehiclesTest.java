package ro.bucharest.transit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class VehiclesTest {

    private static final Vehicle A = new Vehicle("A", "882", 0, 44.5, 26.0, 100);
    private static final Vehicle B = new Vehicle("B", "999", 1, 44.5, 26.0, 200);
    private static final Vehicle C = new Vehicle("C", null, -1, 44.5, 26.0, 300);
    private static final Vehicle D = new Vehicle("D", "882", -1, 44.5, 26.0, 0); // no timestamp: kept
    private static final List<Vehicle> ALL = Arrays.asList(A, B, C, D);

    @Test
    public void dropsStaleVehicles() {
        List<Vehicle> fresh = Vehicles.fresh(ALL, 490, 300);
        assertEquals(Arrays.asList(B, C, D), fresh);
    }
}
