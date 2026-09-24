package co.surumene.whatawonderfulchicken.command;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.config.MessageService;
import co.surumene.whatawonderfulchicken.data.BehaviorMode;
import co.surumene.whatawonderfulchicken.data.Rank;
import co.surumene.whatawonderfulchicken.data.StatType;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import co.surumene.whatawonderfulchicken.util.CommandText;
import co.surumene.whatawonderfulchicken.util.ItemUtil;
import co.surumene.whatawonderfulchicken.util.SnbtLikeParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class CommandService {
    private static final Pattern DANGEROUS_UUID_NBT = Pattern.compile("(?i)(^|[,\\s{])UUID\\s*:");
    private final WhatAWonderfulChickenPlugin plugin;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final ConfigService config;
    private final MessageService messages;
    private final DisplayService displays;

    public CommandService(WhatAWonderfulChickenPlugin plugin, WonderfulChickenService chickens, WonderfulChickenStore store,
                          ConfigService config, MessageService messages, DisplayService displays) {
        this.plugin = plugin;
        this.chickens = chickens;
        this.store = store;
        this.config = config;
        this.messages = messages;
        this.displays = displays;
    }

    public com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("wwc");
        root.then(summonNode());
        root.then(infoNode());
        root.then(modifyNode());
        root.then(configNode());
        root.then(Commands.literal("reload")
                .requires(source -> source.getSender().hasPermission("wwc.command.reload"))
                .executes(this::reload));
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> summonNode() {
        return Commands.literal("summon")
                .requires(source -> source.getSender().hasPermission("wwc.command.summon"))
                .executes(ctx -> summon(ctx, ""))
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                        .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "arguments"))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> infoNode() {
        return Commands.literal("info")
                .requires(source -> source.getSender().hasPermission("wwc.command.info"))
                .executes(this::infoNearest)
                .then(Commands.argument("targets", ArgumentTypes.entities())
                        .executes(this::infoSelected));
    }

    private LiteralArgumentBuilder<CommandSourceStack> modifyNode() {
        var field = Commands.argument("field", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (StatType stat : StatType.values()) builder.suggest(stat.commandName());
                    builder.suggest("current-stamina");
                    builder.suggest("mode");
                    return builder.buildFuture();
                });
        var setValue = Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> modify(ctx, "set"));
        var addValue = Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> modify(ctx, "add"));
        return Commands.literal("modify")
                .requires(source -> source.getSender().hasPermission("wwc.command.modify"))
                .then(Commands.argument("targets", ArgumentTypes.entities())
                        .then(Commands.literal("set").then(field.then(setValue)))
                        .then(Commands.literal("add").then(
                                Commands.argument("field", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (StatType stat : StatType.values()) builder.suggest(stat.commandName());
                                            builder.suggest("current-stamina");
                                            return builder.buildFuture();
                                        }).then(addValue))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> configNode() {
        var root = Commands.literal("config")
                .requires(source -> source.getSender().hasPermission("wwc.command.config")
                        || source.getSender().hasPermission("wwc.command.config.get")
                        || source.getSender().hasPermission("wwc.command.config.list")
                        || source.getSender().hasPermission("wwc.command.config.set")
                        || source.getSender().hasPermission("wwc.command.config.reset"));
        root.then(Commands.literal("get")
                .requires(source -> hasConfigPermission(source.getSender(), "get"))
                .then(Commands.argument("path", StringArgumentType.word()).executes(this::configGet)));
        root.then(Commands.literal("set")
                .requires(source -> hasConfigPermission(source.getSender(), "set"))
                .then(Commands.argument("path", StringArgumentType.word())
                        .then(Commands.argument("value", StringArgumentType.greedyString()).executes(this::configSet))));
        root.then(Commands.literal("list")
                .requires(source -> hasConfigPermission(source.getSender(), "list"))
                .executes(ctx -> configList(ctx, ""))
                .then(Commands.argument("path", StringArgumentType.word()).executes(ctx -> configList(ctx, StringArgumentType.getString(ctx, "path")))));
        root.then(Commands.literal("reset")
                .requires(source -> hasConfigPermission(source.getSender(), "reset"))
                .then(Commands.literal("numeric")
                        .executes(ctx -> configResetCategory(ctx, "numeric", ""))
                        .then(Commands.argument("path", StringArgumentType.word()).executes(ctx -> configResetCategory(ctx, "numeric", StringArgumentType.getString(ctx, "path")))))
                .then(Commands.literal("other")
                        .executes(ctx -> configResetCategory(ctx, "other", ""))
                        .then(Commands.argument("path", StringArgumentType.word()).executes(ctx -> configResetCategory(ctx, "other", StringArgumentType.getString(ctx, "path")))))
                .then(Commands.argument("path", StringArgumentType.word()).executes(this::configReset)));
        return root;
    }

    private int summon(CommandContext<CommandSourceStack> ctx, String rawArguments) {
        CommandSender sender = ctx.getSource().getSender();
        try {
            ParsedSummon parsed = parseSummon(ctx.getSource(), rawArguments);
            World world = parsed.location().getWorld();
            if (world == null) throw new IllegalArgumentException("No world available at command source");
            Chicken chicken = world.spawn(parsed.location(), Chicken.class);
            WonderfulChickenData data = chickens.createNaturalData();
            if (parsed.wwcData() != null) applyWwcData(data, parsed.wwcData(), chicken);
            chickens.initialize(chicken, data);
            if (parsed.nbt() != null && !parsed.nbt().isBlank() && !parsed.nbt().equals("{}")) {
                if (DANGEROUS_UUID_NBT.matcher(parsed.nbt()).find()) throw new IllegalArgumentException("UUID cannot be overridden");
                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:data merge entity " + chicken.getUniqueId() + " " + parsed.nbt());
                if (!ok) throw new IllegalArgumentException("Vanilla NBT merge command failed");
            }
            store.save(chicken, data);
            chickens.projectAttributes(chicken);
            chickens.synchronizeBehaviorState(chicken, data);
            displays.rebuild(chicken);
            sender.sendMessage(messages.text(sender, "command.summoned", chicken.getUniqueId()));
            return Command.SINGLE_SUCCESS;
        } catch (Exception ex) {
            sender.sendMessage(messages.text(sender, "error.summon", ex.getMessage()));
            return 0;
        }
    }

    private int infoNearest(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Location location = ctx.getSource().getLocation();
        Chicken nearest = chickens.loadedChickens().stream()
                .filter(chicken -> chicken.getWorld() == location.getWorld())
                .min(Comparator.comparingDouble(chicken -> chicken.getLocation().distanceSquared(location)))
                .orElse(null);
        if (nearest == null) {
            sender.sendMessage(messages.text(sender, "error.no_target"));
            return 0;
        }
        sendInfo(sender, List.of(nearest));
        return Command.SINGLE_SUCCESS;
    }

    private int infoSelected(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        try {
            EntitySelectorArgumentResolver resolver = ctx.getArgument("targets", EntitySelectorArgumentResolver.class);
            List<Chicken> selected = resolver.resolve(ctx.getSource()).stream()
                    .filter(chickens::isWonderful).map(entity -> (Chicken) entity).toList();
            if (selected.isEmpty()) {
                sender.sendMessage(messages.text(sender, "error.no_target"));
                return 0;
            }
            if (selected.size() > config.infoMaxResults()) {
                sender.sendMessage(messages.text(sender, "error.too_many_targets", config.infoMaxResults()));
                return 0;
            }
            sendInfo(sender, selected);
            return selected.size();
        } catch (Exception ex) {
            sender.sendMessage(messages.text(sender, "error.invalid_value", ex.getMessage()));
            return 0;
        }
    }

    private int modify(CommandContext<CommandSourceStack> ctx, String operation) {
        CommandSender sender = ctx.getSource().getSender();
        String field = StringArgumentType.getString(ctx, "field");
        String rawValue = StringArgumentType.getString(ctx, "value").trim();
        try {
            EntitySelectorArgumentResolver resolver = ctx.getArgument("targets", EntitySelectorArgumentResolver.class);
            List<Chicken> selected = resolver.resolve(ctx.getSource()).stream()
                    .filter(chickens::isWonderful).map(entity -> (Chicken) entity).toList();
            if (selected.isEmpty()) {
                sender.sendMessage(messages.text(sender, "error.no_target"));
                return 0;
            }
            if (field.equalsIgnoreCase("mode")) {
                if (!operation.equals("set")) throw new IllegalArgumentException("mode supports set only");
                BehaviorMode mode = BehaviorMode.parse(rawValue);
                for (Chicken chicken : selected) {
                    WonderfulChickenData data = store.load(chicken);
                    data.behaviorMode(mode);
                    if (mode == BehaviorMode.FOLLOW && ctx.getSource().getExecutor() instanceof Player player) data.followTarget(player.getUniqueId());
                    else if (mode != BehaviorMode.FOLLOW) data.followTarget(null);
                    store.save(chicken, data);
                    chickens.synchronizeBehaviorState(chicken, data);
                }
            } else {
                double value = Double.parseDouble(rawValue);
                for (Chicken chicken : selected) modifyNumber(chicken, operation, field, value);
            }
            sender.sendMessage(messages.text(sender, "command.modified", selected.size()));
            return selected.size();
        } catch (Exception ex) {
            sender.sendMessage(messages.text(sender, "error.invalid_value", ex.getMessage()));
            return 0;
        }
    }

    private void modifyNumber(Chicken chicken, String operation, String field, double operand) {
        if (!Double.isFinite(operand)) throw new IllegalArgumentException("Non-finite number");
        WonderfulChickenData data = store.load(chicken);
        if (field.equalsIgnoreCase("current-stamina")) {
            double next = operation.equals("add") ? data.currentStamina() + operand : operand;
            if (next < 0) throw new IllegalArgumentException("current-stamina cannot be negative");
            data.currentStamina(Math.min(data.value(StatType.STAMINA), next));
        } else {
            StatType stat = StatType.fromCommandName(field).orElseThrow(() -> new IllegalArgumentException("Unknown field: " + field));
            double next = operation.equals("add") ? data.value(stat) + operand : operand;
            next = stat.canonicalizeValue(next);
            if (stat == StatType.MAX_HEALTH || stat == StatType.SIZE || stat == StatType.STAMINA) {
                if (next <= 0) throw new IllegalArgumentException(field + " must be > 0");
            } else if (next < 0) throw new IllegalArgumentException(field + " must be >= 0");
            data.value(stat, next);
            data.normalized(stat, store.toNormalized(stat, next));
            if (stat == StatType.STAMINA) data.currentStamina(Math.min(data.currentStamina(), next));
        }
        store.save(chicken, data);
        chickens.projectAttributes(chicken);
        displays.rebuild(chicken);
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        var result = config.reloadFromDisk();
        if (!result.valid()) {
            ctx.getSource().getSender().sendMessage(messages.text(ctx.getSource().getSender(), "error.invalid_config", String.join("; ", result.errors())));
            return 0;
        }
        messages.reload();
        chickens.resyncAllLoaded();
        displays.rebuildAllLoaded();
        ctx.getSource().getSender().sendMessage(messages.text(ctx.getSource().getSender(), "command.reload_ok"));
        return Command.SINGLE_SUCCESS;
    }

    private int configGet(CommandContext<CommandSourceStack> ctx) {
        String path = StringArgumentType.getString(ctx, "path");
        Object value = config.config().get(path);
        ctx.getSource().getSender().sendMessage(path + " = " + (value == null ? "<null>" : value));
        return value == null ? 0 : Command.SINGLE_SUCCESS;
    }

    private int configSet(CommandContext<CommandSourceStack> ctx) {
        String path = StringArgumentType.getString(ctx, "path");
        String raw = StringArgumentType.getString(ctx, "value").trim();
        Object defaultValue = config.defaults().get(path);
        if (defaultValue == null) {
            ctx.getSource().getSender().sendMessage(messages.text(ctx.getSource().getSender(), "error.invalid_config", "Unknown path: " + path));
            return 0;
        }
        Object value;
        try { value = coerce(raw, defaultValue); }
        catch (RuntimeException ex) {
            ctx.getSource().getSender().sendMessage(messages.text(ctx.getSource().getSender(), "error.invalid_value", ex.getMessage()));
            return 0;
        }
        var result = config.set(path, value);
        if (!result.valid()) {
            ctx.getSource().getSender().sendMessage(messages.text(ctx.getSource().getSender(), "error.invalid_config", String.join("; ", result.errors())));
            return 0;
        }
        chickens.resyncAllLoaded();
        displays.rebuildAllLoaded();
        ctx.getSource().getSender().sendMessage(messages.text(ctx.getSource().getSender(), "command.config_set", path, value));
        return Command.SINGLE_SUCCESS;
    }

    private int configList(CommandContext<CommandSourceStack> ctx, String path) {
        ConfigurationSection section = path == null || path.isBlank() ? config.config() : config.config().getConfigurationSection(path);
        if (section == null) {
            Object value = config.config().get(path);
            if (value != null) ctx.getSource().getSender().sendMessage(path + " = " + value);
            else ctx.getSource().getSender().sendMessage(messages.text(ctx.getSource().getSender(), "error.invalid_config", "Unknown path: " + path));
            return value == null ? 0 : Command.SINGLE_SUCCESS;
        }
        for (String key : section.getKeys(true)) {
            String full = path == null || path.isBlank() ? key : path + "." + key;
            if (!config.config().isConfigurationSection(full)) ctx.getSource().getSender().sendMessage(full + " = " + config.config().get(full));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int configReset(CommandContext<CommandSourceStack> ctx) {
        String path = StringArgumentType.getString(ctx, "path");
        var result = config.reset(path);
        return finishReset(ctx, result, path);
    }

    private int configResetCategory(CommandContext<CommandSourceStack> ctx, String kind, String path) {
        var result = config.resetCategory(kind, path);
        return finishReset(ctx, result, (path == null || path.isBlank() ? kind : kind + " " + path));
    }

    private int finishReset(CommandContext<CommandSourceStack> ctx, ConfigService.ValidationResult result, String label) {
        if (!result.valid()) {
            ctx.getSource().getSender().sendMessage(messages.text(ctx.getSource().getSender(), "error.invalid_config", String.join("; ", result.errors())));
            return 0;
        }
        chickens.resyncAllLoaded();
        displays.rebuildAllLoaded();
        ctx.getSource().getSender().sendMessage(messages.text(ctx.getSource().getSender(), "command.config_reset", label));
        return Command.SINGLE_SUCCESS;
    }

    private void sendInfo(CommandSender sender, Collection<Chicken> targets) {
        int total = targets.size();
        int index = 0;
        for (Chicken chicken : targets) {
            index++;
            if (index > 1) sender.sendMessage(Component.empty());

            WonderfulChickenData data = store.load(chicken);
            Component header = Component.text("◆ ", NamedTextColor.GOLD)
                    .append(Component.text(messages.text(sender, "command.info_header", index, total), NamedTextColor.YELLOW)
                            .decorate(TextDecoration.BOLD));
            sender.sendMessage(header);
            sender.sendMessage(infoLine(messages.text(sender, "command.info_uuid"), chicken.getUniqueId().toString(), NamedTextColor.GRAY));
            sender.sendMessage(infoLine(messages.text(sender, "command.info_bloodline"),
                    chickens.displayBloodlineId(data.bloodlineId()), NamedTextColor.AQUA));
            sender.sendMessage(infoLine(messages.text(sender, "command.info_generation"),
                    Integer.toString(data.generation()), NamedTextColor.AQUA));
            sender.sendMessage(infoLine(messages.text(sender, "command.info_behavior"),
                    messages.text(sender, "behavior." + data.behaviorMode().name().toLowerCase(Locale.ROOT)), NamedTextColor.GREEN));

            for (StatType stat : StatType.values()) {
                Rank rank = Rank.fromNormalized(data.normalized(stat));
                Component line = Component.text("  " + messages.text(sender, "stat." + stat.key()) + ": ", NamedTextColor.GRAY)
                        .append(Component.text(formatInfoValue(stat, data.value(stat)), NamedTextColor.WHITE))
                        .append(Component.text("  " + messages.text(sender, "command.info_normalized") + "="
                                + String.format(Locale.ROOT, "%.3f", data.normalized(stat)), NamedTextColor.DARK_GRAY))
                        .append(Component.text("  [" + messages.rank(sender, rank.key()) + "]", rankColor(rank))
                                .decorate(TextDecoration.BOLD));
                sender.sendMessage(line);
            }

            sender.sendMessage(infoLine(messages.text(sender, "command.info_current_stamina"),
                    String.format(Locale.ROOT, "%.2f / %.2f", data.currentStamina(), data.value(StatType.STAMINA)),
                    NamedTextColor.GREEN));
        }
    }

    private Component infoLine(String label, String value, NamedTextColor valueColor) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, valueColor));
    }

    private String formatInfoValue(StatType stat, double value) {
        return switch (stat) {
            case MAX_HEALTH -> String.format(Locale.ROOT, "%.0f HP", value);
            case SIZE -> String.format(Locale.ROOT, "%.2f m", value * 0.7);
            case GROUND_SPEED, AIR_SPEED, ASCENT_SPEED -> String.format(Locale.ROOT, "%.2f blocks/s", value);
            case JUMP_STRENGTH, STEP_HEIGHT -> String.format(Locale.ROOT, "%.2f blocks", value);
            case STAMINA_RECOVERY -> String.format(Locale.ROOT, "%.2f/s", value);
            default -> String.format(Locale.ROOT, "%.2f", value);
        };
    }

    private NamedTextColor rankColor(Rank rank) {
        return switch (rank) {
            case MISERABLE -> NamedTextColor.DARK_RED;
            case VERY_LOW -> NamedTextColor.RED;
            case LOW -> NamedTextColor.GOLD;
            case SLIGHTLY_LOW -> NamedTextColor.YELLOW;
            case COMMON -> NamedTextColor.WHITE;
            case SLIGHTLY_HIGH -> NamedTextColor.GREEN;
            case HIGH -> NamedTextColor.AQUA;
            case VERY_HIGH -> NamedTextColor.BLUE;
            case LEGENDARY -> NamedTextColor.LIGHT_PURPLE;
            case MYTHICAL -> NamedTextColor.DARK_PURPLE;
            case IMPOSSIBLE -> NamedTextColor.GOLD;
        };
    }

    private ParsedSummon parseSummon(CommandSourceStack source, String raw) {
        String rest = raw == null ? "" : raw.trim();
        Location location = source.getLocation().clone();
        int compoundIndex = CommandText.firstCompoundIndex(rest);
        String prefix = compoundIndex < 0 ? rest : rest.substring(0, compoundIndex).trim();
        String compoundsRaw = compoundIndex < 0 ? "" : rest.substring(compoundIndex);
        if (!prefix.isBlank()) {
            String[] coordinates = prefix.split("\\s+");
            if (coordinates.length != 3) throw new IllegalArgumentException("Position must contain exactly x y z");
            location = parsePosition(location, coordinates);
        }
        List<String> compounds = CommandText.extractTopLevelCompounds(compoundsRaw);
        if (compounds.size() > 2) throw new IllegalArgumentException("At most two compounds are accepted: <wwc-data> <nbt>");
        String wwc = null;
        String nbt = null;
        if (compounds.size() == 1) {
            Map<String, Object> candidate = new SnbtLikeParser(compounds.getFirst()).parseCompound();
            if (looksLikeWwcData(candidate)) wwc = compounds.getFirst();
            else nbt = compounds.getFirst();
        } else if (compounds.size() == 2) {
            wwc = compounds.get(0);
            nbt = compounds.get(1);
        }
        return new ParsedSummon(location, wwc, nbt);
    }

    private Location parsePosition(Location origin, String[] coords) {
        boolean local = coords[0].startsWith("^") || coords[1].startsWith("^") || coords[2].startsWith("^");
        if (local) {
            if (!(coords[0].startsWith("^") && coords[1].startsWith("^") && coords[2].startsWith("^"))) throw new IllegalArgumentException("Local coordinates must use ^ for all three axes");
            double left = localValue(coords[0]);
            double up = localValue(coords[1]);
            double forwardAmount = localValue(coords[2]);
            Vector forward = origin.getDirection().normalize();
            Vector worldUp = new Vector(0, 1, 0);
            Vector leftVec = worldUp.clone().crossProduct(forward).normalize();
            Vector upVec = forward.clone().crossProduct(leftVec).normalize();
            Vector offset = leftVec.multiply(left).add(upVec.multiply(up)).add(forward.multiply(forwardAmount));
            return origin.clone().add(offset);
        }
        return new Location(origin.getWorld(), coordinate(coords[0], origin.getX()), coordinate(coords[1], origin.getY()), coordinate(coords[2], origin.getZ()), origin.getYaw(), origin.getPitch());
    }

    private double coordinate(String token, double base) {
        if (token.startsWith("~")) return base + (token.length() == 1 ? 0.0 : Double.parseDouble(token.substring(1)));
        return Double.parseDouble(token);
    }

    private double localValue(String token) { return token.length() == 1 ? 0.0 : Double.parseDouble(token.substring(1)); }

    @SuppressWarnings("unchecked")
    private void applyWwcData(WonderfulChickenData data, String raw, Chicken chicken) {
        Map<String, Object> root = new SnbtLikeParser(raw).parseCompound();
        Object statsObj = root.get("stats");
        if (statsObj instanceof Map<?, ?> stats) {
            for (Map.Entry<?, ?> entry : stats.entrySet()) {
                String key = String.valueOf(entry.getKey());
                StatType stat = StatType.fromDataKey(key).orElseThrow(() -> new IllegalArgumentException("Unknown stat: " + key));
                double value = stat.canonicalizeValue(number(entry.getValue()));
                data.value(stat, value);
                data.normalized(stat, store.toNormalized(stat, value));
            }
        }
        if (root.containsKey("current_stamina")) data.currentStamina(Math.max(0.0, Math.min(data.value(StatType.STAMINA), number(root.get("current_stamina")))));
        Object equipmentObj = root.get("equipment");
        if (equipmentObj instanceof Map<?, ?> equipment) {
            if (equipment.containsKey("carpet")) {
                ItemStack item = ItemUtil.fromMaterial(String.valueOf(equipment.get("carpet")));
                if (!ItemUtil.isCarpet(item)) throw new IllegalArgumentException("equipment.carpet must be a carpet");
                data.carpet(item);
            }
            if (equipment.containsKey("shulker_box")) {
                ItemStack item = ItemUtil.fromMaterial(String.valueOf(equipment.get("shulker_box")));
                if (!ItemUtil.isShulkerBox(item)) throw new IllegalArgumentException("equipment.shulker_box must be a shulker box");
                data.shulkerBox(item);
            }
            if (equipment.containsKey("head")) {
                ItemStack item = ItemUtil.fromMaterial(String.valueOf(equipment.get("head")));
                if (!ItemUtil.isHeadEquippable(item)) throw new IllegalArgumentException("equipment.head must be head-equippable");
                data.headItem(item);
            }
        }
        Object behaviorObj = root.get("behavior");
        if (behaviorObj instanceof Map<?, ?> behavior) {
            if (behavior.containsKey("mode")) data.behaviorMode(BehaviorMode.parse(String.valueOf(behavior.get("mode"))));
            if (behavior.containsKey("follow_target")) data.followTarget(UUID.fromString(String.valueOf(behavior.get("follow_target"))));
        }
        if (root.containsKey("baby") && Boolean.parseBoolean(String.valueOf(root.get("baby")))) chicken.setBaby();
    }

    private boolean looksLikeWwcData(Map<String, Object> map) {
        return map.keySet().stream().anyMatch(key -> List.of("stats", "equipment", "behavior", "current_stamina", "baby").contains(key));
    }

    private double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }

    private Object coerce(String raw, Object defaultValue) {
        if (defaultValue instanceof Integer) return Integer.parseInt(raw);
        if (defaultValue instanceof Long) return Long.parseLong(raw);
        if (defaultValue instanceof Float) return Float.parseFloat(raw);
        if (defaultValue instanceof Double) return Double.parseDouble(raw);
        if (defaultValue instanceof Boolean) return Boolean.parseBoolean(raw);
        if (defaultValue instanceof List<?>) return List.of(raw.split(","));
        return raw;
    }

    private boolean hasConfigPermission(CommandSender sender, String child) {
        return sender.hasPermission("wwc.command.config") || sender.hasPermission("wwc.command.config." + child);
    }

    private record ParsedSummon(Location location, String wwcData, String nbt) {}
}