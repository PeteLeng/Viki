package showroom.cli;

import java.io.IOException;
import java.util.List;
import showroom.dialogue.DialogueAct;

/** Both the temporary LLM and production adapter take text only. */
public interface Nlu {
    List<DialogueAct> interpret(String text) throws IOException;
}
