package showroom.dialogue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Task {
    public enum Kind { BROWSE, EXPLAIN, DEMONSTRATE }

    final String id;
    final Kind kind;
    String featureId;
    String part;
    String theme;
    Expectation expectation;
    List<Feature> choices = Collections.emptyList();
    SystemAction demoRequest;
    boolean demoOutstanding;

    public Task(Kind kind) { this(UUID.randomUUID().toString(), kind); }

    private Task(String id, Kind kind) { this.id = id; this.kind = kind; }

    Task copy() {
        Task copy = new Task(id, kind);
        copy.featureId = featureId;
        copy.part = part;
        copy.theme = theme;
        copy.expectation = expectation;
        copy.choices = choices;
        copy.demoRequest = demoRequest;
        copy.demoOutstanding = demoOutstanding;
        return copy;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public String featureId() { return featureId; }
    public String part() { return part; }
    public String theme() { return theme; }
    public Expectation expectation() { return expectation; }
    public List<Feature> choices() { return choices; }
    public boolean demoOutstanding() { return demoOutstanding; }
}
