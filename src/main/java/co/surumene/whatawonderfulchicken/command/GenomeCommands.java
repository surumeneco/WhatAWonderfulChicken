package co.surumene.whatawonderfulchicken.command;

import co.surumene.whatawonderfulchicken.WhatAWonderfulChickenPlugin;
import co.surumene.whatawonderfulchicken.breeding.WonderfulChickenBreedingOutcome;
import co.surumene.whatawonderfulchicken.breeding.WonderfulChickenBreedingService;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.data.WonderfulChickenData;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import co.surumene.wgl.api.BreedingParentSource;
import co.surumene.wgl.api.DiploidGenome;
import co.surumene.wgl.api.GenomeEngine;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

public final class GenomeCommands {
    private final WhatAWonderfulChickenPlugin plugin;
    private final WonderfulChickenService chickens;
    private final WonderfulChickenStore store;
    private final DisplayService displays;
    private final GenomeCommandChickenFactory dataFactory;

    public GenomeCommands(
            WhatAWonderfulChickenPlugin plugin,
            WonderfulChickenService chickens,
            WonderfulChickenStore store,
            ConfigService config,
            DisplayService displays) {
        this.plugin=Objects.requireNonNull(plugin);
        this.chickens=Objects.requireNonNull(chickens);
        this.store=Objects.requireNonNull(store);
        this.displays=Objects.requireNonNull(displays);
        this.dataFactory=new GenomeCommandChickenFactory(store,config);
    }

    public LiteralArgumentBuilder<CommandSourceStack> genomeNode() {
        return Commands.literal("genome")
                .requires(source -> source.getSender().hasPermission("wwc.command.genome"))
                .then(Commands.literal("get")
                    .then(Commands.argument("targets",ArgumentTypes.entities())
                        .executes(this::get)));
    }

    public LiteralArgumentBuilder<CommandSourceStack> summonGenomeNode() {
        return Commands.literal("genome")
                .then(Commands.argument("format",StringArgumentType.word())
                    .suggests((context,builder)->{
                        builder.suggest("text");
                        builder.suggest("bits");
                        builder.suggest("hex");
                        builder.suggest("dna");
                        return builder.buildFuture();
                    })
                    .then(Commands.argument("haplotypes",StringArgumentType.greedyString())
                        .executes(this::summonGenome)));
    }

    public LiteralArgumentBuilder<CommandSourceStack> summonOffspringNode() {
        return Commands.literal("offspring")
                .then(Commands.literal("parent")
                    .then(Commands.argument("sourceA",StringArgumentType.word())
                        .then(Commands.literal("parent")
                            .then(Commands.argument("sourceB",StringArgumentType.word())
                                .executes(this::summonOffspring)))));
    }

    private int get(CommandContext<CommandSourceStack> context) {
        CommandSender sender=context.getSource().getSender();
        try {
            EntitySelectorArgumentResolver resolver=context.getArgument(
                    "targets",EntitySelectorArgumentResolver.class);
            List<Chicken> targets=resolver.resolve(context.getSource()).stream()
                    .filter(Chicken.class::isInstance)
                    .map(Chicken.class::cast)
                    .filter(store::isWonderful)
                    .toList();
            if(targets.isEmpty() || targets.size()>10) {
                error(sender,"指定範囲内のWonderful Chickenは1～10羽にしてください。");
                return 0;
            }
            GenomeEngine engine=plugin.genomeLib().engine();
            for(Chicken chicken:targets) {
                chickens.registerLoaded(chicken);
                WonderfulChickenData data=store.load(chicken);
                if(!data.hasGenomeModel()) {
                    error(sender,"個体にGenomeがありません: "+chicken.getUniqueId());
                    return 0;
                }
                var raw=GenomeAdminCodec.rawBits(data.genome());
                sender.sendMessage(Component.text("Genome "+chicken.getUniqueId(),
                        NamedTextColor.GOLD));
                sender.sendMessage(Component.text(
                        "染色体長 A/B: "+raw.chromosomeLengths(),NamedTextColor.GRAY));
                sendCopyable(sender,"A(bits)",raw.haplotypeA());
                sendCopyable(sender,"B(bits)",raw.haplotypeB());
                String source=GenomeParentSourceToken.encode(
                        engine,new BreedingParentSource.DiploidParent(data.genome()));
                sendCopyable(sender,"Parent source (WGLP)",source);
            }
            return targets.size();
        } catch(Exception exception) {
            error(sender,detail(exception));
            return 0;
        }
    }

