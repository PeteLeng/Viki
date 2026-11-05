package showroom.ndialog.dialogue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import showroom.ndialog.agent.Agent;

public class SysActTest {
    private static final class Owner extends Agent {
        Owner() { super("owner-1"); }
        @Override public Bind bind(UserAct act, DialogState state) { return Bind.NOT_MINE; }
        @Override public void execute(DialogState state) { }
        @Override public void onFocus(DialogState state) { }
        @Override public Agent copy() { return this; }
    }

    private final Agent owner = new Owner();

    private static UserAct affirm() { return new UserAct(UserAct.Type.AFFIRM, Collections.emptyMap(), null); }
    private static UserAct deny() { return new UserAct(UserAct.Type.DENY, Collections.emptyMap(), null); }

    @Test public void questionsAreBornOwnedAndCarryCardinality() {
        SysAct ask = SysAct.request(owner, "name");
        assertEquals(SysAct.Type.REQUEST, ask.type());
        assertTrue(ask.ownedBy(owner));
        assertTrue(ask.slots().size() == 1 && ask.has("name"));
        assertNull(ask.slot("name"));

        SysAct offer = SysAct.select(owner, "flow_id", Arrays.asList("a", "b"));
        assertEquals(SysAct.Type.SELECT, offer.type());
        assertTrue(offer.ownedBy(owner));
        assertEquals(Arrays.asList("a", "b"), offer.values("flow_id"));

        SysAct propose = SysAct.confirm(owner, "task", "flow");
        assertEquals(SysAct.Type.CONFIRM, propose.type());
        assertTrue(propose.ownedBy(owner));
        assertEquals("flow", propose.slot("task"));
    }

    @Test public void recordsHaveNoOwner() {
        SysAct description = SysAct.inform("description", "Okay.");
        assertFalse(description.ownedBy(owner));
        SysAct event = SysAct.inform(Collections.singletonMap("flow_id", "rear_audio"));
        assertEquals("rear_audio", event.slot("flow_id"));
    }

    @Test public void selectNeedsChoices() {
        try { SysAct.select(owner, "flow_id", Collections.emptyList()); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void translateRequestAcceptsTheAskedSlot() {
        SysAct ask = SysAct.request(owner, "name");
        UserAct answered = ask.translate(UserAct.inform("name", "Sam"));
        assertEquals(UserAct.Type.INFORM, answered.type());
        assertEquals("Sam", answered.slot("name"));
        assertNull(ask.translate(UserAct.inform("theme", "family")));
        assertNull(ask.translate(affirm()));
    }

    @Test public void translateSelectResolvesOrdinalsAndNames() {
        SysAct offer = SysAct.select(owner, "flow_id", Arrays.asList("a", "b"));
        UserAct byOrdinal = offer.translate(UserAct.inform("choice", "2"));
        assertEquals(UserAct.Type.INFORM, byOrdinal.type());
        assertEquals("b", byOrdinal.slot("flow_id"));
        UserAct byName = offer.translate(UserAct.inform("flow_id", "a"));
        assertEquals("a", byName.slot("flow_id"));
        assertNull(offer.translate(UserAct.inform("choice", "3")));
        assertNull(offer.translate(UserAct.inform("choice", "second")));
        assertNull(offer.translate(affirm()));
    }

    @Test public void translateConfirmAffirmProposesAndDenyClears() {
        SysAct propose = SysAct.confirm(owner, "flow_id", "headlights");
        UserAct accepted = propose.translate(affirm());
        assertEquals(UserAct.Type.INFORM, accepted.type());
        assertEquals("headlights", accepted.slot("flow_id"));
        UserAct rejected = propose.translate(deny());
        assertTrue(rejected.cleared("flow_id"));

        SysAct goal = SysAct.confirm(owner, "task", "flow");
        UserAct started = goal.translate(affirm());
        assertEquals(UserAct.Type.REQUEST, started.type());
        assertEquals("flow", started.slot("task"));
        assertNull(started.slot("flow_id"));
        UserAct declined = goal.translate(deny());
        assertTrue(declined.cleared("task"));
    }

    @Test public void recordsDoNotTranslate() {
        SysAct description = SysAct.inform("description", "Okay.");
        assertNull(description.translate(affirm()));
    }
}
