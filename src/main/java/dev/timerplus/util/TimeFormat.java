package dev.timerplus.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeFormat {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 86_400_000L;

    private static final Pattern GROUP = Pattern.compile("\\[([^\\[\\]]*)]");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(hh|h|mm|m|ss|s|ms|d)}");
    private static final String PLAIN = "[{d}d ][{h}h ][{m}m ]{s}.{ms}s";

    private TimeFormat() { }

    /** Einheitliches Format fuer Chat-Nachrichten, z. B. "1h 3m 5.123s". */
    public static String plain(long millis) {
        return format(PLAIN, millis);
    }

    public static String format(String pattern, long millis) {
        long ms = Math.max(0L, millis);
        boolean hasDays = pattern.contains("{d}");
        boolean hasHours = pattern.contains("{h}") || pattern.contains("{hh}");
        boolean hasMinutes = pattern.contains("{m}") || pattern.contains("{mm}");

        long days = ms / DAY;
        long hours = hasDays ? (ms / HOUR) % 24 : ms / HOUR;
        long minutes = hasHours ? (ms / MINUTE) % 60 : ms / MINUTE;
        long seconds = hasMinutes ? (ms / SECOND) % 60 : ms / SECOND;
        long millisPart = ms % 1000;

        Matcher group = GROUP.matcher(pattern);
        StringBuilder reduced = new StringBuilder();
        while (group.find()) {
            String inner = group.group(1);
            group.appendReplacement(reduced, Matcher.quoteReplacement(isVisible(inner, ms) ? inner : ""));
        }
        group.appendTail(reduced);

        Matcher placeholder = PLACEHOLDER.matcher(reduced);
        StringBuilder out = new StringBuilder();
        while (placeholder.find()) {
            String value = switch (placeholder.group(1)) {
                case "d" -> Long.toString(days);
                case "h" -> Long.toString(hours);
                case "hh" -> pad(hours, 2);
                case "m" -> Long.toString(minutes);
                case "mm" -> pad(minutes, 2);
                case "s" -> Long.toString(seconds);
                case "ss" -> pad(seconds, 2);
                default -> pad(millisPart, 3);
            };
            placeholder.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        placeholder.appendTail(out);
        return out.toString();
    }

    private static boolean isVisible(String inner, long millis) {
        Matcher m = PLACEHOLDER.matcher(inner);
        boolean any = false;
        long threshold = 0L;
        while (m.find()) {
            any = true;
            threshold = Math.max(threshold, unit(m.group(1)));
        }
        return !any || millis >= threshold;
    }

    private static long unit(String key) {
        return switch (key) {
            case "d" -> DAY;
            case "h", "hh" -> HOUR;
            case "m", "mm" -> MINUTE;
            case "s", "ss" -> SECOND;
            default -> 0L;
        };
    }

    private static String pad(long value, int length) {
        String s = Long.toString(value);
        return "0".repeat(Math.max(0, length - s.length())) + s;
    }
}
