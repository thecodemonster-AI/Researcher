package com.ender.researcher;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.stats.Stats;
import javax.annotation.Nullable;

import com.ender.blockentity.ResearchTableBlockEntity;

public class ResearchTableBlock extends Block implements EntityBlock {

    // Build a shape that mirrors the model exported from Blockbench (legs + skirt + top slab)
    private static final VoxelShape SHAPE;

    static {
        // coordinates are in model space (0..16) -> convert to 0..1 by dividing by 16
        VoxelShape leg1 = Shapes.box(12.0D/16.0D, 0.0D/16.0D, 12.0D/16.0D, 15.0D/16.0D, 11.0D/16.0D, 15.0D/16.0D);
        VoxelShape leg2 = Shapes.box(1.0D/16.0D, 0.0D/16.0D, 12.0D/16.0D, 4.0D/16.0D, 11.0D/16.0D, 15.0D/16.0D);
        VoxelShape leg3 = Shapes.box(1.0D/16.0D, 0.0D/16.0D, 1.0D/16.0D, 4.0D/16.0D, 11.0D/16.0D, 4.0D/16.0D);
        VoxelShape leg4 = Shapes.box(12.0D/16.0D, 0.0D/16.0D, 1.0D/16.0D, 15.0D/16.0D, 11.0D/16.0D, 4.0D/16.0D);
        VoxelShape skirt = Shapes.box(1.5D/16.0D, 3.0D/16.0D, 1.5D/16.0D, 14.5D/16.0D, 11.0D/16.0D, 14.5D/16.0D);
        VoxelShape top = Shapes.box(0.0D/16.0D, 11.0D/16.0D, 0.0D/16.0D, 16.0D/16.0D, 14.0D/16.0D, 16.0D/16.0D);

        VoxelShape shape = Shapes.empty();
        shape = Shapes.or(shape, leg1);
        shape = Shapes.or(shape, leg2);
        shape = Shapes.or(shape, leg3);
        shape = Shapes.or(shape, leg4);
        shape = Shapes.or(shape, skirt);
        shape = Shapes.or(shape, top);
        SHAPE = shape;
    }

    public ResearchTableBlock(Properties props) {
        super(props);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchTableBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Let vanilla render the block model normally so placed block shows its model/texture
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        // Defer to vanilla logic so face culling is calculated correctly (don't always return true)
        return super.skipRendering(state, adjacentBlockState, side);
    }
    

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {

         if (level.isClientSide) {
             BlockEntity be = level.getBlockEntity(pos);
             if (be instanceof ResearchTableBlockEntity rtb) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ender.client.ClientHooks.openResearchTableScreen(rtb, player));
             }
         }
         return InteractionResult.SUCCESS;


    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (!level.isClientSide && blockEntity instanceof ResearchTableBlockEntity rtb) {
            if (shouldPersistResearch(rtb)) {
                rtb.persistResearchGlobally();
            }
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ResearchTableBlockEntity rtb && shouldPersistResearch(rtb)) {
                rtb.persistResearchGlobally();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private boolean shouldPersistResearch(ResearchTableBlockEntity blockEntity) {
        return blockEntity.isResearching() && !blockEntity.isFinished()
                && blockEntity.getActiveResearchIndex() >= 0 && blockEntity.getProgress() > 0;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ResearchTableBlockEntity rtb) {
                rtb.loadPersistedResearch();
            }
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, t) -> {
            if (t instanceof ResearchTableBlockEntity rtb) {
                ResearchTableBlockEntity.tick(lvl, pos, st, rtb);
            }
        };
    }
}