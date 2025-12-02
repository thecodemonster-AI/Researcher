package com.ender.commands;

import com.ender.item.ResearchScrollItem;
import com.ender.registry.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ender.researcher.ResearchProgressManager;
import com.ender.network.ModNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.ender.researcher.ResearchState;
import com.ender.researcher.ResearchManager;
import com.ender.config.ResearchEntry;

import java.util.UUID;
import java.util.Map;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.commands.arguments.item.ItemArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.arguments.item.ItemInput;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.world.entity.player.Player;

@Mod.EventBusSubscriber
public class AdminCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("research")
            .requires(source -> source.hasPermission(2))
             .then(Commands.literal("set")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(), builder))
                    .then(Commands.argument("index", IntegerArgumentType.integer())
                        .suggests((context, builder) -> {
                            try {
                                int size = com.ender.researcher.ResearchManager.getAll().size();
                                java.util.List<String> idxs = new java.util.ArrayList<>();
                                for (int i = 0; i < size; i++) idxs.add(String.valueOf(i));
                                return net.minecraft.commands.SharedSuggestionProvider.suggest(idxs, builder);
                            } catch (Exception ex) { return net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Collections.emptyList(), builder); }
                        })
                        .then(Commands.argument("state", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                try {
                                    java.util.List<String> names = new java.util.ArrayList<>();
                                    for (com.ender.researcher.ResearchState rs : com.ender.researcher.ResearchState.values()) names.add(rs.name());
                                    return net.minecraft.commands.SharedSuggestionProvider.suggest(names, builder);
                                } catch (Exception ex) { return net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Collections.emptyList(), builder); }
                            })
                            .executes(ctx -> {
                                String player = StringArgumentType.getString(ctx, "player");
                                int idx = IntegerArgumentType.getInteger(ctx, "index");
                                String state = StringArgumentType.getString(ctx, "state");
                                return setPlayerState(ctx.getSource(), player, idx, state);
                            })
                        )
                    )
                )
            )
            .then(Commands.literal("pending")
                .then(Commands.literal("list")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(), builder))
                        .executes(ctx -> {
                            String player = StringArgumentType.getString(ctx, "player");
                            return listPending(ctx.getSource(), player);
                        })
                    )
                )
                .then(Commands.literal("clear")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(), builder))
                        .then(Commands.argument("index", IntegerArgumentType.integer())
                            .suggests((context, builder) -> {
                                try {
                                    int size = com.ender.researcher.ResearchManager.getAll().size();
                                    java.util.List<String> idxs = new java.util.ArrayList<>();
                                    for (int i = 0; i < size; i++) idxs.add(String.valueOf(i));
                                    return net.minecraft.commands.SharedSuggestionProvider.suggest(idxs, builder);
                                } catch (Exception ex) { return net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Collections.emptyList(), builder); }
                            })
                            .executes(ctx -> {
                                String player = StringArgumentType.getString(ctx, "player");
                                int idx = IntegerArgumentType.getInteger(ctx, "index");
                                return clearPending(ctx.getSource(), player, idx);
                            })
                        )
                        .executes(ctx -> {
                            String player = StringArgumentType.getString(ctx, "player");
                            return clearPending(ctx.getSource(), player, -1);
                        })
                    )
                )
            )
             .then(Commands.literal("status")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(), builder))
                    .executes(ctx -> {
                        String player = StringArgumentType.getString(ctx, "player");
                        return listStatus(ctx.getSource(), player);
                    })
                )
             )
             .then(Commands.literal("reset")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(context.getSource().getServer().getPlayerNames(), builder))
                    .executes(ctx -> {
                        String player = StringArgumentType.getString(ctx, "player");
                        return resetPlayerProgress(ctx.getSource(), player);
                    })
                )
             )
             .then(Commands.literal("scroll")
                 .then(Commands.literal("set")
                     .then(Commands.argument("command", StringArgumentType.greedyString())
                         .executes(ctx -> setScrollCommand(ctx, StringArgumentType.getString(ctx, "command")))
                     )
                 )
                 .then(Commands.literal("clear")
                     .executes(ctx -> clearScrollCommand(ctx))
                 )
             );

        event.getDispatcher().register(root);
    }

    private static int setScrollCommand(CommandContext<CommandSourceStack> ctx, String command) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof Player player)) {
            src.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.RESEARCH_SCROLL.get())) {
            src.sendFailure(Component.literal("Hold a research scroll in your main hand."));
            return 0;
        }
        ResearchScrollItem.setCommand(held, command.trim());
        src.sendSuccess(() -> Component.literal("Bound scroll to /" + command), true);
        return 1;
    }

    private static int clearScrollCommand(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof Player player)) {
            src.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.RESEARCH_SCROLL.get())) {
            src.sendFailure(Component.literal("Hold a research scroll in your main hand."));
            return 0;
        }
        ResearchScrollItem.clearCommand(held);
        src.sendSuccess(() -> Component.literal("Cleared scroll command."), true);
        return 1;
    }

    private static int listPending(CommandSourceStack src, String playerName) {
        try {
            ServerPlayer sp = src.getServer().getPlayerList().getPlayerByName(playerName);
            if (sp == null) {
                src.sendFailure(Component.literal("Player not found: " + playerName));
                return 0;
            }
            UUID uuid = sp.getUUID();
            var map = ResearchProgressManager.getPendingForPlayer(uuid);
            if (map == null || map.isEmpty()) {
                src.sendSuccess(() -> Component.literal("No pending rewards for " + playerName), false);
                return 1;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Pending rewards for ").append(playerName).append(":\n");
            for (var e : map.entrySet()) {
                sb.append(" - idx=").append(e.getKey()).append(" -> ");
                List<ItemStack> stacks = e.getValue();
                if (stacks == null || stacks.isEmpty()) {
                    sb.append("<empty>\n");
                } else {
                    for (int i = 0; i < stacks.size(); i++) {
                        ItemStack stack = stacks.get(i);
                        if (stack == null || stack.isEmpty()) continue;
                        if (i > 0) sb.append(", ");
                        sb.append(stack.getHoverName().getString()).append(" x").append(stack.getCount());
                    }
                    sb.append("\n");
                }
            }
            src.sendSuccess(() -> Component.literal(sb.toString()), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error listing pending: " + e.getMessage()));
            return 0;
        }
    }

    private static int clearPending(CommandSourceStack src, String playerName, int idx) {
        try {
            ServerPlayer sp = src.getServer().getPlayerList().getPlayerByName(playerName);
            if (sp == null) {
                src.sendFailure(Component.literal("Player not found: " + playerName));
                return 0;
            }
            UUID uuid = sp.getUUID();
            if (idx >= 0) {
                // mark as COMPLETE_CLAIMED and clear pending reward
                ResearchProgressManager.setState(uuid, idx, ResearchState.COMPLETE_CLAIMED);
                if (sp.level() instanceof ServerLevel slevel) ResearchProgressManager.saveStateToLevel(slevel, uuid);
                ResearchProgressManager.clearPendingForPlayer(uuid, idx); // remove pending reward
                // notify player client if online (we already resolved sp above)
                if (sp != null) {
                    var openPos = com.ender.researcher.ResearchProgressManager.getOpenTablePos(sp.getUUID());
                    ModNetworking.sendResearchState(sp, openPos != null ? openPos : BlockPos.ZERO, idx, ResearchState.COMPLETE_CLAIMED.ordinal());
                }
                src.sendSuccess(() -> Component.literal("Cleared pending reward " + idx + " for " + playerName), true);
            } else {
                // Collect indices so we can update states & notify client
                var pending = ResearchProgressManager.getPendingForPlayer(uuid);
                java.util.List<Integer> indices = new java.util.ArrayList<>(pending.keySet());
                // mark all as COMPLETE_CLAIMED
                for (int k : indices) {
                    ResearchProgressManager.setState(uuid, k, ResearchState.COMPLETE_CLAIMED);
                    if (sp.level() instanceof ServerLevel slevel2) ResearchProgressManager.saveStateToLevel(slevel2, uuid);
                }
                ResearchProgressManager.clearPendingForPlayer(uuid);
                // notify client for each index if online (we already have sp)
                if (sp != null) {
                    var openPos = com.ender.researcher.ResearchProgressManager.getOpenTablePos(sp.getUUID());
                    BlockPos posToUse = openPos != null ? openPos : BlockPos.ZERO;
                    for (int k : indices) {
                        ModNetworking.sendResearchState(sp, posToUse, k, ResearchState.COMPLETE_CLAIMED.ordinal());
                    }
                }
                src.sendSuccess(() -> Component.literal("Cleared all pending rewards for " + playerName), true);
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error clearing pending: " + e.getMessage()));
            return 0;
        }
    }

    private static int listStatus(CommandSourceStack src, String playerName) {
        try {
            ServerPlayer sp = src.getServer().getPlayerList().getPlayerByName(playerName);
            if (sp == null) {
                src.sendFailure(Component.literal("Player not found or offline: " + playerName));
                return 0;
            }
            UUID uuid = sp.getUUID();
            // Get global states and research entries
            Map<Integer, com.ender.researcher.ResearchState> states = ResearchProgressManager.getStatesForPlayer(uuid);
            var entries = ResearchManager.getAll();
            StringBuilder sb = new StringBuilder();
            sb.append("Research status for ").append(playerName).append(":\n");
            for (int i = 0; i < entries.size(); i++) {
                ResearchEntry entry = entries.get(i);
                String title = entry != null ? (entry.title != null ? entry.title : entry.id) : ("#" + i);
                com.ender.researcher.ResearchState st = states != null ? states.getOrDefault(i, com.ender.researcher.ResearchState.NOT_STARTED) : com.ender.researcher.ResearchState.NOT_STARTED;
                var pending = ResearchProgressManager.getPendingForPlayer(uuid);
                String pendingText = "";
                if (pending != null) {
                    var ps = pending.get(i);
                    if (ps != null && !ps.isEmpty()) {
                        StringBuilder sb2 = new StringBuilder(" (pending: ");
                        for (int j = 0; j < ps.size(); j++) {
                            ItemStack stack = ps.get(j);
                            if (stack == null || stack.isEmpty()) continue;
                            if (j > 0) sb2.append(", ");
                            sb2.append(stack.getHoverName().getString()).append(" x").append(stack.getCount());
                        }
                        sb2.append(")");
                        pendingText = sb2.toString();
                    }
                }
                sb.append(i).append(": ").append(title).append(" - ").append(st.name()).append(pendingText).append("\n");
            }
            src.sendSuccess(() -> Component.literal(sb.toString()), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error listing status: " + e.getMessage()));
            return 0;
        }
    }

    // Admin helper to set a player's research state by name
    private static int setPlayerState(CommandSourceStack src, String playerName, int index, String stateName) {
        try {
            ServerPlayer sp = src.getServer().getPlayerList().getPlayerByName(playerName);
            if (sp == null) {
                src.sendFailure(Component.literal("Player not found: " + playerName));
                return 0;
            }
            UUID uuid = sp.getUUID();
            var entries = ResearchManager.getAll();
            if (index < 0 || index >= entries.size()) {
                src.sendFailure(Component.literal("Invalid research index: " + index));
                return 0;
            }
            ResearchState target;
            try {
                target = ResearchState.valueOf(stateName.toUpperCase());
            } catch (Exception e) {
                src.sendFailure(Component.literal("Invalid state name: " + stateName + " (valid: NOT_STARTED, IN_PROGRESS, COMPLETE_UNCLAIMED, COMPLETE_CLAIMED)"));
                return 0;
            }

            // Apply state change in memory
            ResearchProgressManager.setState(uuid, index, target);
            // Persist to world saved data if possible
            try {
                Level lvl = sp.level();
                if (lvl instanceof ServerLevel slevel) {
                    ResearchProgressManager.saveStateToLevel(slevel, uuid);
                }
            } catch (Exception ignored) {}

            // If state is COMPLETE_CLAIMED, clear any pending reward for that index
            if (target == ResearchState.COMPLETE_CLAIMED) {
                ResearchProgressManager.clearPendingForPlayer(uuid, index);
            }

            // Notify the player client if online and has an open table pos, otherwise use ZERO
            var openPos = ResearchProgressManager.getOpenTablePos(uuid);
            BlockPos posToUse = openPos != null ? openPos : BlockPos.ZERO;
            ModNetworking.sendResearchState(sp, posToUse, index, target.ordinal());

            src.sendSuccess(() -> Component.literal("Set research state for " + playerName + " index=" + index + " to " + target.name()), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error setting state: " + e.getMessage()));
            return 0;
        }
    }

    private static int resetPlayerProgress(CommandSourceStack src, String playerName) {
        try {
            ServerPlayer sp = src.getServer().getPlayerList().getPlayerByName(playerName);
            if (sp == null) {
                src.sendFailure(Component.literal("Player not found: " + playerName));
                return 0;
            }
            UUID uuid = sp.getUUID();
            // Get all research indices for this player
            var entries = ResearchManager.getAll();
            // Reset each one to NOT_STARTED
            for (int i = 0; i < entries.size(); i++) {
                ResearchProgressManager.setState(uuid, i, ResearchState.NOT_STARTED);
            }
            // Also clear any pending rewards
            ResearchProgressManager.clearPendingForPlayer(uuid);
            // Save the state to the world
            if (sp.level() instanceof ServerLevel slevel) {
                ResearchProgressManager.saveStateToLevel(slevel, uuid);
            }
            // Notify the player client if online
            var openPos = ResearchProgressManager.getOpenTablePos(uuid);
            BlockPos posToUse = openPos != null ? openPos : BlockPos.ZERO;
            for (int i = 0; i < entries.size(); i++) {
                ModNetworking.sendResearchState(sp, posToUse, i, ResearchState.NOT_STARTED.ordinal());
            }
            src.sendSuccess(() -> Component.literal("Reset research progress for " + playerName), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error resetting progress: " + e.getMessage()));
            return 0;
        }
    }
}