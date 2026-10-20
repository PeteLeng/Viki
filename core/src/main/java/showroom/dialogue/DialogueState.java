package showroom.dialogue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DialogueState {
    final List<Task> tasks = new ArrayList<>();

    public Task focused() { return tasks.isEmpty() ? null : tasks.get(tasks.size() - 1); }
    public List<Task> tasks() { return Collections.unmodifiableList(tasks); }

    DialogueState copy() {
        DialogueState copy = new DialogueState();
        for (Task task : tasks) copy.tasks.add(task.copy());
        return copy;
    }

    Task find(String id) {
        for (Task task : tasks) if (task.id.equals(id)) return task;
        return null;
    }
}
