package showroom.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import showroom.ndialog.bridge.Feature;
import showroom.ndialog.nlu.Segment;

/** Stateless opencode-backed interpreter: one "opencode run" subprocess per utterance. */
public final class OpenCodeNlu implements Nlu {
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;
    private static final String DEFAULT_PERMISSION = "{\"external_directory\":\"allow\",\"edit\":\"deny\"}";

    private final String dir;
    private final List<String> fixedArgv;
    private final String prompt;
    private final long timeoutSeconds;
    private List<String> argvPrefix;

    public OpenCodeNlu(String dir, List<Feature> catalog) {
        this(dir, null, catalog, DEFAULT_TIMEOUT_SECONDS);
    }

    OpenCodeNlu(List<String> baseArgv, List<Feature> catalog, long timeoutSeconds) {
        this(null, baseArgv, catalog, timeoutSeconds);
    }

    private OpenCodeNlu(String dir, List<String> fixedArgv, List<Feature> catalog, long timeoutSeconds) {
        this.dir = dir;
        this.fixedArgv = fixedArgv;
        this.prompt = NluPrompt.prompt(catalog);
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override public List<Segment> interpret(String text) throws IOException {
        List<String> argv = command();
        argv.add(prompt + "\n\nText to parse: " + text);
        ProcessBuilder builder = new ProcessBuilder(argv);
        Map<String, String> environment = builder.environment();
        if (!environment.containsKey("OPENCODE_PERMISSION")) {
            environment.put("OPENCODE_PERMISSION", DEFAULT_PERMISSION);
        }
        Process process = builder.start();
        process.getOutputStream().close();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread out = reader(process.getInputStream(), stdout);
        Thread err = reader(process.getErrorStream(), stderr);
        out.start();
        err.start();
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("opencode did not finish within " + timeoutSeconds + " seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for opencode.");
        }
        join(out);
        join(err);
        if (process.exitValue() != 0) {
            throw new IOException("opencode exited with " + process.exitValue() + ": " + excerpt(stderr.toString()));
        }
        String output = stdout.toString();
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start < 0 || end < start) throw new IOException("opencode returned no JSON: " + excerpt(output));
        return JsonCodec.segments(output.substring(start, end + 1));
    }

    private List<String> command() throws IOException {
        if (fixedArgv != null) return new ArrayList<String>(fixedArgv);
        if (argvPrefix == null) argvPrefix = resolvePrefix();
        return new ArrayList<String>(argvPrefix);
    }

    private List<String> resolvePrefix() throws IOException {
        List<String> prefix = new ArrayList<String>();
        String configured = System.getenv("NLU_OPENCODE_CMD");
        if (configured != null && !configured.trim().isEmpty()) {
            for (String part : configured.trim().split("\\s+")) prefix.add(part);
        } else {
            prefix.add(resolveExecutable());
        }
        prefix.add("run");
        prefix.add("--dir");
        prefix.add(dir);
        return prefix;
    }

    /** npm .cmd shims are not directly executable; resolve the wrapped node_modules binary instead. */
    private static String resolveExecutable() throws IOException {
        boolean windows = File.pathSeparatorChar == ';';
        String path = System.getenv("PATH");
        if (path == null) path = "";
        for (String entry : path.split(Pattern.quote(File.pathSeparator))) {
            if (entry.trim().isEmpty()) continue;
            File directory = new File(entry);
            if (windows) {
                File exe = new File(directory, "opencode.exe");
                if (exe.isFile()) return exe.getPath();
                File cmd = new File(directory, "opencode.cmd");
                if (cmd.isFile()) {
                    File npm = new File(directory, "node_modules/opencode-ai/bin/opencode.exe");
                    if (npm.isFile()) return npm.getPath();
                }
            } else {
                File exe = new File(directory, "opencode");
                if (exe.isFile()) return exe.getPath();
            }
        }
        throw new IOException("opencode executable not found on PATH. Set NLU_OPENCODE_CMD to the full command.");
    }

    /** The banner, blank lines, and markdown fences surround the reply; braces bound the JSON payload. */
    private static String excerpt(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 200 ? compact : compact.substring(0, 200) + "...";
    }

    private static Thread reader(final InputStream stream, final StringBuilder sink) {
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                try { sink.append(read(stream)); } catch (IOException ignored) { }
            }
        });
        thread.setDaemon(true);
        return thread;
    }

    private static void join(Thread thread) {
        try { thread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
