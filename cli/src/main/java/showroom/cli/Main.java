package showroom.cli;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class Main {
    public static void main(String[] args) throws IOException {
        FakeFlow flow = new FakeFlow();
        String endpoint = System.getenv("NLU_URL");
        String model = System.getenv("NLU_MODEL");
        Nlu nlu;
        if (endpoint != null && model != null) {
            nlu = new LlmNlu(endpoint, model, System.getenv("NLU_API_KEY"), flow.list());
        } else {
            nlu = text -> { throw new IOException("Set NLU_URL and NLU_MODEL for text input, or use /inject JSON."); };
        }
        PrintWriter console = new PrintWriter(System.out, true);
        new Cli(nlu, flow, console, console)
                .run(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }
}
