package dev.timerplus.gui;

import dev.timerplus.GameTimer;
import dev.timerplus.Messages;
import dev.timerplus.TimerManager;
import dev.timerplus.TimerPlugin;
import dev.timerplus.command.TimerCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Einfaches Einstellungs-Menue (/timer setup und /ptimer setup). */
public final class SetupGui implements Listener {

    public enum Mode { MAIN, PERSONAL }

    private static final List<String> DISPLAY_TYPES = List.of("actionbar", "bossbar", "both");

    private static final class Holder implements InventoryHolder {
        private final Mode mode;
        private Inventory inventory;

        private Holder(Mode mode) {
            this.mode = mode;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final TimerPlugin plugin;

    public SetupGui(TimerPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Mode mode) {
        Holder holder = new Holder(mode);
        Inventory inventory = Bukkit.createInventory(holder, 27, plugin.messages().component("gui-title"));
        holder.inventory = inventory;
        render(holder, player);
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------ Anzeige

    private void render(Holder holder, Player viewer) {
        Inventory inv = holder.inventory;
        Messages m = plugin.messages();
        TimerManager tm = plugin.timers();
        FileConfiguration cfg = plugin.getConfig();
        GameTimer timer = holder.mode == Mode.MAIN ? tm.global() : tm.personal(viewer.getUniqueId(), true);

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), false);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        Component runName = timer.isRunning() ? m.component("gui-pause") : m.component("gui-run");
        Component directionName = m.component("gui-direction", Placeholder.component("value",
                m.component(timer.direction() == GameTimer.Direction.UP ? "mode-up" : "mode-down")));

        if (holder.mode == Mode.MAIN) {
            inv.setItem(10, item(Material.CLOCK, runName, true));
            inv.setItem(11, item(Material.BARRIER, m.component("gui-reset"), true));
            inv.setItem(12, item(Material.COMPARATOR, directionName, true));
            inv.setItem(13, item(Material.ENDER_EYE,
                    m.component("gui-display", Messages.tag("value", cfg.getString("display.type", "actionbar"))), true));
            inv.setItem(14, item(Material.PAINTING,
                    m.component("gui-design", Messages.tag("value", cfg.getString("display.design", "default"))), true));
            inv.setItem(15, item(Material.DRAGON_HEAD,
                    m.component("gui-goal-dragon", onOff(tm.goalEntities().contains("ender_dragon"))), true));
            inv.setItem(16, item(Material.WITHER_SKELETON_SKULL,
                    m.component("gui-goal-wither", onOff(tm.goalEntities().contains("wither"))), true));
            inv.setItem(19, item(Material.TOTEM_OF_UNDYING,
                    m.component("gui-death-action", Messages.tag("value", cfg.getString("rules.death-action", "NONE"))), true));
            inv.setItem(20, item(Material.LEVER,
                    m.component("gui-pause-empty", onOff(cfg.getBoolean("rules.pause-when-empty", true))), true));
            inv.setItem(21, item(Material.REDSTONE_TORCH,
                    m.component("gui-auto-start", onOff(cfg.getBoolean("rules.auto-start-on-join", false))), true));
        } else {
            inv.setItem(11, item(Material.CLOCK, runName, true));
            inv.setItem(12, item(Material.BARRIER, m.component("gui-reset"), true));
            inv.setItem(13, item(Material.COMPARATOR, directionName, true));
            inv.setItem(15, item(Material.PAINTING,
                    m.component("gui-design", Messages.tag("value", tm.designFor(viewer))), true));
        }
    }

    private TagResolver onOff(boolean on) {
        return Placeholder.component("value", plugin.messages().component(on ? "value-on" : "value-off"));
    }

    private ItemStack item(Material material, Component name, boolean withLore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (withLore) {
            meta.lore(List.of(plugin.messages().component("gui-click").decoration(TextDecoration.ITALIC, false)));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------------ Klicks

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        if (holder.mode == Mode.MAIN && !player.hasPermission("timerplus.timer.control")) {
            plugin.messages().send(player, "no-permission");
            player.closeInventory();
            return;
        }

        if (holder.mode == Mode.MAIN) handleMain(player, event.getRawSlot());
        else handlePersonal(player, event.getRawSlot());

        render(holder, player);
    }

    private void handleMain(Player player, int slot) {
        TimerManager tm = plugin.timers();
        FileConfiguration cfg = plugin.getConfig();
        GameTimer timer = tm.global();

        switch (slot) {
            case 10 -> toggleRun(tm, timer, null);
            case 11 -> tm.reset(timer, null);
            case 12 -> toggleDirection(timer);
            case 13 -> cfg.set("display.type", next(DISPLAY_TYPES, cfg.getString("display.type", "actionbar")));
            case 14 -> cfg.set("display.design",
                    next(new ArrayList<>(plugin.designs().names()), cfg.getString("display.design", "default")));
            case 15 -> toggleGoal("ender_dragon");
            case 16 -> toggleGoal("wither");
            case 19 -> cfg.set("rules.death-action",
                    next(TimerCommand.DEATH_ACTIONS, cfg.getString("rules.death-action", "NONE")));
            case 20 -> cfg.set("rules.pause-when-empty", !cfg.getBoolean("rules.pause-when-empty", true));
            case 21 -> cfg.set("rules.auto-start-on-join", !cfg.getBoolean("rules.auto-start-on-join", false));
            default -> { return; }
        }
        plugin.saveConfig();
    }

    private void handlePersonal(Player player, int slot) {
        TimerManager tm = plugin.timers();
        UUID id = player.getUniqueId();
        GameTimer timer = tm.personal(id, true);

        switch (slot) {
            case 11 -> {
                if (!timer.isRunning() && !tm.isOptedOut(id)) {
                    tm.setOptedOut(id, true);
                    plugin.messages().send(player, "ptimer-switched");
                }
                toggleRun(tm, timer, id);
            }
            case 12 -> tm.reset(timer, id);
            case 13 -> toggleDirection(timer);
            case 15 -> {
                String current = tm.designFor(player);
                tm.setPlayerDesign(id, next(new ArrayList<>(plugin.designs().names()), current));
            }
            default -> { }
        }
    }

    private void toggleRun(TimerManager tm, GameTimer timer, UUID owner) {
        if (timer.isRunning()) {
            tm.pause(timer, owner);
        } else if (!(timer.direction() == GameTimer.Direction.DOWN && timer.targetMillis() <= 0)) {
            tm.resume(timer, owner);
        }
    }

    private void toggleDirection(GameTimer timer) {
        boolean toDown = timer.direction() == GameTimer.Direction.UP;
        timer.setDirection(toDown ? GameTimer.Direction.DOWN : GameTimer.Direction.UP);
        if (toDown && timer.targetMillis() <= 0) timer.setTarget(300_000L);
    }

    private void toggleGoal(String entity) {
        FileConfiguration cfg = plugin.getConfig();
        List<String> list = new ArrayList<>(cfg.getStringList("goals.entities"));
        boolean removed = list.removeIf(s -> TimerManager.normalizeEntity(s).equals(entity));
        if (!removed) list.add(entity);
        cfg.set("goals.entities", list);
    }

    private static String next(List<String> options, String current) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(current)) {
                return options.get((i + 1) % options.size());
            }
        }
        return options.get(0);
    }
}
