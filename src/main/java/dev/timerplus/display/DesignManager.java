package dev.timerplus.display;

import dev.timerplus.GameTimer;
import dev.timerplus.TimerPlugin;
import dev.timerplus.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DesignManager {

    private static final String DEFAULT_FORMAT = "[{d}d ][{h}:]{mm}:{ss}";

    private final TimerPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, Design> designs = new LinkedHashMap<>();

    public DesignManager(TimerPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        designs.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("designs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection d = section.getConfigurationSection(key);
                if (d == null) continue;
                String name = key.toLowerCase(Locale.ROOT);
                designs.put(name, new Design(
                        name,
                        d.getString("time-format", DEFAULT_FORMAT),
                        d.getString("idle", "<gray>{time}"),
                        d.getString("running", "<white>{time}"),
                        d.getString("finished", "<gold>{time}")));
            }
        }
        if (designs.isEmpty()) {
            designs.put("default", new Design("default", DEFAULT_FORMAT,
                    "<gray>{time}", "<white>{time}", "<gold>{time}"));
        }
    }

    public boolean exists(String name) {
        return name != null && designs.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Design get(String name) {
        Design d = name == null ? null : designs.get(name.toLowerCase(Locale.ROOT));
        return d != null ? d : designs.values().iterator().next();
    }

    public Collection<String> names() {
        return designs.keySet();
    }

    public Component render(Design design, GameTimer timer, double phase) {
        long millis = timer.displayMillis();
        if (timer.direction() == GameTimer.Direction.DOWN && !design.timeFormat().contains("{ms}")) {
            // Countdown aufrunden, damit "00:00" erst beim echten Ende erscheint
            millis = ((millis + 999L) / 1000L) * 1000L;
        }
        String time = TimeFormat.format(design.timeFormat(), millis);
        String template = switch (timer.state()) {
            case RUNNING -> design.running();
            case FINISHED -> design.finished();
            default -> design.idle();
        };
        String input = template
                .replace("{time}", time)
                .replace("%phase%", String.format(Locale.ROOT, "%.3f", phase));
        try {
            return mm.deserialize(input);
        } catch (RuntimeException e) {
            return Component.text(time);
        }
    }
}
