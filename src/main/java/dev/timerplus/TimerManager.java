package dev.timerplus;

import dev.timerplus.event.TimerStateChangeEvent;
import dev.timerplus.util.TimeFormat;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Verwaltet den globalen Timer, persoenliche Timer, Spieler-Einstellungen und Persistenz. */
public final class TimerManager {

    private final TimerPlugin plugin;
    private final File dataFile;
    private final GameTimer global = new GameTimer();
    private final Map<UUID, GameTimer> personal = new HashMap<>();
    private final Set<UUID> optedOut = new HashSet<>();
    private final Map<UUID, String> playerDesigns = new HashMap<>();
    private long bestMillis = -1L;
    private boolean autoPaused = false;

    public TimerManager(TimerPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    // ------------------------------------------------------------------ Zugriff

    public GameTimer global() {
        return global;
    }

    public GameTimer personal(UUID id, boolean create) {
        if (create) return personal.computeIfAbsent(id, k -> new GameTimer());
        return personal.get(id);
    }

    /** Welcher Timer wird dem Spieler angezeigt? (null = keiner) */
    public GameTimer timerFor(Player player) {
        if (optedOut.contains(player.getUniqueId())) {
            return personal.get(player.getUniqueId());
        }
        return global;
    }

    public boolean isOptedOut(UUID id) {
        return optedOut.contains(id);
    }

    public void setOptedOut(UUID id, boolean out) {
        if (out) optedOut.add(id);
        else optedOut.remove(id);
    }

    /** @return true, wenn der Spieler den globalen Timer jetzt ausgeblendet hat */
    public boolean toggleOptOut(UUID id) {
        boolean nowOut = !optedOut.contains(id);
        setOptedOut(id, nowOut);
        return nowOut;
    }

    public String designFor(Player player) {
        String own = playerDesigns.get(player.getUniqueId());
        if (own != null && plugin.designs().exists(own)) return own;
        return plugin.getConfig().getString("display.design", "default");
    }

    public void setPlayerDesign(UUID id, String name) {
        if (name == null) playerDesigns.remove(id);
        else playerDesigns.put(id, name);
    }

    public long bestMillis() {
        return bestMillis;
    }

    // ------------------------------------------------------------------ Ziele

    public static String normalizeEntity(String raw) {
        String v = raw.toLowerCase(Locale.ROOT).trim();
        return v.startsWith("minecraft:") ? v.substring("minecraft:".length()) : v;
    }

    public static String normalizeAdvancement(String raw) {
        String v = raw.toLowerCase(Locale.ROOT).trim();
        return v.contains(":") ? v : "minecraft:" + v;
    }

    public Set<String> goalEntities() {
        Set<String> out = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("goals.entities")) out.add(normalizeEntity(s));
        return out;
    }

