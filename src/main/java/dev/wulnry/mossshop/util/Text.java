package dev.wulnry.mossshop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renk ve placeholder yardimcisi.
 * Hem klasik ({@code &7}) hem hex ({@code &#RRGGBB}) kodlarini Adventure
 * {@link Component} nesnesine donusturur. Itemlerde istenmeyen varsayilan
 * italik dekorasyonunu kaldirir.
 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    public static Component color(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> color(List<String> input) {
        List<Component> out = new ArrayList<>();
        if (input != null) {
            for (String line : input) {
                out.add(color(line));
            }
        }
        return out;
    }

    /**
     * Verilen placeholder haritasini ham metne uygular.
     */
    public static String apply(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public static List<String> apply(List<String> input, Map<String, String> placeholders) {
        List<String> out = new ArrayList<>();
        if (input != null) {
            for (String line : input) {
                out.add(apply(line, placeholders));
            }
        }
        return out;
    }
}
