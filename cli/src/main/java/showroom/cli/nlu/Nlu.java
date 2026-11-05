package showroom.cli.nlu;

import java.io.IOException;
import java.util.List;
import showroom.ndialog.dialogue.UserAct;

/** Text in, user acts out; the interpreter takes no dialogue state in this slice. */
public interface Nlu {
    List<UserAct> interpret(String text) throws IOException;

    /** Notifies adapters when the CLI's /debug toggle flips so they can print raw output. */
    default void setDebug(boolean on) { }
}
