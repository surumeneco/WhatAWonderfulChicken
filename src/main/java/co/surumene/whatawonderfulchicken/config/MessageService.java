package co.surumene.whatawonderfulchicken.config;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Locale;

public final class MessageService {
    private final WhatAWonderfulChickenPlugin plugin;
    private YamlConfiguration ja;
    private YamlConfiguration en;

    public MessageService(WhatAWonderfulChickenPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        saveIfMissing("lang/ja_jp.yml");
        saveIfMissing("lang/en_us.yml");
        ja = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/ja_jp.yml"));
        en = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/en_us.yml"));
    }

    public String text(CommandSender sender, String key, Object... args) {
        return format(bundle(sender).getString(key, key), args);
    }

    public String text(Locale locale, String key, Object... args) {
        return format(bundle(locale).getString(key, key), args);
    }

    public String rank(Locale locale, String rankKey) {
        return bundle(locale).getString("rank." + rankKey, rankKey);
    }

    private YamlConfiguration bundle(CommandSender sender) {
        return sender instanceof Player player ? bundle(player.locale()) : ja;
    }

    private YamlConfiguration bundle(Locale locale) {
        return locale != null && locale.getLanguage().equalsIgnoreCase("ja") ? ja : en;
    }

    private String format(String source, Object... args) {
        String result = source;
        for (int i = 0; i < args.length; i++) result = result.replace("{" + i + "}", String.valueOf(args[i]));
        return result;
    }

    private void saveIfMissing(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) plugin.saveResource(path, false);
    }
}