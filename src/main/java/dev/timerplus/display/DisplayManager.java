package dev.timerplus.display;

import dev.timerplus.GameTimer;
import dev.timerplus.TimerManager;
import dev.timerplus.TimerPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Zeigt den Timer per Actionbar und/oder Bossbar an. */
public final class DisplayManager {

    private final TimerPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private BukkitTask task;
    private long ticks = 0L;

    public DisplayManager(TimerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        int interval = Math.max(1, plugin.getConfig().getInt("display.update-interval-ticks", 2));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> update(interval), 1L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        hideAll();
    }

    public void remove(UUID id) {
        bars.remove(id);
    }

    private void update(int interval) {
        ticks += interval;
        FileConfiguration cfg = plugin.getConfig();
        String type = cfg.getString("display.type", "actionbar").toLowerCase(Locale.ROOT);
        boolean actionbar = type.equals("actionbar") || type.equals("both");
        boolean bossbar = type.equals("bossbar") || type.equals("both");
        boolean showIdle = cfg.getBoolean("display.show-when-idle", true);
        int period = Math.max(1, cfg.getInt("display.animation-period-ticks", 80));
        final double phase = ((ticks % period) / (double) period) * 2.0 - 1.0;

        TimerManager timers = plugin.timers();
        DesignManager designs = plugin.designs();
        Map<String, Component> cache = new HashMap<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            final GameTimer timer = timers.timerFor(player);
            if (timer == null || (!showIdle && timer.state() == GameTimer.State.IDLE)) {
                removeBar(player);
                continue;
            }
            final String designName = timers.designFor(player);
            String key = System.identityHashCode(timer) + ":" + designName;
            Component component = cache.computeIfAbsent(key,
                    k -> designs.render(designs.get(designName), timer, phase));

            if (actionbar) player.sendActionBar(component);
            if (bossbar) showBar(player, component, timer);
            else removeBar(player);
        }
    }

    private void showBar(Player player, Component name, GameTimer timer) {
        FileConfiguration cfg = plugin.getConfig();
        float progress = 1.0f;
        if (timer.direction() == GameTimer.Direction.DOWN
                && timer.targetMillis() > 0
                && cfg.getBoolean("display.bossbar.countdown-progress", true)) {
            progress = (float) Math.min(1.0, Math.max(0.0, timer.displayMillis() / (double) timer.targetMillis()));
        }
        BossBar.Color color = parseColor(cfg.getString("display.bossbar.color", "WHITE"));
        BossBar.Overlay overlay = parseOverlay(cfg.getString("display.bossbar.overlay", "PROGRESS"));

        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(name, progress, color, overlay);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(name);
            bar.progress(progress);
            bar.color(color);
            bar.overlay(overlay);
        }
    }

    private void removeBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    private void hideAll() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) p.hideBossBar(entry.getValue());
        }
        bars.clear();
    }

    private static BossBar.Color parseColor(String name) {
        try {
            return BossBar.Color.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.WHITE;
        }
    }

    private static BossBar.Overlay parseOverlay(String name) {
        try {
            return BossBar.Overlay.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }
}
