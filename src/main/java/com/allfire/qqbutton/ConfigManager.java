package com.allfire.qqbutton;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private String prefix;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        prefix = config.getString("settings.prefix", "&7[&6QQB&7]");
    }

    public String getPrefix() {
        return prefix;
    }

    @SuppressWarnings("unchecked")
    public List<Map<?, ?>> getButtons() {
        List<Map<?, ?>> buttons = (List<Map<?, ?>>) config.getList("buttons");
        if (buttons == null) {
            return new ArrayList<>();
        }
        return buttons;
    }

    @SuppressWarnings("unchecked")
    public Map<?, ?> getDefaultButton() {
        return (Map<?, ?>) config.get("default");
    }

    @SuppressWarnings("unchecked")
    public List<Map<?, ?>> getConditions(Map<?, ?> button) {
        if (button.containsKey("conditions_and")) {
            return (List<Map<?, ?>>) button.get("conditions_and");
        }
        if (button.containsKey("conditions")) {
            return (List<Map<?, ?>>) button.get("conditions");
        }
        if (button.containsKey("conditions_any")) {
            return (List<Map<?, ?>>) button.get("conditions_any");
        }
        return null;
    }

    public boolean isOrMode(Map<?, ?> button) {
        return button.containsKey("conditions_any");
    }
}
