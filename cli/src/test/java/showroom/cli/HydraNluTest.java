package showroom.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import showroom.ndialog.bridge.Feature;
import showroom.ndialog.dialogue.Act;

public class HydraNluTest {
    private static final List<Feature> CATALOG = catalog();
    private static HttpServer server;

    private static List<Feature> catalog() {
        List<Feature> catalog = new ArrayList<Feature>();
        catalog.add(new Feature("cabin_cinema", "Cabin Cinema",
                Arrays.asList("mobile theater"), Arrays.asList("inside"),
                Arrays.asList("family", "entertainment"), ""));
        return catalog;
    }

    @BeforeClass public static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"ok\": true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.createContext("/parse", exchange -> {
            byte[] body = ("{\"intent\":\"request_flow\",\"confidence\":0.99,\"slots\":"
                    + "[{\"type\":\"flow_ref\",\"text\":\"mobile theater\",\"start\":12,\"end\":26}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
    }

    @AfterClass public static void stopStub() { server.stop(0); }

    private static String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    @Test public void interpretPostsAndTranslates() throws IOException {
        HydraNlu nlu = new HydraNlu(url(), CATALOG);
        List<Act> acts = nlu.interpret("show me the mobile theater");
        assertEquals(1, acts.size());
        assertEquals(Act.Type.REQUEST, acts.get(0).type);
        assertEquals("flow", acts.get(0).slot("task"));
        assertEquals("cabin_cinema", acts.get(0).slot("flow_id"));
        assertEquals(Double.valueOf(0.99), acts.get(0).confidence);
    }

    @Test public void unreachableServerReportsTheStartCommand() {
        try {
            new HydraNlu("http://127.0.0.1:1", CATALOG);
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Start it first"));
            assertTrue(e.getMessage().contains("serve.py"));
        }
    }

    @Test public void greetWithANameSplitsIntoTwoActs() throws IOException {
        List<Act> acts = HydraNlu.toActs(
                "{\"intent\":\"greet\",\"confidence\":0.96,\"slots\":"
                        + "[{\"type\":\"name\",\"text\":\"Sam\",\"start\":7,\"end\":10}]}",
                "hi I'm Sam", CATALOG);
        assertEquals(2, acts.size());
        assertEquals(Act.Type.GREET, acts.get(0).type);
        assertEquals(Act.Type.INFORM, acts.get(1).type);
        assertEquals("Sam", acts.get(1).slot("name"));
        assertEquals(Double.valueOf(0.96), acts.get(0).confidence);
        assertEquals(Double.valueOf(0.96), acts.get(1).confidence);
    }

    @Test public void bareConfirmationsMapToTheirActs() throws IOException {
        assertEquals(Act.Type.AFFIRM, HydraNlu.toActs(
                "{\"intent\":\"affirm\",\"confidence\":0.9,\"slots\":[]}", "yes", CATALOG).get(0).type);
        assertEquals(Act.Type.DENY, HydraNlu.toActs(
                "{\"intent\":\"deny\",\"confidence\":0.9,\"slots\":[]}", "no", CATALOG).get(0).type);
    }

    @Test public void informNormalizesEachSlotInOrder() throws IOException {
        List<Act> acts = HydraNlu.toActs(
                "{\"intent\":\"inform\",\"confidence\":0.9,\"slots\":["
                        + "{\"type\":\"part\",\"text\":\"interior\",\"start\":0,\"end\":8}]}",
                "interior", CATALOG);
        assertEquals(1, acts.size());
        assertEquals("inside", acts.get(0).slot("part"));
    }

    @Test public void informPassesUnknownFlowTextThrough() throws IOException {
        List<Act> acts = HydraNlu.toActs(
                "{\"intent\":\"inform\",\"confidence\":0.9,\"slots\":["
                        + "{\"type\":\"flow_ref\",\"text\":\"massage chairs\",\"start\":0,\"end\":14}]}",
                "massage chairs", CATALOG);
        assertEquals(1, acts.size());
        assertEquals("massage chairs", acts.get(0).slot("flow_id"));
    }

    @Test public void requestFlowCarriesTheArguments() throws IOException {
        List<Act> acts = HydraNlu.toActs(
                "{\"intent\":\"request_flow\",\"confidence\":0.98,\"slots\":["
                        + "{\"type\":\"flow_ref\",\"text\":\"mobile theater\",\"start\":12,\"end\":26},"
                        + "{\"type\":\"part\",\"text\":\"inside\",\"start\":0,\"end\":6}]}",
                "show me the mobile theater inside", CATALOG);
        assertEquals(1, acts.size());
        assertEquals("flow", acts.get(0).slot("task"));
        assertEquals("cabin_cinema", acts.get(0).slot("flow_id"));
        assertEquals("inside", acts.get(0).slot("part"));
    }

    @Test public void requestFlowWithoutSlotsAsksForAGoal() throws IOException {
        List<Act> acts = HydraNlu.toActs(
                "{\"intent\":\"request_flow\",\"confidence\":0.9,\"slots\":[]}", "show me a demo", CATALOG);
        assertEquals(1, acts.size());
        assertEquals("flow", acts.get(0).slot("task"));
        assertNull(acts.get(0).slot("flow_id"));
    }

    @Test public void controlsBecomeTaskRequests() throws IOException {
        assertEquals("stop", HydraNlu.toActs(
                "{\"intent\":\"request_stop\",\"confidence\":0.9,\"slots\":[]}", "stop", CATALOG)
                .get(0).slot("task"));
        assertEquals("resume", HydraNlu.toActs(
                "{\"intent\":\"request_resume\",\"confidence\":0.9,\"slots\":[]}", "resume", CATALOG)
                .get(0).slot("task"));
        assertEquals("quit", HydraNlu.toActs(
                "{\"intent\":\"request_quit\",\"confidence\":0.9,\"slots\":[]}", "quit", CATALOG)
                .get(0).slot("task"));
    }

    @Test public void unknownUtteranceBindsNowhere() throws IOException {
        List<Act> acts = HydraNlu.toActs(
                "{\"intent\":\"UNK\",\"confidence\":0.71,\"slots\":[]}", "what is the weather", CATALOG);
        assertEquals(1, acts.size());
        assertEquals(Act.Type.INFORM, acts.get(0).type);
        assertEquals("what is the weather", acts.get(0).slot("text"));
    }

    @Test public void unknownIntentStringFallsBackLikeUnk() throws IOException {
        List<Act> acts = HydraNlu.toActs(
                "{\"intent\":\"gibberish\",\"confidence\":0.5,\"slots\":[]}", "blah", CATALOG);
        assertEquals("blah", acts.get(0).slot("text"));
    }

    @Test public void informWithoutUsableSlotsFallsBack() throws IOException {
        List<Act> acts = HydraNlu.toActs(
                "{\"intent\":\"inform\",\"confidence\":0.5,\"slots\":[]}", "hmm", CATALOG);
        assertEquals(1, acts.size());
        assertEquals("hmm", acts.get(0).slot("text"));
    }

    @Test public void confidenceIsOptional() throws IOException {
        List<Act> acts = HydraNlu.toActs(
                "{\"intent\":\"affirm\",\"slots\":[]}", "yes", CATALOG);
        assertNull(acts.get(0).confidence);
    }

    @Test public void malformedPredictionIsRejected() {
        try {
            HydraNlu.toActs("{\"intent\": 42}", "x", CATALOG);
            fail("expected IOException");
        } catch (IOException e) {
            assertNotNull(e.getMessage());
        }
    }
}
