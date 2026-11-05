package showroom.cli.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import showroom.ndialog.dialogue.UserAct;

public class UserActJsonTest {
    @Test public void parseBuildsEachActType() throws IOException {
        List<UserAct> acts = UserActJson.parse("{\"acts\":["
                + "{\"type\":\"greet\"},"
                + "{\"type\":\"inform\",\"slots\":{\"name\":\"Sam\"}},"
                + "{\"type\":\"request\",\"slots\":{\"task\":\"flow\",\"flow_id\":\"headlights\"}}]}");
        assertEquals(3, acts.size());
        assertEquals(UserAct.Type.GREET, acts.get(0).type());
        assertEquals("Sam", acts.get(1).slot("name"));
        assertEquals("flow", acts.get(2).slot("task"));
        assertEquals("headlights", acts.get(2).slot("flow_id"));
    }

    @Test public void unknownFieldsAreIgnoredAndNullSlotsUnset() throws IOException {
        List<UserAct> acts = UserActJson.parse("{\"extra\":1,\"acts\":["
                + "{\"type\":\"greet\",\"unknown\":true},"
                + "{\"type\":\"inform\",\"slots\":{\"name\":null}}]}");
        assertEquals(UserAct.Type.GREET, acts.get(0).type());
        assertTrue(acts.get(1).cleared("name"));
        assertNull(acts.get(1).slot("name"));
    }

    @Test public void arraySlotValuesAndUnknownTypesAreRejected() {
        try {
            UserActJson.parse("{\"acts\":[{\"type\":\"inform\",\"slots\":{\"name\":[\"Sam\"]}}]}");
            fail("expected IOException");
        } catch (IOException expected) { }
        try {
            UserActJson.parse("{\"acts\":[{\"type\":\"select\",\"slots\":{\"flow_id\":\"x\"}}]}");
            fail("expected IOException");
        } catch (IOException expected) { }
    }

    @Test public void emptyInputIsRejected() {
        try {
            UserActJson.parse("{\"acts\":[]}");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("At least one act"));
        }
    }
}
