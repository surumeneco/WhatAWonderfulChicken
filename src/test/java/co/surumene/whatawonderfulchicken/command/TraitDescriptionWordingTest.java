package co.surumene.whatawonderfulchicken.command;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class TraitDescriptionWordingTest {
    @Test
    void japaneseTraitDescriptionsUseApprovedWording() throws Exception {
        var expected = Map.of(
                "jikiden", "子供が能力を受け継ぎやすい。",
                "saikutsu_ouen", "近くにいると仕事が早くなる。",
                "oyogi_jouzu", "水の中でもへっちゃら。",
                "yukiguni_umare", "冷たいところでもへっちゃら。"
        );
        try (var stream = getClass().getClassLoader().getResourceAsStream("lang/ja_jp.yml")) {
            assertNotNull(stream);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            expected.forEach((key, value) -> assertEquals(
                    value, yaml.getString("trait." + key + ".description"), key));
        }
    }
}
