package showroom.cli;

import java.io.IOException;
import java.util.List;
import showroom.ndialog.dialogue.Act;

/** Text in, acts out; the interpreter takes no dialogue state in this slice. */
public interface Nlu {
    List<Act> interpret(String text) throws IOException;
}
