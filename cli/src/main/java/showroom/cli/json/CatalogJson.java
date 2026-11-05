package showroom.cli.json;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import showroom.ndialog.bridge.Feature;

/** The catalog dialect: catalog.json → Features, bound by schema and constructor-checked. */
public final class CatalogJson {
    private static final Gson GSON = new Gson();

    private CatalogJson() { }

    public static List<Feature> load(Reader reader) throws IOException {
        try {
            Feature[] features = GSON.fromJson(reader, Feature[].class);
            if (features == null) throw new IOException("The catalog is empty.");
            for (Feature feature : features) {
                if (feature.id == null || feature.name == null || feature.aliases == null
                        || feature.parts == null || feature.themes == null) {
                    throw new IllegalArgumentException("A catalog entry needs id, name, aliases, parts, and themes.");
                }
            }
            return Feature.immutable(Arrays.asList(features));
        } catch (RuntimeException e) {
            throw new IOException("Invalid catalog: " + e.getMessage(), e);
        }
    }
}
