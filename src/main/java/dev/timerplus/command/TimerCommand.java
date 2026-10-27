package dev.timerplus.command;

import dev.timerplus.GameTimer;
import dev.timerplus.Messages;
import dev.timerplus.TimerManager;
import dev.timerplus.TimerPlugin;
import dev.timerplus.display.DesignManager;
import dev.timerplus.gui.SetupGui;
import dev.timerplus.util.TimeFormat;
import dev.timerplus.util.TimeParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;

/** Eine Klasse fuer /timer (global) und /ptimer (persoenlich). */
public final class TimerCommand implements TabExecutor {

    public static final List<String> DEATH_ACTIONS =
            List.of("NONE", "SPECTATOR", "KICK", "PAUSE_TIMER", "STOP_TIMER", "RESET_TIMER");

    private static final List<String> COMMON =
            List.of("resume", "pause", "reset", "stop", "set", "add", "mode", "lap", "laps", "design", "setup", "info", "help");
    private static final List<String> GLOBAL_ONLY = List.of("toggle", "reload", "goal", "rule", "language");
    private static final List<String> RULES =
            List.of("death-action", "pause-when-empty", "auto-start-on-join", "resume-on-startup", "only-while-running");

    private final TimerPlugin plugin;
    private final boolean personal;

    public TimerCommand(TimerPlugin plugin, boolean personal) {
        this.plugin = plugin;
        this.personal = personal;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();
        TimerManager tm = plugin.timers();

        if (personal) {
            if (!(sender instanceof Player)) {
                msg.send(sender, "player-only");
                return true;
            }
            if (!sender.hasPermission("timerplus.ptimer")) {
                msg.send(sender, "no-permission");
                return true;
            }
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (personal && GLOBAL_ONLY.contains(sub)) {
            msg.send(sender, "unknown-command");
            return true;
        }

        Player player = sender instanceof Player p ? p : null;
        UUID owner = personal ? player.getUniqueId() : null;
        GameTimer timer = personal ? tm.personal(owner, true) : tm.global();

        switch (sub) {
            case "resume", "start" -> {
                if (denied(sender)) return true;
                if (timer.direction() == GameTimer.Direction.DOWN && timer.targetMillis() <= 0) {
                    msg.send(sender, "countdown-needs-time");
                } else if (tm.resume(timer, owner)) {
                    if (personal && !tm.isOptedOut(owner)) {
                        tm.setOptedOut(owner, true);
                        msg.send(sender, "ptimer-switched");
                    }
                    announce(sender, "timer-resumed");
                } else {
                    msg.send(sender, "timer-already-running");
                }
            }
            case "pause" -> {
                if (denied(sender)) return true;
                if (tm.pause(timer, owner)) announce(sender, "timer-paused");
                else msg.send(sender, "timer-not-active");
            }
            case "reset" -> {
                if (denied(sender)) return true;
                tm.reset(timer, owner);
                announce(sender, "timer-reset");
            }
            case "stop" -> {
                if (denied(sender)) return true;
                if (!tm.finish(timer, owner, null)) msg.send(sender, "timer-not-active");
            }
            case "set" -> {
                if (denied(sender)) return true;
                if (args.length < 2) {
                    usage(sender, label, "set [zeit]");
                    return true;
                }
                OptionalLong parsed = TimeParser.parse(args[1]);
                if (parsed.isEmpty() || parsed.getAsLong() < 0) {
                    msg.send(sender, "invalid-time");
                    return true;
                }
                long ms = parsed.getAsLong();
                if (timer.direction() == GameTimer.Direction.DOWN) {
                    if (ms <= 0) {
                        msg.send(sender, "invalid-time");
                        return true;
                    }
                    timer.setTarget(ms);
                } else {
                    timer.setElapsed(ms);
                }
                announce(sender, "timer-set", Messages.tag("time", TimeFormat.plain(ms)));
            }
            case "add" -> {
                if (denied(sender)) return true;
                if (args.length < 2) {
                    usage(sender, label, "add [zeit]");
                    return true;
                }
                OptionalLong parsed = TimeParser.parse(args[1]);
                if (parsed.isEmpty()) {
                    msg.send(sender, "invalid-time");
                    return true;
                }
                long ms = parsed.getAsLong();
                timer.adjust(ms);
                String shown = (ms >= 0 ? "+" : "-") + TimeFormat.plain(Math.abs(ms));
                announce(sender, "timer-adjusted", Messages.tag("time", shown));
            }
            case "mode" -> {
                if (denied(sender)) return true;
                if (args.length < 2) {
                    usage(sender, label, "mode up|down");
                    return true;
                }
                GameTimer.Direction dir = switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "up", "stopwatch", "stoppuhr" -> GameTimer.Direction.UP;
                    case "down", "countdown" -> GameTimer.Direction.DOWN;
                    default -> null;
                };
                if (dir == null) {
                    usage(sender, label, "mode up|down");
                    return true;
                }
                timer.setDirection(dir);
                if (dir == GameTimer.Direction.DOWN && timer.targetMillis() <= 0) timer.setTarget(300_000L);
                announce(sender, "mode-set", Placeholder.component("mode",
                        msg.component(dir == GameTimer.Direction.UP ? "mode-up" : "mode-down")));
                if (dir == GameTimer.Direction.DOWN) {
                    msg.send(sender, "mode-down-hint", Messages.tag("time", TimeFormat.plain(timer.targetMillis())));
                }
            }
            case "lap" -> {
                if (denied(sender)) return true;
                if (timer.state() != GameTimer.State.RUNNING && timer.state() != GameTimer.State.PAUSED) {
                    msg.send(sender, "timer-not-active");
                    return true;
                }
                String name = args.length > 1
                        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        : "#" + (timer.laps().size() + 1);
                timer.addLap(name);
                announce(sender, "lap-added", Messages.tag("name", name),
                        Messages.tag("time", TimeFormat.plain(timer.displayMillis())));
            }
            case "laps" -> {
                List<GameTimer.Lap> laps = timer.laps();
                if (laps.isEmpty()) {
                    msg.send(sender, "laps-empty");
                    return true;
                }
                sender.sendMessage(msg.prefixed("laps-header"));
                int index = 1;
                for (GameTimer.Lap lap : laps) {
                    sender.sendMessage(msg.component("laps-line",
                            Messages.tag("index", index++),
                            Messages.tag("name", lap.name()),
                            Messages.tag("time", TimeFormat.plain(lap.millis()))));
                }
            }
            case "design" -> handleDesign(sender, player, args);
            case "setup" -> {
                if (player == null) {
                    msg.send(sender, "player-only");
                    return true;
                }
                if (denied(sender)) return true;
                plugin.gui().open(player, personal ? SetupGui.Mode.PERSONAL : SetupGui.Mode.MAIN);
            }
            case "info" -> {
                long best = tm.bestMillis();
                List<Component> lines = msg.list("info",
                        Placeholder.component("state", msg.component("state-" + timer.state().name().toLowerCase(Locale.ROOT))),
                        Placeholder.component("mode", msg.component(
                                timer.direction() == GameTimer.Direction.UP ? "mode-up" : "mode-down")),
                        Messages.tag("time", TimeFormat.plain(timer.displayMillis())),
                        Messages.tag("best", best < 0 ? "-" : TimeFormat.plain(best)));
                for (Component line : lines) sender.sendMessage(line);
            }
            case "help" -> sendHelp(sender);
            case "toggle" -> {
                if (player == null) {
                    msg.send(sender, "player-only");
                    return true;
                }
                if (!sender.hasPermission("timerplus.timer.toggle")) {
                    msg.send(sender, "no-permission");
                    return true;
                }
                boolean out = tm.toggleOptOut(player.getUniqueId());
                msg.send(sender, out ? "toggle-optout" : "toggle-optin");
            }
            case "reload" -> {
                if (denied(sender)) return true;
                plugin.reload();
                msg.send(sender, "reload-done");
            }
            case "language" -> {
                if (denied(sender)) return true;
                if (args.length < 2) {
                    usage(sender, label, "language [de|en]");
                    return true;
                }
                String code = args[1].toLowerCase(Locale.ROOT);
                if (!msg.languageExists(code)) {
                    msg.send(sender, "invalid-arg");
                    return true;
                }
                plugin.getConfig().set("language", code);
                plugin.saveConfig();
                msg.load();
                msg.send(sender, "language-set", Messages.tag("language", code));
            }
            case "goal" -> {
                if (denied(sender)) return true;
                handleGoal(sender, label, args);
            }
            case "rule" -> {
                if (denied(sender)) return true;
                handleRule(sender, label, args);
            }
            default -> msg.send(sender, "unknown-command");
        }
        return true;
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    private boolean denied(CommandSender sender) {
        if (personal || sender.hasPermission("timerplus.timer.control")) return false;
        plugin.messages().send(sender, "no-permission");
        return true;
    }

    /** Globale Aktionen gehen an alle, persoenliche nur an den Spieler selbst. */
    private void announce(CommandSender sender, String key, TagResolver... resolvers) {
        Component component = plugin.messages().prefixed(key, resolvers);
        if (personal) sender.sendMessage(component);
        else Bukkit.broadcast(component);
    }

    private void usage(CommandSender sender, String label, String text) {
        plugin.messages().send(sender, "usage", Messages.tag("usage", "/" + label + " " + text));
    }

    private void sendHelp(CommandSender sender) {
        Messages msg = plugin.messages();
        TagResolver cmd = Messages.tag("cmd", personal ? "ptimer" : "timer");
        for (Component line : msg.list("help", cmd)) sender.sendMessage(line);
        if (!personal) {
            for (Component line : msg.list("help-global", cmd)) sender.sendMessage(line);
        }
    }

    private void handleDesign(CommandSender sender, Player player, String[] args) {
        Messages msg = plugin.messages();
        DesignManager designs = plugin.designs();
        String all = String.join(", ", designs.names());

        if (args.length < 2) {
            msg.send(sender, "design-list", Messages.tag("designs", all));
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (personal && name.equals("reset")) {
            plugin.timers().setPlayerDesign(player.getUniqueId(), null);
            msg.send(sender, "design-reset");
            return;
        }
        if (!designs.exists(name)) {
            msg.send(sender, "design-unknown", Messages.tag("designs", all));
            return;
        }
        if (personal) {
            plugin.timers().setPlayerDesign(player.getUniqueId(), name);
            msg.send(sender, "design-set", Messages.tag("design", name));
        } else {
            if (denied(sender)) return;
            plugin.getConfig().set("display.design", name);
            plugin.saveConfig();
            announce(sender, "design-set", Messages.tag("design", name));
        }
    }

    private void handleGoal(CommandSender sender, String label, String[] args) {
        Messages msg = plugin.messages();
        FileConfiguration cfg = plugin.getConfig();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";

        if (action.equals("list")) {
            msg.send(sender, "goal-list",
                    Messages.tag("entities", joinOrDash(cfg.getStringList("goals.entities"))),
                    Messages.tag("advancements", joinOrDash(cfg.getStringList("goals.advancements"))));
            return;
        }
        if (args.length < 3 || !(action.equals("add") || action.equals("remove"))) {
            usage(sender, label, "goal list|add|remove [entity/advancement]");
            return;
        }

        String raw = args[2];
        boolean advancement = raw.contains("/");
        String path = advancement ? "goals.advancements" : "goals.entities";
        String value = advancement ? TimerManager.normalizeAdvancement(raw) : TimerManager.normalizeEntity(raw);
        List<String> list = new ArrayList<>(cfg.getStringList(path));

        if (action.equals("add")) {
            if (!validGoal(value, advancement)) {
                msg.send(sender, "goal-unknown", Messages.tag("goal", raw));
                return;
            }
            boolean exists = list.stream().anyMatch(s -> normalize(s, advancement).equals(value));
            if (!exists) list.add(value);
            msg.send(sender, "goal-added", Messages.tag("goal", value));
        } else {
            list.removeIf(s -> normalize(s, advancement).equals(value));
            msg.send(sender, "goal-removed", Messages.tag("goal", value));
        }
        cfg.set(path, list);
        plugin.saveConfig();
    }

    private static String normalize(String s, boolean advancement) {
        return advancement ? TimerManager.normalizeAdvancement(s) : TimerManager.normalizeEntity(s);
    }

    private boolean validGoal(String value, boolean advancement) {
        try {
            if (advancement) {
                NamespacedKey key = NamespacedKey.fromString(value);
                return key != null && Bukkit.getAdvancement(key) != null;
            }
            return Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(value)) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void handleRule(CommandSender sender, String label, String[] args) {
        Messages msg = plugin.messages();
        if (args.length < 3 || !RULES.contains(args[1].toLowerCase(Locale.ROOT))) {
            usage(sender, label, "rule [regel] [wert]");
            return;
        }
        String rule = args[1].toLowerCase(Locale.ROOT);
        String raw = args[2];
        Object value;
        if (rule.equals("death-action")) {
            String upper = raw.toUpperCase(Locale.ROOT);
            if (!DEATH_ACTIONS.contains(upper)) {
                msg.send(sender, "invalid-arg");
                return;
            }
            value = upper;
        } else {
            if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
                msg.send(sender, "invalid-arg");
                return;
            }
            value = Boolean.parseBoolean(raw);
        }
        plugin.getConfig().set("rules." + rule, value);
        plugin.saveConfig();
        msg.send(sender, "rule-set", Messages.tag("rule", rule), Messages.tag("value", value));
    }

    private static String joinOrDash(List<String> list) {
        return list.isEmpty() ? "-" : String.join(", ", list);
    }

    // ------------------------------------------------------------------ Tab-Completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(COMMON);
            if (!personal) options.addAll(GLOBAL_ONLY);
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "mode":
                    return filter(List.of("up", "down"), args[1]);
                case "design": {
                    List<String> names = new ArrayList<>(plugin.designs().names());
                    if (personal) names.add("reset");
                    return filter(names, args[1]);
                }
                case "set":
                case "add":
                    return filter(List.of("30s", "5m", "10m", "1h", "1h30m"), args[1]);
                case "language":
                    return filter(List.of("de", "en"), args[1]);
                case "goal":
                    return filter(List.of("list", "add", "remove"), args[1]);
                case "rule":
                    return filter(RULES, args[1]);
                default:
                    return List.of();
            }
        }
        if (args.length == 3 && !personal) {
            if (sub.equals("goal")) {
                String action = args[1].toLowerCase(Locale.ROOT);
                if (action.equals("add")) {
                    return filter(List.of("ender_dragon", "wither", "warden", "elder_guardian"), args[2]);
                }
                if (action.equals("remove")) {
                    List<String> current = new ArrayList<>(plugin.getConfig().getStringList("goals.entities"));
                    current.addAll(plugin.getConfig().getStringList("goals.advancements"));
                    return filter(current, args[2]);
                }
            }
            if (sub.equals("rule")) {
                String rule = args[1].toLowerCase(Locale.ROOT);
                if (rule.equals("death-action")) return filter(DEATH_ACTIONS, args[2]);
                return filter(List.of("true", "false"), args[2]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) out.add(option);
        }
        return out;
    }
}
