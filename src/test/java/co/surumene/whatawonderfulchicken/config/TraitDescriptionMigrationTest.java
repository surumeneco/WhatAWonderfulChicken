package co.surumene.whatawonderfulchicken.config;

import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class TraitDescriptionMigrationTest {
    private static final Map<String, String[]> DEFAULT_CHANGES = Map.ofEntries(
            Map.entry("jikiden", new String[]{"自分の能力を必ず1つ子供に伝える。", "子供が能力を受け継ぎやすい。"}),
            Map.entry("saikutsu_ouen", new String[]{"近くにいると採掘速度が上がる。", "近くにいると仕事が早くなる。"}),
            Map.entry("oyogi_jouzu", new String[]{"水中でも溺れない。", "水の中でもへっちゃら。"}),
            Map.entry("yukiguni_umare", new String[]{"凍える寒さの影響を受けない。", "冷たいところでもへっちゃら。"})
    );

    @Test
    void migratesAllBundledOldDescriptionsOnce() {
        var yaml = new YamlConfiguration();
        DEFAULT_CHANGES.forEach((key, pair) -> yaml.set("trait." + key + ".description", pair[0]));
        assertTrue(MessageService.migrateJapaneseTraitDescriptions(yaml));
        DEFAULT_CHANGES.forEach((key, pair) ->
                assertEquals(pair[1], yaml.getString("trait." + key + ".description"), key));
        assertFalse(MessageService.migrateJapaneseTraitDescriptions(yaml));
    }

    @Test
    void keepsCustomDescriptionsAndUnrelatedKeys() {
        var yaml = new YamlConfiguration();
        var first = DEFAULT_CHANGES.keySet().iterator().next();
        yaml.set("trait." + first + ".description", "カスタム翻訳");
        yaml.set("trait." + first + ".name", "独自名称");
        assertFalse(MessageService.migrateJapaneseTraitDescriptions(yaml));
        assertEquals("カスタム翻訳", yaml.getString("trait." + first + ".description"));
        assertEquals("独自名称", yaml.getString("trait." + first + ".name"));
    }
}
