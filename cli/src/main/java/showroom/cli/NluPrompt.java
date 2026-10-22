package showroom.cli;

import java.util.List;
import showroom.ndialog.bridge.Feature;

/** The shared static interpretation prompt; adapters send only this plus the current text. */
final class NluPrompt {
    private NluPrompt() { }

    static String prompt(List<Feature> catalog) {
        return "Parse only the supplied ASR text. Return JSON, with no markdown: "
                + "{\"acts\":[{\"type\":\"inform\",\"slots\":{\"name\":\"Sam\"}}]}. "
                + "User acts: greet, request, inform, affirm, deny. Slots hold one string value each. "
                + "greet, affirm, and deny take no slots; use them for a greeting or a bare yes/no. "
                + "request needs task: task=flow requests a flow demo with optional flow_id, part, theme slots. "
                + "Use flow_id only for a known catalog ID below; never invent ids. "
                + "Other task names (stop, resume, quit, help, list) are preserved as-is. "
                + "inform carries one named value: name, flow_id, part, theme, or choice "
                + "(choice=\"2\" for 'the second one', a one-based ordinal into the offered list). "
                + "Split a compound utterance into several acts in order. "
                + "Preserve unknown intents rather than relabeling them; never invent state or confidence. "
                + "Static catalog vocabulary: " + JsonCodec.GSON.toJson(catalog);
    }
}
