package showroom.cli;

import java.io.IOException;
import java.util.List;
import showroom.dialogue.Act;

/** Both the temporary LLM and production adapter take text only. */
public interface Nlu {
    List<Act> interpret(String text) throws IOException;
}
