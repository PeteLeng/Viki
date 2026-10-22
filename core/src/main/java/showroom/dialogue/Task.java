package showroom.dialogue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Task {
    public enum Kind { WELCOME, PLAY }
    public enum Progress { PENDING, START_REQUESTED, RUNNING, COMPLETED, FAILED }

    final String id;
    final Kind kind;
    final Map<String, String> parameters = new LinkedHashMap<>();
    Progress progress = Progress.PENDING;
    FlowCommand startCommand;

    Task(Kind kind) { this(UUID.randomUUID().toString(), kind); }

    private Task(String id, Kind kind) { this.id = id; this.kind = kind; }

    Task copy() {
        Task copy = new Task(id, kind);
        copy.parameters.putAll(parameters);
        copy.progress = progress;
        copy.startCommand = startCommand;
        return copy;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public String parameter(String name) { return parameters.get(name); }
    public Map<String, String> parameters() { return Collections.unmodifiableMap(parameters); }
    public Progress progress() { return progress; }

    boolean executionOutstanding() {
        return progress == Progress.START_REQUESTED || progress == Progress.RUNNING;
    }
}
