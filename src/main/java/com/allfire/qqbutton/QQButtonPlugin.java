package com.allfire.qqbutton;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ServerLinks;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;

public final class QQButtonPlugin extends JavaPlugin implements Listener {

    private static QQButtonPlugin instance;
    private ConfigManager configManager;
    private ActionExecutor actionExecutor;
    private MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        actionExecutor = new ActionExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI не найден! Плейсхолдеры не будут работать.");
        }

        int updateInterval = getConfig().getInt("settings.update-interval", 30);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateServerLinks(player);
            }
        }, 0L, 20L * updateInterval);

        getLogger().info("QQButton v" + getDescription().getVersion() + " by AllFiRE включен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("QQButton выключен!");
    }

    public static QQButtonPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ActionExecutor getActionExecutor() {
        return actionExecutor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("qqbutton.admin")) {
                sender.sendMessage("§cУ вас нет прав на использование этой команды!");
                return true;
            }

            configManager.reload();
            reloadConfig();

            for (Player player : Bukkit.getOnlinePlayers()) {
                updateServerLinks(player);
            }

            sender.sendMessage("§aКонфигурация QQButton успешно перезагружена!");
            getLogger().info("Конфигурация перезагружена игроком " + sender.getName());
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== §eQQButton §6===");
        sender.sendMessage("§7/qqbutton reload §8- §7Перезагрузить конфигурацию");
        sender.sendMessage("§7/qqbutton help §8- §7Показать эту справку");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            updateServerLinks(event.getPlayer());
        }, 20L);
    }

    public void updateServerLinks(Player player) {
        ServerLinks serverLinks = Bukkit.getServerLinks();
        if (serverLinks == null) return;

        clearServerLinks(serverLinks);

        List<Map<?, ?>> buttons = configManager.getButtons();

        boolean matched = false;
        for (Map<?, ?> button : buttons) {
            String permission = (String) button.get("permission");
            if (permission != null && !player.hasPermission(permission)) {
                continue;
            }

            if (checkConditions(player, button)) {
                String name = (String) button.get("name");
                String url = (String) button.get("url");
                List<String> commands = (List<String>) button.get("commands");

                name = replacePlaceholders(player, name);
                url = replacePlaceholders(player, url);

                if (commands != null && !commands.isEmpty()) {
                    actionExecutor.setPlayerCommands(player.getUniqueId(), commands);
                }

                Component displayName = miniMessage.deserialize(name);
                serverLinks.addLink(displayName, URI.create(url));
                matched = true;
                break;
            }
        }

        if (!matched) {
            Map<?, ?> defaultButton = configManager.getDefaultButton();
            if (defaultButton != null) {
                String name = (String) defaultButton.get("name");
                String url = (String) defaultButton.get("url");
                List<String> commands = (List<String>) defaultButton.get("commands");

                name = replacePlaceholders(player, name);
                url = replacePlaceholders(player, url);

                if (commands != null && !commands.isEmpty()) {
                    actionExecutor.setPlayerCommands(player.getUniqueId(), commands);
                }

                Component displayName = miniMessage.deserialize(name);
                serverLinks.addLink(displayName, URI.create(url));
            }
        }

        sendServerLinksUpdate(player);
    }

    private void clearServerLinks(ServerLinks serverLinks) {
        try {
            Method clearMethod = serverLinks.getClass().getMethod("clear");
            clearMethod.invoke(serverLinks);
            return;
        } catch (Exception ignored) {}

        try {
            Method removeAllMethod = serverLinks.getClass().getMethod("removeAll");
            removeAllMethod.invoke(serverLinks);
        } catch (Exception ignored) {}
    }

    private void sendServerLinksUpdate(Player player) {
        try {
            Method updateMethod = player.getClass().getMethod("sendServerLinksUpdate");
            updateMethod.invoke(player);
        } catch (Exception ignored) {}
    }

    private boolean checkConditions(Player player, Map<?, ?> button) {
        List<Map<?, ?>> conditions = configManager.getConditions(button);
        boolean isOrMode = configManager.isOrMode(button);

        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        if (isOrMode) {
            for (Map<?, ?> condition : conditions) {
                if (checkSingleCondition(player, condition)) {
                    return true;
                }
            }
            return false;
        } else {
            for (Map<?, ?> condition : conditions) {
                if (!checkSingleCondition(player, condition)) {
                    return false;
                }
            }
            return true;
        }
    }

    private boolean checkSingleCondition(Player player, Map<?, ?> condition) {
        String placeholder = (String) condition.get("placeholder");
        String operator = (String) condition.get("operator");
        String value = (String) condition.get("value");

        if (placeholder == null || operator == null || value == null) {
            return true;
        }

        String replacedPlaceholder = replacePlaceholders(player, placeholder);
        String replacedValue = replacePlaceholders(player, value);

        return compare(replacedPlaceholder, operator, replacedValue);
    }

    private boolean compare(String actual, String operator, String expected) {
        try {
            switch (operator) {
                case "==":
                    return actual.equals(expected);
                case "!=":
                    return !actual.equals(expected);
                case "!~":
                    return !actual.contains(expected);
                case ">=":
                    return Double.parseDouble(actual) >= Double.parseDouble(expected);
                case "<=":
                    return Double.parseDouble(actual) <= Double.parseDouble(expected);
                case ">":
                    return Double.parseDouble(actual) > Double.parseDouble(expected);
                case "<":
                    return Double.parseDouble(actual) < Double.parseDouble(expected);
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String replacePlaceholders(Player player, String text) {
        if (text == null) return null;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }
}
