package showroom.dialogue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DialogueState {
    final List<Task> tasks = new ArrayList<>();
    String userName;
    PendingInteraction pendingInteraction;

    public Task focused() { return tasks.isEmpty() ? null : tasks.get(tasks.size() - 1); }
    public List<Task> tasks() { return Collections.unmodifiableList(tasks); }
    public String userName() { return userName; }
    public PendingInteraction pendingInteraction() { return pendingInteraction; }

    DialogueState copy() {
        DialogueState copy = new DialogueState();
        for (Task task : tasks) copy.tasks.add(task.copy());
        copy.userName = userName;
        copy.pendingInteraction = pendingInteraction;
        return copy;
    }

    Task find(String id) {
        for (Task task : tasks) if (task.id.equals(id)) return task;
        return null;
    }

    Task find(Task.Kind kind) {
        for (Task task : tasks) if (task.kind == kind) return task;
        return null;
    }
}
