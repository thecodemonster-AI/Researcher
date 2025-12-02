package com.ender.item;

import com.ender.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class ResearchScrollItem extends Item {
    public static final String COMMAND_TAG = "ScrollCommand";

    public ResearchScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(COMMAND_TAG) || tag.getString(COMMAND_TAG).isBlank()) {
            player.displayClientMessage(Component.literal("This scroll has no command bound.").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        String command = tag.getString(COMMAND_TAG);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        int result = serverPlayer.getServer().getCommands().performPrefixedCommand(
                serverPlayer.createCommandSourceStack(),
                command.startsWith("/") ? command.substring(1) : command
        );

        if (result < 0) {
            player.displayClientMessage(Component.literal("Failed to execute scroll command.").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        stack.shrink(1);
        ItemStack parchment = new ItemStack(ModItems.PARCHMENT.get());
        if (stack.isEmpty()) {
            player.setItemInHand(hand, parchment);
        } else if (!player.addItem(parchment)) {
            player.drop(parchment, false);
        }

        player.displayClientMessage(Component.literal("The scroll crumbles into parchment.").withStyle(ChatFormatting.GRAY), true);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(COMMAND_TAG) && !tag.getString(COMMAND_TAG).isBlank()) {
            tooltip.add(Component.literal("Command: /" + tag.getString(COMMAND_TAG)).withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    public static void setCommand(ItemStack stack, String command) {
        stack.getOrCreateTag().putString(COMMAND_TAG, command);
    }

    public static void clearCommand(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) tag.remove(COMMAND_TAG);
    }
}
