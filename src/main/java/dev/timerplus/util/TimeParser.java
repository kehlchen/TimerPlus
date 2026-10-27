package dev.timerplus.util;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParser {

    private static final Pattern UNITS =
            Pattern.compile("^(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$");

    private TimeParser() { }

    /**
     * Versteht "30", "30s", "5m", "1h30m", "1d2h", "01:30" (min:sek), "1:30:00" (h:min:sek).
     * Ein Vorzeichen (+/-) ist erlaubt. Ergebnis in Millisekunden.
     */
    public static OptionalLong parse(String input) {
        if (input == null) return OptionalLong.empty();
        String s = input.trim().toLowerCase(Locale.ROOT);
        boolean negative = s.startsWith("-");
        if (negative || s.startsWith("+")) s = s.substring(1);
        if (s.isEmpty()) return OptionalLong.empty();

        try {
            long seconds;
            if (s.contains(":")) {
                String[] parts = s.split(":", -1);
                if (parts.length < 2 || parts.length > 3) return OptionalLong.empty();
                long total = 0;
                for (String part : parts) {
                    if (!part.matches("\\d+")) return OptionalLong.empty();
                    total = total * 60 + Long.parseLong(part);
                }
                seconds = total;
            } else if (s.matches("\\d+")) {
                seconds = Long.parseLong(s);
            } else {
                Matcher m = UNITS.matcher(s);
                if (!m.matches()) return OptionalLong.empty();
                seconds = group(m, 1) * 86_400L + group(m, 2) * 3_600L + group(m, 3) * 60L + group(m, 4);
            }
            long millis = Math.multiplyExact(seconds, 1000L);
            return OptionalLong.of(negative ? -millis : millis);
        } catch (RuntimeException e) {
            return OptionalLong.empty();
        }
    }

    private static long group(Matcher m, int index) {
        String value = m.group(index);
        return value == null ? 0L : Long.parseLong(value);
    }
}
