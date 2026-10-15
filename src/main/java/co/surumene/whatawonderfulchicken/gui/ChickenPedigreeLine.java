package co.surumene.whatawonderfulchicken.gui;

import java.util.List;
import java.util.Objects;

/** Shared compact ancestry row for the pedigree item and clicked details. */
public final class ChickenPedigreeLine {
    private ChickenPedigreeLine() {}

    public static String format(String name, int generation, String marker,
                                String personality, List<String> traits, String emptyLabel) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(traits, "traits");
        Objects.requireNonNull(emptyLabel, "emptyLabel");
        String nature = personality == null ? emptyLabel : personality;
        String names = personality == null || traits.isEmpty()
                ? emptyLabel : String.join("/", traits);
        return name + "(" + generation + ") " + marker + " " + nature + " " + names;
    }
}
