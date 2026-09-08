package ro.bucharest.transit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GtfsTest {
    private static final String ROUTES = "route_id,agency_id,route_short_name,route_type,route_color,route_text_color,route_long_name\n"
            + "r1,1,32,3,00AAFF,FFFFFF,\"Coast - Center, Main\"\n";
    private static final String TRIPS = "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id\n"
            + "r1,D,t1,Unirii,,0,b,sh1\n"
            + "r1,D,t2,Victoriei,,1,b,sh2\n"
            + "r1,D,t3,Unirii,,0,b,sh1\n";
    private static final String SHAPES = "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\n"
            + "sh1,44.00000,26.00000,0\n"
            + "sh1,44.00100,26.00000,1\n"
            + "sh1,44.00100,26.00000,2\n"
            + "sh2,44.50000,26.50000,0\n"
            + "sh2,44.50000,26.51000,1\n";
    private static final String STOPS = "stop_id,stop_name,stop_desc,stop_lat,stop_lon,location_type,platform_code,parent_station,level_id\n"
            + "s1,Station A,,44.00000,26.00000,,,,\n"
            + "s2,Station B,,44.00100,26.00000,,,,\n"
            + "s3,Station C,,44.50000,26.50000,,,,\n";
    private static final String STOP_TIMES = "trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint\n"
            + "t1,05:00:00,05:00:00,s1,0,,,,,\n"
            + "t1,05:01:00,05:01:00,s2,1,,,,,\n"
            + "t3,05:00:00,05:00:00,s2,0,,,,,\n"
            + "t2,06:00:00,06:00:00,s3,0,,,,,\n"
            + "t2,06:01:00,06:01:00,s2,1,,,,,\n";

    @Test
    public void buildsRoutesStopsAndStopSequences() throws IOException {
        Network n = RoutesDb.parse(Gtfs.build(zip()));
        assertEquals(1, n.routes.size());
        Route r = n.routes.get(0);
        assertEquals("r1", r.id);
        assertEquals("#00AAFF", r.color);
        assertEquals("Coast - Center, Main", r.name);
        assertEquals(2, r.paths.length);
        assertEquals(2, r.paths[0].length);
        assertEquals(2, r.stopIds.length);
        assertEquals("s1", r.stopIds[0][0]);
        assertEquals("s2", r.stopIds[0][1]);
        assertEquals("s3", r.stopIds[1][0]);
        assertEquals("Unirii", r.headsigns[0]);
        assertEquals("Victoriei", r.headsigns[1]);
        assertArrayEquals(new int[]{0, 1}, r.dirIds);
        assertEquals("Station A", n.stops.get("s1").name);
        assertEquals(44.0, n.stops.get("s1").lat, 1e-9);
        assertEquals(3, n.stops.size());
    }

    @Test
    public void branchesOfOneDirectionBecomeSeparateVariants() throws IOException {
        String trips = "route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id\n"
                + "r1,D,t1,Raul Doamnei,,0,b,sh1\n"
                + "r1,D,t2,Valea Ialomitei,,0,b,sh2\n"
                + "r1,D,t3,Eroilor,,1,b,sh1\n";
        String stopTimes = "trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint\n"
                + "t1,05:00:00,05:00:00,s1,0,,,,,\n"
                + "t2,05:00:00,05:00:00,s2,0,,,,,\n"
                + "t3,06:00:00,06:00:00,s3,0,,,,,\n";
        File zip = File.createTempFile("gtfs", ".zip");
        zip.deleteOnExit();
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            put(z, "routes.txt", ROUTES);
            put(z, "trips.txt", trips);
            put(z, "shapes.txt", SHAPES);
            put(z, "stops.txt", STOPS);
            put(z, "stop_times.txt", stopTimes);
        }
        Route r = RoutesDb.parse(Gtfs.build(zip)).routes.get(0);
        assertEquals(3, r.variantCount());
        assertArrayEquals(new String[]{"Raul Doamnei", "Valea Ialomitei", "Eroilor"}, r.headsigns);
        assertArrayEquals(new int[]{0, 0, 1}, r.dirIds);
        assertEquals("s1", r.stopIds[0][0]);
        assertEquals("s2", r.stopIds[1][0]);
        assertEquals("s3", r.stopIds[2][0]);
    }

    @Test
    public void rejectsIncompleteArchive() throws IOException {
        File zip = File.createTempFile("gtfs", ".zip");
        zip.deleteOnExit();
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            put(z, "routes.txt", ROUTES);
        }
        try {
            Gtfs.build(zip);
            assertTrue("expected IOException", false);
        } catch (IOException expected) {
        }
    }

    private static File zip() throws IOException {
        File zip = File.createTempFile("gtfs", ".zip");
        zip.deleteOnExit();
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            put(z, "routes.txt", ROUTES);
            put(z, "trips.txt", TRIPS);
            put(z, "shapes.txt", SHAPES);
            put(z, "stops.txt", STOPS);
            put(z, "stop_times.txt", STOP_TIMES);
        }
        return zip;
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
