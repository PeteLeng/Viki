package showroom.cli;

import java.util.List;
import showroom.ndialog.bridge.Feature;

/** The shared static interpretation prompt; adapters send only this plus the current text. */
final class NluPrompt {
    private NluPrompt() { }

    static String prompt(List<Feature> catalog) {
        return "Parse only the supplied ASR text. Return JSON, with no markdown: "
                + "{\"segments\":[{\"key\":\"flow\",\"slots\":{\"id\":\"headlights\"}}]}. "
                + "Segment keys: wakeup, flow, value, choice, yes, no, stop, resume, help, list. Slots are string values. "
                + "wakeup: a greeting or session start; optionally include name, e.g. wakeup with slots {\"name\":\"Sam\"} for 'hi, I'm Sam'. "
                + "flow: a request to play a flow; slots id, part, theme. Use id only for a known catalog ID below; never invent ids. "
                + "value: one named value answering or correcting a parameter; slots name, id, part, or theme, one value per segment. "
                + "choice: an ordinal pick like 'the second one'; slots {\"n\":\"2\"}. "
                + "yes/no: bare agreement or refusal with empty slots. "
                + "Split a compound utterance into several segments in order. "
                + "Preserve unknown intents as their own segment rather than relabeling them; unknown flow names must not become ids. "
                + "Never invent state, choices, or confidence. Static catalog vocabulary: " + JsonCodec.GSON.toJson(catalog);
    }
}
