package com.allfire.qqbutton;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionExecutor implements Listener {

    private final QQButtonPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, List<String>> playerCommands = new HashMap<>();
    private final Map<UUID, Integer> taskIds = new HashMap<>();

    public ActionExecutor(QQButtonPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void setPlayerCommands(UUID playerId, List<String> commands) {
        playerCommands.put(playerId, commands);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerCommands.remove(playerId);
        Integer taskId = taskIds.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().toLowerCase();
        
        if (command.startsWith("/openlink")) {
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();
            
            List<String> commands = playerCommands.get(playerId);
            if (commands != null && !commands.isEmpty()) {
                event.setCancelled(true);
                executeActions(player, commands);
                playerCommands.remove(playerId);
            }
        }
    }

    public void executeActions(Player player, List<String> actions) {
        if (actions == null || actions.isEmpty()) return;
        executeNextAction(player, new ArrayList<>(actions), 0);
    }

    private void executeNextAction(Player player, List<String> actions, int index) {
        if (index >= actions.size()) return;
        
        String action = actions.get(index);
        if (action == null || action.isEmpty()) {
            executeNextAction(player, actions, index + 1);
            return;
        }

        if (action.startsWith("delay!")) {
            try {
                String delayStr = action.substring(6).trim();
                int delayTicks = Integer.parseInt(delayStr);
                
                int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    executeNextAction(player, actions, index + 1);
                }, delayTicks).getTaskId();
                
                taskIds.put(player.getUniqueId(), taskId);
                return;
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Ошибка парсинга delay! " + action);
                executeNextAction(player, actions, index + 1);
            }
            return;
        }

        executeAction(player, action);
        executeNextAction(player, actions, index + 1);
    }

    public void executeAction(Player player, String action) {
        if (action == null || action.isEmpty()) return;

        try {
            action = action.replace("%prefix%", plugin.getConfigManager().getPrefix());

            if (action.startsWith("sound!")) {
                handleSound(player, action.substring(6).trim());
            } else if (action.startsWith("gSound!")) {
                handleGlobalSound(player, action.substring(7).trim());
            } else if (action.startsWith("message!")) {
                handleMessage(player, action.substring(8).trim());
            } else if (action.startsWith("gMessage!")) {
                handleGlobalMessage(player, action.substring(9).trim());
            } else if (action.startsWith("actionbar:")) {
                handleActionbarWithDuration(player, action);
            } else if (action.startsWith("gActionbar:")) {
                handleGlobalActionbarWithDuration(player, action);
            } else if (action.startsWith("actionbar!")) {
                handleActionbar(player, action.substring(10).trim(), 60);
            } else if (action.startsWith("gActionbar!")) {
                handleGlobalActionbar(player, action.substring(11).trim(), 60);
            } else if (action.startsWith("title:")) {
                handleTitleWithTimings(player, action);
            } else if (action.startsWith("gTitle:")) {
                handleGlobalTitleWithTimings(player, action);
            } else if (action.startsWith("title!")) {
                handleTitle(player, action.substring(6).trim(), 20, 40, 20);
            } else if (action.startsWith("gTitle!")) {
                handleGlobalTitle(player, action.substring(7).trim(), 20, 40, 20);
            } else if (action.startsWith("asConsole!")) {
                handleConsoleCommand(player, action.substring(10).trim());
            } else if (action.startsWith("asPlayer!")) {
                handlePlayerCommand(player, action.substring(9).trim());
            } else {
                handleMessage(player, action);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при выполнении действия: " + action);
            e.printStackTrace();
        }
    }

    private void handleSound(Player player, String soundParams) {
        try {
            String[] parts = soundParams.split(" ");
            if (parts.length < 3) {
                plugin.getLogger().warning("Неверный формат sound! ТРЕБУЕТСЯ: НАЗВАНИЕ ГРОМКОСТЬ ТОН");
                return;
            }
            Sound sound = Sound.valueOf(parts[0]);
            float volume = Float.parseFloat(parts[1]);
            float pitch = Float.parseFloat(parts[2]);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неизвестный звук: " + soundParams);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка воспроизведения звука: " + soundParams);
        }
    }

    private void handleGlobalSound(Player player, String soundParams) {
        try {
            String[] parts = soundParams.split(" ");
            if (parts.length < 3) {
                plugin.getLogger().warning("Неверный формат gSound! ТРЕБУЕТСЯ: НАЗВАНИЕ ГРОМКОСТЬ ТОН");
                return;
            }
            Sound sound = Sound.valueOf(parts[0]);
            float volume = Float.parseFloat(parts[1]);
            float pitch = Float.parseFloat(parts[2]);
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.playSound(onlinePlayer.getLocation(), sound, volume, pitch);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неизвестный звук: " + soundParams);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка воспроизведения глобального звука: " + soundParams);
        }
    }

    private void handleMessage(Player player, String message) {
        player.sendMessage(formatForDisplay(message, player));
    }

    private void handleGlobalMessage(Player player, String message) {
        Bukkit.broadcast(formatForDisplay(message, player));
    }

    private void handleActionbar(Player player, String message, int duration) {
        Component component = formatForDisplay(message, player);
        player.sendActionBar(component);
        
        int refreshTicks = plugin.getConfig().getInt("settings.actionbar-refresh-ticks", 20);
        if (duration > refreshTicks && refreshTicks > 0) {
            int repeats = duration / refreshTicks;
            for (int i = 1; i <= repeats; i++) {
                int taskId = Bukkit.getScheduler().runTaskLater(plugin, 
                    () -> player.sendActionBar(component), i * (long)refreshTicks).getTaskId();
                taskIds.put(player.getUniqueId(), taskId);
            }
        }
    }

    private void handleActionbarWithDuration(Player player, String action) {
        try {
            String[] parts = action.substring(10).split("!", 2);
            String durationStr = parts[0].trim();
            int duration = Integer.parseInt(durationStr);
            
            if (parts.length > 1) {
                handleActionbar(player, parts[1].trim(), duration);
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Ошибка парсинга длительности actionbar: " + action);
        }
    }

    private void handleGlobalActionbar(Player player, String message, int duration) {
        Component component = formatForDisplay(message, player);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendActionBar(component);
        }
        
        int refreshTicks = plugin.getConfig().getInt("settings.actionbar-refresh-ticks", 20);
        if (duration > refreshTicks && refreshTicks > 0) {
            int repeats = duration / refreshTicks;
            for (int i = 1; i <= repeats; i++) {
                int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        onlinePlayer.sendActionBar(component);
                    }
                }, i * (long)refreshTicks).getTaskId();
                taskIds.put(player.getUniqueId(), taskId);
            }
        }
    }

    private void handleGlobalActionbarWithDuration(Player player, String action) {
        try {
            String[] parts = action.substring(11).split("!", 2);
            String durationStr = parts[0].trim();
            int duration = Integer.parseInt(durationStr);
            
            if (parts.length > 1) {
                handleGlobalActionbar(player, parts[1].trim(), duration);
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Ошибка парсинга длительности gActionbar: " + action);
        }
    }

    private void handleTitle(Player player, String titleText, int fadeIn, int stay, int fadeOut) {
        String text = PlaceholderAPI.setPlaceholders(player, titleText);
        Component title;
        Component subtitle = Component.empty();
        
        if (text.contains("\\n")) {
            String[] parts = text.split("\\\\n", 2);
            title = parseMessage(parts[0]);
            subtitle = parseMessage(parts[1]);
        } else {
            title = parseMessage(text);
        }
        
        Title.Times times = Title.Times.times(
            Ticks.duration(fadeIn),
            Ticks.duration(stay),
            Ticks.duration(fadeOut)
        );
        player.showTitle(Title.title(title, subtitle, times));
    }

    private void handleTitleWithTimings(Player player, String action) {
        try {
            String[] parts = action.substring(6).split("!", 2);
            if (parts.length > 1) {
                String[] times = parts[0].split(":");
                if (times.length < 3) {
                    plugin.getLogger().warning("Неверный формат title: требуется 3 параметра времени (fadeIn:stay:fadeOut)");
                    return;
                }
                
                int fadeIn = Integer.parseInt(times[0].trim());
                int stay = Integer.parseInt(times[1].trim());
                int fadeOut = Integer.parseInt(times[2].trim());
                handleTitle(player, parts[1].trim(), fadeIn, stay, fadeOut);
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Ошибка парсинга параметров title: " + action);
        }
    }

    private void handleGlobalTitle(Player player, String titleText, int fadeIn, int stay, int fadeOut) {
        String text = PlaceholderAPI.setPlaceholders(player, titleText);
        Component title;
        Component subtitle = Component.empty();
        
        if (text.contains("\\n")) {
            String[] parts = text.split("\\\\n", 2);
            title = parseMessage(parts[0]);
            subtitle = parseMessage(parts[1]);
        } else {
            title = parseMessage(text);
        }
        
        Title.Times times = Title.Times.times(
            Ticks.duration(fadeIn),
            Ticks.duration(stay),
            Ticks.duration(fadeOut)
        );
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showTitle(Title.title(title, subtitle, times));
        }
    }

    private void handleGlobalTitleWithTimings(Player player, String action) {
        try {
            String[] parts = action.substring(7).split("!", 2);
            if (parts.length > 1) {
                String[] times = parts[0].split(":");
                if (times.length < 3) {
                    plugin.getLogger().warning("Неверный формат gTitle: требуется 3 параметра времени (fadeIn:stay:fadeOut)");
                    return;
                }
                
                int fadeIn = Integer.parseInt(times[0].trim());
                int stay = Integer.parseInt(times[1].trim());
                int fadeOut = Integer.parseInt(times[2].trim());
                handleGlobalTitle(player, parts[1].trim(), fadeIn, stay, fadeOut);
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Ошибка парсинга параметров gTitle: " + action);
        }
    }

    private void handleConsoleCommand(Player player, String command) {
        command = formatForCommand(command, player);
        final String finalCommand = command;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        });
    }

    private void handlePlayerCommand(Player player, String command) {
        command = formatForCommand(command, player);
        final String finalCommand = command;
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.performCommand(finalCommand);
        });
    }

    private Component parseMessage(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        message = message.replace('§', '&');

        Pattern singleColorPattern = Pattern.compile("\\{#([A-Fa-f0-9]{6})\\}");
        Matcher singleMatcher = singleColorPattern.matcher(message);
        if (singleMatcher.find()) {
            message = singleMatcher.replaceAll("<#$1>");
        }

        // 3. Конвертируем градиенты {#FFFFFF>}текст{#000000<} в MiniMessage формат
        Pattern gradientPattern = Pattern.compile("\\{#([A-Fa-f0-9]{6})>\\}(.*?)\\{#([A-Fa-f0-9]{6})<\\}");
        Matcher gradientMatcher = gradientPattern.matcher(message);
        if (gradientMatcher.find()) {
            message = gradientMatcher.replaceAll("<gradient:#$1:#$3>$2</gradient>");
        }

        try {
            return MiniMessage.miniMessage().deserialize(message);
        } catch (Exception ignored) {
        }

        try {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        } catch (Exception ignored) {
            return Component.text(message);
        }
    }

    private String formatForCommand(String text, Player player) {
        text = PlaceholderAPI.setPlaceholders(player, text);
        try {
            return MiniMessage.miniMessage().serialize(parseMessage(text));
        } catch (Exception e) {
            return text;
        }
    }

    private Component formatForDisplay(String text, Player player) {
        text = PlaceholderAPI.setPlaceholders(player, text);
        return parseMessage(text);
    }
}
