package showroom.dialogue;

import java.util.List;

/** Integration boundary; the app remains authoritative about execution. */
public interface Flow {
    List<Feature> list();
    void start(String featureId, Listener listener);
    void stop();

    interface Listener { void onEvent(Event event); }

    /** Sample notification shape, not an internal lifecycle-state machine. */
    final class Event {
        public final String kind;
        public final String text;

        public Event(String kind, String text) { this.kind = kind; this.text = text; }
    }
}