    private int summonGenome(CommandContext<CommandSourceStack> context) {
        CommandSender sender=context.getSource().getSender();
        try {
            GenomeInputParser.Format format=GenomeInputParser.Format.parse(
                    StringArgumentType.getString(context,"format"));
            List<String> values=GenomeCommandArguments.parse(
                    StringArgumentType.getString(context,"haplotypes"));
            GenomeEngine engine=plugin.genomeLib().engine();
            DiploidGenome genome=GenomeAdminCodec.parseGenome(
                    format,values.get(0),
                    values.size()==2?values.get(1):null,
                    engine.sequenceCodec());
            WonderfulChickenData data=dataFactory.decoded(
                    engine,plugin.genomeProfile(),genome);
            return spawnPrepared(context,data);
        } catch(Exception exception) {
            error(sender,detail(exception));
            return 0;
        }
    }

    private int summonOffspring(CommandContext<CommandSourceStack> context) {
        CommandSender sender=context.getSource().getSender();
        try {
            GenomeEngine engine=plugin.genomeLib().engine();
            BreedingParentSource a=GenomeParentSourceToken.decode(
                    engine,StringArgumentType.getString(context,"sourceA"));
            BreedingParentSource b=GenomeParentSourceToken.decode(
                    engine,StringArgumentType.getString(context,"sourceB"));
            WonderfulChickenBreedingOutcome outcome=
                    new WonderfulChickenBreedingService(
                            ()->plugin.genomeLib().engine(),plugin::genomeProfile)
                            .breedSources(a,b,java.util.concurrent.ThreadLocalRandom.current().nextLong());
            if(outcome instanceof WonderfulChickenBreedingOutcome.Fallback fallback) {
                error(sender,"繁殖不可: "+fallback.detail());
                return 0;
            }
            var result=(WonderfulChickenBreedingOutcome.Success)outcome;
            return spawnPrepared(context,dataFactory.fromSnapshot(
                    result.genome(),result.phenotypeSnapshot()));
        } catch(Exception exception) {
            error(sender,detail(exception));
            return 0;
        }
    }

    private int spawnPrepared(
            CommandContext<CommandSourceStack> context,
            WonderfulChickenData data) {
        Location location=context.getSource().getLocation();
        if(location.getWorld()==null) {
            throw new IllegalArgumentException("召喚可能なワールドがありません。");
        }
        Chicken chicken=location.getWorld().spawn(location,Chicken.class);
        try {
            chickens.initialize(chicken,data);
            displays.rebuild(chicken);
        } catch(RuntimeException exception) {
            chicken.remove();
            throw exception;
        }
        context.getSource().getSender().sendMessage(
                Component.text("Wonderful Chickenを生成しました: "+
                    chicken.getUniqueId(),NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendCopyable(CommandSender sender,String label,String value) {
        if(sender instanceof Player) {
            sender.sendMessage(Component.text(label+" — クリックしてコピー ("+
                    value.length()+"文字)",NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.copyToClipboard(value)));
        } else {
            sender.sendMessage(label+": "+value);
        }
    }

    private static void error(CommandSender sender,String message) {
        sender.sendMessage(Component.text(message,NamedTextColor.RED));
    }

    private static String detail(Exception exception) {
        String message=exception.getMessage();
        return message==null||message.isBlank()
                ? exception.getClass().getSimpleName():message;
    }
}
