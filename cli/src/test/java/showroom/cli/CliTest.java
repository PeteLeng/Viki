package showroom.cli;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.Test;

public class CliTest {
    @Test public void injectScriptRunsTheGreetingHandoff() throws IOException {
        FakeFlow app = new FakeFlow();
        StringWriter sink = new StringWriter();
        PrintWriter out = new PrintWriter(sink, true);
        Nlu nlu = text -> { throw new IOException("no nlu in this test"); };
        new Cli(nlu, app, out).run(new StringReader(
                "/inject {\"segments\":[{\"key\":\"wakeup\"}]}\n"
                        + "/inject {\"segments\":[{\"key\":\"value\",\"slots\":{\"name\":\"Sam\"}}]}\n"
                        + "/inject {\"segments\":[{\"key\":\"yes\"}]}\n"
                        + "/inject {\"segments\":[{\"key\":\"value\",\"slots\":{\"theme\":\"family\"}}]}\n"
                        + "/inject {\"segments\":[{\"key\":\"value\",\"slots\":{\"part\":\"back-seat\"}}]}\n"
                        + "/inject {\"segments\":[{\"key\":\"choice\",\"slots\":{\"n\":\"2\"}}]}\n"
                        + "/state\n"
                        + "/quit\n"));
        String output = sink.toString();
        assertTrue(output.contains("Hello!"));
        assertTrue(output.contains("What is your name?"));
        assertTrue(output.contains("Would you like to see a demo?"));
        assertTrue(output.contains("Which flow would you like?"));
        assertTrue(output.contains("Requesting flow: rear_audio"));
        assertTrue(output.contains("flow: completed rear_audio"));
        assertTrue(output.contains("[fake flow] Completed Rear audio"));
        assertTrue(output.contains("\"Sam\""));
    }
}
