package showroom.cli;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import showroom.cli.nlu.Nlu;
import showroom.ndialog.dialogue.UserAct;

public class CliTest {
    @Test public void injectScriptRunsTheGreetingHandoff() throws IOException {
        FakeFlow app = new FakeFlow();
        StringWriter sink = new StringWriter();
        PrintWriter out = new PrintWriter(sink, true);
        Nlu nlu = text -> { throw new IOException("no nlu in this test"); };
        new Cli(nlu, app, out).run(new StringReader(
                "/inject {\"acts\":[{\"type\":\"greet\"}]}\n"
                        + "/inject {\"acts\":[{\"type\":\"inform\",\"slots\":{\"name\":\"Sam\"}}]}\n"
                        + "/inject {\"acts\":[{\"type\":\"affirm\"}]}\n"
                        + "/inject {\"acts\":[{\"type\":\"inform\",\"slots\":{\"theme\":\"family\"}}]}\n"
                        + "/inject {\"acts\":[{\"type\":\"inform\",\"slots\":{\"choice\":\"2\"}}]}\n"
                        + "/state\n"
                        + "/quit\n"));
        String output = sink.toString();
        assertTrue(output.contains("Hello!"));
        assertTrue(output.contains("What is your name?"));
        assertTrue(output.contains("Would you like to see a demo?"));
        assertTrue(output.contains("Which flow would you like?"));
        assertTrue(output.contains("Requesting flow: cabin_cinema"));
        assertTrue(output.contains("flow: completed cabin_cinema"));
        assertTrue(output.contains("[fake flow] Completed Cabin Cinema"));
        assertTrue(output.contains("\"Sam\""));
    }

    @Test public void debugTogglePrintsActsAndReachesTheNlu() throws IOException {
        FakeFlow app = new FakeFlow();
        StringWriter sink = new StringWriter();
        PrintWriter out = new PrintWriter(sink, true);
        final boolean[] debugSeen = {false};
        Nlu nlu = new Nlu() {
            @Override public List<UserAct> interpret(String text) {
                return Collections.singletonList(
                        new UserAct(UserAct.Type.GREET, Collections.emptyMap(), null));
            }
            @Override public void setDebug(boolean on) { debugSeen[0] = on; }
        };
        new Cli(nlu, app, out).run(new StringReader(
                "/debug\n" + "hi there\n" + "/debug\n" + "/quit\n"));
        String output = sink.toString();
        assertTrue(output.contains("debug: on"));
        assertTrue(output.contains("[debug] acts: "));
        assertTrue(output.contains("\"type\":\"GREET\""));
        assertTrue(output.contains("debug: off"));
        assertTrue(!debugSeen[0]);
    }
}
