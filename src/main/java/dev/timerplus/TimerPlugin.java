package dev.timerplus;

import dev.timerplus.command.TimerCommand;
import dev.timerplus.display.DesignManager;
import dev.timerplus.display.DisplayManager;
import dev.timerplus.gui.SetupGui;
import dev.timerplus.listener.TimerListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class TimerPlugin extends JavaPlugin {

    private Messages messages;
    private DesignManager designs;
    private TimerManager timers;
    private DisplayManager display;
    private SetupGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        messages.load();
        designs = new DesignManager(this);
        designs.load();
        timers = new TimerManager(this);
        timers.load();
        display = new DisplayManager(this);
        display.start();
        gui = new SetupGui(this);

        getServer().getPluginManager().registerEvents(new TimerListener(this), this);
        getServer().getPluginManager().registerEvents(gui, this);

        register("timer", new TimerCommand(this, false));
        register("ptimer", new TimerCommand(this, true));

        getServer().getScheduler().runTaskTimer(this, timers::tick, 1L, 1L);
        long autosaveTicks = Math.max(10L, getConfig().getLong("autosave-seconds", 60L)) * 20L;
        getServer().getScheduler().runTaskTimer(this, timers::save, autosaveTicks, autosaveTicks);

        // Andere Plugins koennen den Manager ueber den ServicesManager abrufen
        getServer().getServicesManager().register(TimerManager.class, timers, this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (timers != null) timers.save();
        if (display != null) display.stop();
    }

    private void register(String name, TimerCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Befehl /" + name + " fehlt in der plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public void reload() {
        reloadConfig();
        messages.load();
        designs.load();
        display.start();
    }

    public Messages messages() {
        return messages;
    }

    public DesignManager designs() {
        return designs;
    }

    public TimerManager timers() {
        return timers;
    }

    public DisplayManager display() {
        return display;
    }

    public SetupGui gui() {
        return gui;
    }
}
