package showroom.ndialog.dialogue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class UserActTest {
    @Test public void constructionProducesTheDocumentedShapes() {
        UserAct greet = new UserAct(UserAct.Type.GREET, Collections.emptyMap(), null);
        assertEquals(UserAct.Type.GREET, greet.type());
        assertTrue(greet.slots().isEmpty());
        assertEquals(UserAct.Type.AFFIRM, new UserAct(UserAct.Type.AFFIRM, Collections.emptyMap(), null).type());
        assertEquals(UserAct.Type.DENY, new UserAct(UserAct.Type.DENY, Collections.emptyMap(), null).type());

        UserAct inform = UserAct.inform("name", " Sam ");
        assertEquals(UserAct.Type.INFORM, inform.type());
        assertEquals("Sam", inform.slot("name"));

        UserAct request = UserAct.request("flow", Collections.singletonMap("flow_id", "headlights"));
        assertEquals(UserAct.Type.REQUEST, request.type());
        assertEquals("flow", request.slot("task"));
        assertEquals("headlights", request.slot("flow_id"));
    }

    @Test public void clearedDistinguishesAbsentFromUnset() {
        UserAct inform = UserAct.inform("name", "Sam");
        assertFalse(inform.cleared("name"));
        assertFalse(inform.cleared("task"));
        UserAct clear = UserAct.clear("task");
        assertTrue(clear.cleared("task"));
        assertNull(clear.slot("task"));
    }

    @Test public void requestRejectsATaskInsideArguments() {
        try {
            UserAct.request("flow", Collections.singletonMap("task", "other"));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void theConstructorCarriesConfidence() {
        UserAct stamped = new UserAct(UserAct.Type.INFORM,
                Collections.singletonMap("name", "Sam"), 0.9);
        assertEquals(Double.valueOf(0.9), stamped.confidence());
        assertNull(UserAct.inform("name", "Sam").confidence());
    }

    @Test public void constructorRejectsBrokenInput() {
        try { new UserAct(null, Collections.emptyMap(), null); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
        try { new UserAct(UserAct.Type.INFORM, Collections.singletonMap(" ", "x"), null); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
        try { new UserAct(UserAct.Type.INFORM, Collections.singletonMap("name", "  "), null); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
