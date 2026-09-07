package ro.bucharest.transit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class CsvTest {

    @Test
    public void parsesPlainRows() {
        List<List<String>> rows = Csv.parse("a,b\nc,d\n");
        assertEquals(2, rows.size());
        assertEquals("a", rows.get(0).get(0));
        assertEquals("d", rows.get(1).get(1));
    }

    @Test
    public void parsesQuotedFieldsWithCommasAndEscapes() {
        List<List<String>> rows = Csv.parse("x,\"1,2\",\"e\"\"s\"\"\",y");
        assertEquals(4, rows.get(0).size());
        assertEquals("1,2", rows.get(0).get(1));
        assertEquals("e\"s\"", rows.get(0).get(2));
    }

    @Test
    public void handlesCrlfAndSkipsBlankLines() {
        List<List<String>> rows = Csv.parse("h1,h2\r\n\r\na,b\n");
        assertEquals(2, rows.size());
    }

    @Test
    public void keepsLastRowWithoutNewline() {
        List<List<String>> rows = Csv.parse("a,b\nc,d");
        assertEquals(2, rows.size());
        assertEquals("d", rows.get(1).get(1));
    }
}
