package dev.timerplus.listener;

import dev.timerplus.GameTimer;
import dev.timerplus.Messages;
import dev.timerplus.TimerManager;
import dev.timerplus.TimerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Automatisierung: Ziele (Goals) und Regeln (Rules). */
public final class TimerListener implements Listener {

    private final TimerPlugin plugin;
    private final Set<UUID> pendingSpectator = new HashSet<>();

    public TimerListener(TimerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.timers().onJoin();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.display().remove(id);
        pendingSpectator.remove(id);
        // Einen Tick warten, bis der Spieler wirklich aus der Online-Liste ist
        Bukkit.getScheduler().runTask(plugin, () -> plugin.timers().checkEmpty());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        TimerManager timers = plugin.timers();
        GameTimer global = timers.global();
        if (!global.isRunning()) return;
        String key = event.getEntityType().getKey().getKey();
        if (timers.goalEntities().contains(key)) {
            timers.finish(global, null, key);
        }
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        TimerManager timers = plugin.timers();
        GameTimer global = timers.global();
        if (!global.isRunning()) return;
        String key = event.getAdvancement().getKey().toString();
        if (timers.goalAdvancements().contains(key)) {
            timers.finish(global, null, key);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        TimerManager timers = plugin.timers();
        GameTimer global = timers.global();
        FileConfiguration cfg = plugin.getConfig();
        Messages msg = plugin.messages();

        if (cfg.getBoolean("rules.only-while-running", true) && !global.isRunning()) return;
        if (player.hasPermission("timerplus.bypass")) return;

        String action = cfg.getString("rules.death-action", "NONE").toUpperCase(Locale.ROOT);
        switch (action) {
            case "SPECTATOR" -> pendingSpectator.add(player.getUniqueId());
            case "KICK" -> Bukkit.getScheduler().runTask(plugin,
                    () -> player.kick(msg.component("death-kick")));
            case "PAUSE_TIMER" -> {
                if (timers.pause(global, null)) {
                    Bukkit.broadcast(msg.prefixed("death-timer-paused", Messages.tag("player", player.getName())));
                }
            }
            case "STOP_TIMER" -> timers.finish(global, null, null);
            case "RESET_TIMER" -> {
                timers.reset(global, null);
                Bukkit.broadcast(msg.prefixed("death-timer-reset", Messages.tag("player", player.getName())));
            }
            default -> { }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (pendingSpectator.remove(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
        }
    }
}
