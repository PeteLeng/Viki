package showroom.cli;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import showroom.ndialog.bridge.AppPort;

public final class Main {
    public static void main(String[] args) throws IOException {
        AppPort app = new FakeFlow();
        Nlu nlu;
        if ("opencode".equals(System.getenv("NLU_BACKEND"))) {
            String dir = System.getenv("NLU_OPENCODE_DIR");
            nlu = new OpenCodeNlu(dir == null ? "D:/yleng/scratch/dm-master" : dir, app.list());
        } else {
            nlu = text -> { throw new IOException("Set NLU_BACKEND=opencode for text input, or use /inject JSON."); };
        }
        PrintWriter console = new PrintWriter(System.out, true);
        new Cli(nlu, app, console).run(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }
}
