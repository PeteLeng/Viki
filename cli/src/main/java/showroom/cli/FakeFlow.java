package showroom.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import showroom.dialogue.Feature;
import showroom.dialogue.Flow;

/** Small synchronous stand-in, not a simulation of the Android flow engine. */
public final class FakeFlow implements Flow {
    private final List<Feature> features;

    public FakeFlow() throws IOException {
        try (InputStream input = FakeFlow.class.getResourceAsStream("/catalog.json")) {
            if (input == null) throw new IOException("Missing sample catalog.");
            features = JsonCodec.catalog(LlmNlu.read(input));
        }
    }

    @Override public List<Feature> list() { return java.util.Collections.unmodifiableList(features); }

    @Override public void start(String flowId, Listener listener) {
        for (Feature feature : features) {
            if (feature.id.equals(flowId)) {
                listener.onEvent(new Event("narration", "[fake flow] " + feature.description));
                listener.onEvent(new Event("progress", "[fake flow] 100%"));
                listener.onEvent(new Event("completed", "[fake flow] Completed " + feature.name));
                return;
            }
        }
        listener.onEvent(new Event("failed", "[fake flow] Unknown flow: " + flowId));
    }

    @Override public void stop() {
        // This fake completes synchronously; there is no active run to stop.
    }
}
