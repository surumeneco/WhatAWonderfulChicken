package co.surumene.whatawonderfulchicken.config;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
        migrateLegacyDisplayNames("lang/ja_jp.yml", japaneseNameMigrations());
        migrateLegacyDisplayNames("lang/en_us.yml", englishNameMigrations());
        ja = loadWithBundledDefaults("lang/ja_jp.yml");
        en = loadWithBundledDefaults("lang/en_us.yml");
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

    public String rank(CommandSender sender, String rankKey) {
        return bundle(sender).getString("rank." + rankKey, rankKey);
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

    private void migrateLegacyDisplayNames(String path, Map<String, String[]> migrations) {
        File file = new File(plugin.getDataFolder(), path);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (Map.Entry<String, String[]> entry : migrations.entrySet()) {
            String current = config.getString(entry.getKey());
            String[] values = entry.getValue();
            if (current != null && current.equals(values[0])) {
                config.set(entry.getKey(), values[1]);
                changed = true;
            }
        }
        if (!changed) return;
        try {
            config.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to migrate language file " + path + ": " + ex.getMessage());
        }
    }

    private Map<String, String[]> japaneseNameMigrations() {
        Map<String, String[]> migrations = new LinkedHashMap<>();
        migrations.put("error.no_target", new String[]{"対象となるデカ鳥が見つかりません。", "対象となるわんだふるちきんが見つかりません。"});
        migrations.put("error.inventory_busy", new String[]{"このデカ鳥の荷物は別のプレイヤーが操作中です。", "このわんだふるちきんの荷物は別のプレイヤーが操作中です。"});
        migrations.put("command.modified", new String[]{"{0} 羽のデカ鳥を変更しました。", "{0} 羽のわんだふるちきんを変更しました。"});
        migrations.put("command.summoned", new String[]{"デカ鳥を召喚しました: {0}", "わんだふるちきんを召喚しました: {0}"});
        migrations.put("command.header", new String[]{"--- デカ鳥情報 ---", "--- わんだふるちきん情報 ---"});
        migrations.put("command.info_header", new String[]{"デカ鳥 {0}/{1}", "わんだふるちきん {0}/{1}"});
        migrations.put("gui.title", new String[]{"デカ鳥", "わんだふるちきん"});
        return migrations;
    }

    private Map<String, String[]> englishNameMigrations() {
        Map<String, String[]> migrations = new LinkedHashMap<>();
        migrations.put("error.no_target", new String[]{"No wonderful chicken was found.", "No Wonderful Chicken was found."});
        migrations.put("error.inventory_busy", new String[]{"Another player is currently using this chicken's cargo.", "Another player is currently using this Wonderful Chicken's cargo."});
        migrations.put("command.modified", new String[]{"Modified {0} wonderful chicken(s).", "Modified {0} Wonderful Chicken(s)."});
        migrations.put("command.summoned", new String[]{"Summoned wonderful chicken: {0}", "Summoned Wonderful Chicken: {0}"});
        return migrations;
    }

    private YamlConfiguration loadWithBundledDefaults(String path) {
        YamlConfiguration result = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), path));
        try (var stream = plugin.getResource(path)) {
            if (stream != null) {
                YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                result.setDefaults(bundled);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load bundled language defaults for " + path + ": " + ex.getMessage());
        }
        return result;
    }

    private void saveIfMissing(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) plugin.saveResource(path, false);
    }
}