package showroom.cli;

import java.io.IOException;
import java.util.List;
import showroom.ndialog.nlu.Segment;

/** Text in, segments out; the interpreter takes no dialogue state in this slice. */
public interface Nlu {
    List<Segment> interpret(String text) throws IOException;
}
