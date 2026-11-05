package showroom.cli.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import org.junit.Test;
import showroom.ndialog.bridge.Feature;

public class CatalogJsonTest {
    @Test public void catalogBindsTheSchemaAndValidates() throws IOException {
        String json = "[{\"id\":\"dlp_headlights\",\"name\":\"DLP Headlights\","
                + "\"aliases\":[\"DLP lights\",\"DLP大灯\"],\"parts\":[\"outside\"],"
                + "\"themes\":[\"family\"],\"description\":\"Projects patterns.\"}]";
        Feature feature = CatalogJson.load(new StringReader(json)).get(0);
        assertEquals("dlp_headlights", feature.id);
        assertEquals("DLP Headlights", feature.name);
        assertEquals(Arrays.asList("DLP lights", "DLP大灯"), feature.aliases);

        try {
            CatalogJson.load(new StringReader("[{\"id\":\"x\"}]"));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("A catalog entry needs id, name"));
        }
    }
}
