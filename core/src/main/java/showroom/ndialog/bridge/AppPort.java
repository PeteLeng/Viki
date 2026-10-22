package showroom.ndialog.bridge;

import java.util.List;

/** Application boundary; the host stays authoritative about execution. */
public interface AppPort {
    List<Feature> list();

    void start(String flowId, Listener listener);

    void stop();

    interface Listener { void onEvent(Event event); }

    /** Notification shape; "running"/"completed"/"failed" report execution progress. */
    final class Event {
        public final String kind;
        public final String text;

        public Event(String kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }
}
