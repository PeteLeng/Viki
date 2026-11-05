package showroom.cli.nlu;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import showroom.ndialog.bridge.Feature;

public class HydraValuesTest {
    private static final List<Feature> CATALOG = catalog();

    private static List<Feature> catalog() {
        List<Feature> catalog = new ArrayList<Feature>();
        catalog.add(new Feature("dlp_headlights", "DLP Headlights",
                Arrays.asList("DLP lights", "headlights", "DLP大灯", "DLP车灯", "大灯"),
                Arrays.asList("outside"), Arrays.asList("family", "entertainment"), ""));
        catalog.add(new Feature("cabin_cinema", "Cabin Cinema",
                Arrays.asList("mobile theater", "theater", "in-car cinema", "座舱影院", "车载影院", "移动影院"),
                Arrays.asList("inside"), Arrays.asList("family", "entertainment"), ""));
        catalog.add(new Feature("massage_seats", "Massaging Seats",
                Arrays.asList("massage seats", "seat massage", "按摩座椅", "座椅按摩"),
                Arrays.asList("inside"), Arrays.asList("entertainment"), ""));
        catalog.add(new Feature("privacy_window", "Privacy Window",
                Arrays.asList("privacy glass", "privacy windows", "隐私车窗", "隐私玻璃"),
                Arrays.asList("outside"), Arrays.asList("privacy"), ""));
        return catalog;
    }

    @Test public void flowResolvesIdsNamesAndAliasesCaseInsensitively() {
        assertEquals("cabin_cinema", HydraValues.flow("cabin cinema", CATALOG));
        assertEquals("cabin_cinema", HydraValues.flow("Cabin Cinema", CATALOG));
        assertEquals("cabin_cinema", HydraValues.flow("mobile theater", CATALOG));
        assertEquals("cabin_cinema", HydraValues.flow("座舱影院", CATALOG));
        assertEquals("dlp_headlights", HydraValues.flow("DLP lights", CATALOG));
        assertEquals("privacy_window", HydraValues.flow("privacy glass", CATALOG));
        assertEquals("massage_seats", HydraValues.flow("seat massage", CATALOG));
    }

    @Test public void flowPassesUnknownTextThroughVerbatim() {
        assertEquals("massage chairs", HydraValues.flow("massage chairs", CATALOG));
        assertEquals("the movie thing", HydraValues.flow("the movie thing", CATALOG));
    }

    @Test public void partCanonicalizesKnownAliases() {
        assertEquals("inside", HydraValues.part("interior"));
        assertEquals("inside", HydraValues.part("cabin"));
        assertEquals("inside", HydraValues.part("车内"));
        assertEquals("outside", HydraValues.part("exterior"));
        assertEquals("outside", HydraValues.part("车外"));
        assertEquals("trunk", HydraValues.part("trunk"));
    }

    @Test public void themeCanonicalizesKnownAliases() {
        assertEquals("family", HydraValues.theme("family-friendly"));
        assertEquals("family", HydraValues.theme("families"));
        assertEquals("family", HydraValues.theme("家庭"));
        assertEquals("entertainment", HydraValues.theme("娱乐"));
        assertEquals("privacy", HydraValues.theme("私密"));
        assertEquals("spooky", HydraValues.theme("spooky"));
    }

    @Test public void choiceParsesOrdinalsDigitsAndWordNumbers() {
        assertEquals("2", HydraValues.choice("the second one"));
        assertEquals("2", HydraValues.choice("option two"));
        assertEquals("3", HydraValues.choice("number 3"));
        assertEquals("2", HydraValues.choice("2nd option"));
        assertEquals("2", HydraValues.choice("two"));
        assertEquals("1", HydraValues.choice("one"));
        assertEquals("10", HydraValues.choice("the tenth one"));
        assertEquals("5", HydraValues.choice("第五个 5"));
    }

    @Test public void choicePassesUnparseableTextThroughVerbatim() {
        assertEquals("the blue one", HydraValues.choice("the blue one"));
        assertEquals("that one there", HydraValues.choice("that one there"));
    }
}
