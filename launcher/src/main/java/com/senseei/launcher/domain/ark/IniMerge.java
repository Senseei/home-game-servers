package com.senseei.launcher.domain.ark;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges the keys of one INI's section into another, preserving everything else
 * — line order, other sections, and keys the source never mentions (e.g. the
 * image-managed {@code ServerPassword}). Pure: strings in, string out, no I/O.
 *
 * <p>This is the heart of "apply a map's config": overlay a map's (or the
 * default's) {@code [ServerSettings]} onto the live {@code GameUserSettings.ini}
 * without clobbering what the server wrote there.
 */
public final class IniMerge {

    private IniMerge() {
    }

    /**
     * Returns {@code live} with the {@code section} keys from {@code source}
     * applied: existing keys updated in place, new keys appended to the section.
     * If {@code source} has no such keys, {@code live} is returned unchanged.
     */
    public static String merge(String live, String source, String section) {
        Map<String, String> overrides = sectionKeys(source, section);
        if (overrides.isEmpty()) {
            return live;
        }

        List<String> lines = new ArrayList<>(live.lines().toList());
        int start = indexOfSection(lines, section);

        if (start < 0) {
            lines.add("");
            lines.add(section);
            overrides.forEach((k, v) -> lines.add(k + "=" + v));
            return String.join("\n", lines) + "\n";
        }

        int end = indexOfNextSection(lines, start + 1);

        List<String> body = new ArrayList<>();
        Set<String> applied = new HashSet<>();
        for (int i = start + 1; i < end; i++) {
            String key = keyOf(lines.get(i));
            if (key != null && overrides.containsKey(key)) {
                body.add(key + "=" + overrides.get(key));
                applied.add(key);
            } else {
                body.add(lines.get(i));
            }
        }
        overrides.forEach((k, v) -> {
            if (!applied.contains(k)) {
                body.add(k + "=" + v);
            }
        });

        List<String> out = new ArrayList<>(lines.subList(0, start + 1));
        out.addAll(body);
        out.addAll(lines.subList(end, lines.size()));
        return String.join("\n", out) + "\n";
    }

    /** KEY=VALUE pairs inside {@code section} (inline {@code ;} comments stripped). */
    static Map<String, String> sectionKeys(String content, String section) {
        Map<String, String> keys = new LinkedHashMap<>();
        String current = null;
        for (String raw : content.lines().toList()) {
            String s = stripComment(raw).strip();
            if (isSectionHeader(s)) {
                current = s;
            } else if (section.equalsIgnoreCase(current) && s.contains("=")) {
                int eq = s.indexOf('=');
                keys.put(s.substring(0, eq).strip(), s.substring(eq + 1).strip());
            }
        }
        return keys;
    }

    private static int indexOfSection(List<String> lines, String section) {
        for (int i = 0; i < lines.size(); i++) {
            if (section.equalsIgnoreCase(lines.get(i).strip())) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfNextSection(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (isSectionHeader(lines.get(i).strip())) {
                return i;
            }
        }
        return lines.size();
    }

    /** The key of a {@code KEY=VALUE} line, or null if the line isn't one. */
    private static String keyOf(String line) {
        String s = line.strip();
        int eq = s.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String key = s.substring(0, eq).strip();
        return key.matches("[A-Za-z0-9_\\[\\]]+") ? key : null;
    }

    private static boolean isSectionHeader(String s) {
        return s.startsWith("[") && s.endsWith("]");
    }

    private static String stripComment(String line) {
        int sc = line.indexOf(';');
        return sc >= 0 ? line.substring(0, sc) : line;
    }
}
