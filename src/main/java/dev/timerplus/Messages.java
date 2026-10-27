package dev.timerplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Laedt Nachrichten aus lang/<sprache>.yml (Fallback: eingebaute Datei, dann Englisch). */
public final class Messages {

    private final TimerPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final List<YamlConfiguration> chain = new ArrayList<>();

    public Messages(TimerPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        chain.clear();
        String code = plugin.getConfig().getString("language", "de").toLowerCase(Locale.ROOT);

        for (String bundledCode : List.of("de", "en")) {
            String path = "lang/" + bundledCode + ".yml";
            if (!new File(plugin.getDataFolder(), path).exists()) {
                plugin.saveResource(path, false);
            }
        }

        File custom = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        if (custom.exists()) {
            chain.add(YamlConfiguration.loadConfiguration(custom));
        }
        YamlConfiguration sameBundled = bundled(code);
        if (sameBundled != null) chain.add(sameBundled);
        YamlConfiguration english = bundled("en");
        if (english != null) chain.add(english);
    }

    private YamlConfiguration bundled(String code) {
        InputStream in = plugin.getResource("lang/" + code + ".yml");
        if (in == null) return null;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            return null;
        }
    }

    private String raw(String key) {
        for (YamlConfiguration config : chain) {
            String value = config.getString(key);
            if (value != null) return value;
        }
        return key;
    }

    public boolean languageExists(String code) {
        return plugin.getResource("lang/" + code + ".yml") != null
                || new File(plugin.getDataFolder(), "lang/" + code + ".yml").exists();
    }

    /** Nachricht ohne Praefix. */
    public Component component(String key, TagResolver... resolvers) {
        return mm.deserialize(raw(key), resolvers);
    }

    /** Nachricht mit Praefix. */
    public Component prefixed(String key, TagResolver... resolvers) {
        return mm.deserialize(raw("prefix") + raw(key), resolvers);
    }

    public void send(CommandSender target, String key, TagResolver... resolvers) {
        target.sendMessage(prefixed(key, resolvers));
    }

    public List<Component> list(String key, TagResolver... resolvers) {
        for (YamlConfiguration config : chain) {
            if (config.isList(key)) {
                List<Component> out = new ArrayList<>();
                for (String line : config.getStringList(key)) {
                    out.add(mm.deserialize(line, resolvers));
                }
                return out;
            }
        }
        return List.of(Component.text(key));
    }

    public static TagResolver tag(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }
}
