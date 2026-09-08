package ro.bucharest.transit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoutesDbTest {
    private static final String SAMPLE =
            "{\"routes\":["
            + "{\"i\":\"882\",\"s\":\"54\",\"t\":0,\"c\":\"#BE1622\",\"l\":\"Sura Mare - Piata Unirii\","
            + "\"p\":[[[44.5,26.0],[44.51,26.01]],[[44.52,26.02],[44.53,26.03]]],"
            + "\"q\":[[\"s1\",\"s2\"],[\"s3\",\"s2\"]],"
            + "\"h\":[\"Sura Mare\",\"Piata Unirii\"]}],"
            + "\"stops\":{\"s1\":[\"Piata Unirii\",44.4,26.1],\"s2\":[\"Sura Mare\",44.5,26.0]}}";

    @Test
    public void parsesRoutesPolylinesAndStopSequences() {
        Network n = RoutesDb.parse(SAMPLE);
        Route r = n.routes.get(0);
        assertEquals("882", r.id);
        assertEquals("54", r.shortName);
        assertEquals(0, r.type);
        assertEquals("#BE1622", r.color);
        assertEquals(2, r.paths.length);
        assertEquals(26.01, r.paths[0][1][1], 1e-9);
        assertEquals(2, r.stopIds.length);
        assertEquals("s2", r.stopIds[1][1]);
        assertEquals("Sura Mare", r.headsigns[0]);
        assertEquals("Piata Unirii", r.headsigns[1]);
    }

    @Test
    public void parsesStops() {
        Network n = RoutesDb.parse(SAMPLE);
        assertEquals("Piata Unirii", n.stops.get("s1").name);
        assertEquals(26.1, n.stops.get("s1").lon, 1e-9);
        assertEquals(2, n.stops.size());
    }

    @Test
    public void toleratesLegacySchemaWithoutStops() {
        Network n = RoutesDb.parse("{\"routes\":[{\"i\":\"882\",\"s\":\"54\",\"t\":0,\"c\":null,\"l\":\"x\",\"p\":[]}]}");
        assertEquals(0, n.routes.get(0).stopIds.length);
        assertEquals(0, n.routes.get(0).headsigns.length);
        assertNull(n.routes.get(0).color);
        assertTrue(n.stops.isEmpty());
    }

    @Test
    public void toleratesInvalidInput() {
        assertTrue(RoutesDb.parse("garbage").routes.isEmpty());
        assertTrue(RoutesDb.parse(null).routes.isEmpty());
    }
}