    public Set<String> goalAdvancements() {
        Set<String> out = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("goals.advancements")) out.add(normalizeAdvancement(s));
        return out;
    }

    // ------------------------------------------------------------------ Aktionen

    public boolean resume(GameTimer timer, UUID owner) {
        GameTimer.State old = timer.state();
        if (!timer.start()) return false;
        if (timer == global) autoPaused = false;
        fire(owner, old, timer);
        return true;
    }

    public boolean pause(GameTimer timer, UUID owner) {
        GameTimer.State old = timer.state();
        if (!timer.pause()) return false;
        if (timer == global) autoPaused = false;
        fire(owner, old, timer);
        return true;
    }

    public void reset(GameTimer timer, UUID owner) {
        GameTimer.State old = timer.state();
        timer.reset();
        if (timer == global) autoPaused = false;
        fire(owner, old, timer);
    }

    /** Beendet den Timer. {@code goal} ist der Name des erreichten Ziels oder null. */
    public boolean finish(GameTimer timer, UUID owner, String goal) {
        GameTimer.State old = timer.state();
        if (!timer.finish()) return false;
        fire(owner, old, timer);
        playFinishEffects(timer, owner, goal);
        return true;
    }

    private void fire(UUID owner, GameTimer.State old, GameTimer timer) {
        Bukkit.getPluginManager().callEvent(new TimerStateChangeEvent(owner, old, timer.state(), timer.displayMillis()));
    }

    private void playFinishEffects(GameTimer timer, UUID owner, String goal) {
        FileConfiguration cfg = plugin.getConfig();
        Messages msg = plugin.messages();
        long elapsed = timer.elapsedMillis();
        String time = TimeFormat.plain(elapsed);
        List<Player> audience = audience(owner);

        if (cfg.getBoolean("finish.broadcast", true)) {
            for (Player p : audience) {
                p.sendMessage(msg.prefixed("timer-finished", Messages.tag("time", time)));
                if (goal != null) {
                    p.sendMessage(msg.prefixed("goal-reached", Messages.tag("goal", goal)));
                }
            }
        }
        if (cfg.getBoolean("finish.title", true)) {
            Title title = Title.title(
                    msg.component("finish-title"),
                    msg.component("finish-subtitle", Messages.tag("time", time)),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1)));
            for (Player p : audience) p.showTitle(title);
        }
        playSound(audience, cfg.getString("finish.sound", ""));

        if (owner == null) {
            if (goal != null && timer.direction() == GameTimer.Direction.UP) {
                long old = bestMillis;
                if (old < 0 || elapsed < old) {
                    bestMillis = elapsed;
                    if (old >= 0) {
                        Component c = msg.prefixed("new-best",
                                Messages.tag("time", time), Messages.tag("old", TimeFormat.plain(old)));
                        for (Player p : audience) p.sendMessage(c);
                    }
                }
            }
            for (String command : cfg.getStringList("finish.commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{time}", time));
            }
        }
    }

    private List<Player> audience(UUID owner) {
        if (owner == null) return new ArrayList<>(Bukkit.getOnlinePlayers());
        Player p = Bukkit.getPlayer(owner);
        List<Player> out = new ArrayList<>();
        if (p != null) out.add(p);
        return out;
    }

    public static void playSound(Collection<Player> players, String key) {
        if (key == null || key.isBlank()) return;
        try {
            Sound sound = Sound.sound(Key.key(key), Sound.Source.MASTER, 1.0f, 1.0f);
            for (Player p : players) p.playSound(sound);
        } catch (RuntimeException ignored) {
            // ungueltiger Sound-Name in der Config
        }
    }

    // ------------------------------------------------------------------ Tick

    /** Wird jeden Tick aufgerufen: Countdown-Ende und Warnungen. */
    public void tick() {
        tickTimer(global, null);
        for (Map.Entry<UUID, GameTimer> entry : new ArrayList<>(personal.entrySet())) {
            tickTimer(entry.getValue(), entry.getKey());
        }
    }

    private void tickTimer(GameTimer timer, UUID owner) {
        if (!timer.isRunning() || timer.direction() != GameTimer.Direction.DOWN) return;
        long remaining = timer.displayMillis();
        if (remaining <= 0L) {
            finish(timer, owner, null);
            return;
        }
        long seconds = (remaining + 999L) / 1000L;
        if (seconds == timer.lastWarnedSecond()) return;
        timer.lastWarnedSecond(seconds);
        if (plugin.getConfig().getIntegerList("countdown.warn-at-seconds").contains((int) seconds)) {
            warn(owner, seconds);
        }
    }

    private void warn(UUID owner, long seconds) {
        Messages msg = plugin.messages();
        List<Player> audience = audience(owner);
        Component chat = msg.prefixed("countdown-warn", Messages.tag("seconds", seconds));
        Title title = Title.title(
                Component.text(Long.toString(seconds), net.kyori.adventure.text.format.NamedTextColor.YELLOW),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
        for (Player p : audience) {
            p.sendMessage(chat);
            if (seconds <= 5) p.showTitle(title);
        }
        playSound(audience, plugin.getConfig().getString("countdown.warn-sound", ""));
    }

    // ------------------------------------------------------------------ Regeln (Server leer / Join)

    /** Eine Tick nach dem Quit aufrufen: pausiert den Timer, wenn niemand mehr online ist. */
    public void checkEmpty() {
        if (!plugin.getConfig().getBoolean("rules.pause-when-empty", true)) return;
        if (!Bukkit.getOnlinePlayers().isEmpty()) return;
        if (global.isRunning()) {
            pause(global, null);
            autoPaused = true;
        }
    }

    public void onJoin() {
        FileConfiguration cfg = plugin.getConfig();
        if (autoPaused) {
            autoPaused = false;
            if (cfg.getBoolean("rules.resume-when-player-joins", true)) {
                resume(global, null);
            }
        } else if (cfg.getBoolean("rules.auto-start-on-join", false)
                && global.state() == GameTimer.State.IDLE
                && !(global.direction() == GameTimer.Direction.DOWN && global.targetMillis() <= 0)) {
            resume(global, null);
        }
    }

    // ------------------------------------------------------------------ Persistenz

    public void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        boolean resume = plugin.getConfig().getBoolean("rules.resume-on-startup", false);

        bestMillis = yaml.getLong("best", -1L);
        autoPaused = yaml.getBoolean("auto-paused", false);

        ConfigurationSection g = yaml.getConfigurationSection("global");
        if (g != null) global.load(g, resume && !autoPaused);

        ConfigurationSection p = yaml.getConfigurationSection("personal");
        if (p != null) {
            for (String key : p.getKeys(false)) {
                ConfigurationSection s = p.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    GameTimer timer = new GameTimer();
                    timer.load(s, resume);
                    personal.put(UUID.fromString(key), timer);
                } catch (IllegalArgumentException ignored) {
                    // ungueltige UUID
                }
            }
        }
        for (String s : yaml.getStringList("opted-out")) {
            try {
                optedOut.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // ungueltige UUID
            }
        }
        ConfigurationSection d = yaml.getConfigurationSection("designs");
        if (d != null) {
            for (String key : d.getKeys(false)) {
                try {
                    playerDesigns.put(UUID.fromString(key), d.getString(key, "default"));
                } catch (IllegalArgumentException ignored) {
                    // ungueltige UUID
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        global.save(yaml.createSection("global"));
        yaml.set("best", bestMillis);
        yaml.set("auto-paused", autoPaused);
        ConfigurationSection p = yaml.createSection("personal");
        for (Map.Entry<UUID, GameTimer> entry : personal.entrySet()) {
            entry.getValue().save(p.createSection(entry.getKey().toString()));
        }
        List<String> out = new ArrayList<>();
        for (UUID id : optedOut) out.add(id.toString());
        yaml.set("opted-out", out);
        ConfigurationSection d = yaml.createSection("designs");
        for (Map.Entry<UUID, String> entry : playerDesigns.entrySet()) {
            d.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte data.yml nicht speichern: " + e.getMessage());
        }
    }
}
