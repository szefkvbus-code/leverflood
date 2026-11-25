package com.example.leverflood;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Right-clicking a lever while holding a block:
 *   1. floods the lever's position with water (this destroys the lever),
 *   2. removes the water again,
 *   3. places the block you were holding there.
 * Everything happens synchronously inside one server tick.
 */
public class LeverFlood implements ModInitializer {

    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player.isSpectator()) return ActionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            BlockState original = world.getBlockState(pos);
            if (!original.isOf(Blocks.LEVER)) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof BlockItem blockItem)) return ActionResult.PASS;

            // Client: just swallow the click so the lever doesn't visually toggle.
            // The server does the real work and syncs the result back.
            if (world.isClient) return ActionResult.SUCCESS;

            if (!player.canModifyBlocks() || !world.canPlayerModifyAt(player, pos)) {
                return ActionResult.FAIL;
            }

            // 1) Flood: water replaces the lever
            world.setBlockState(pos, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);

            // 2) Drain: remove the water
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

            // 3) Place the intended block (position is now air, so it goes in the lever's spot)
            ItemPlacementContext context = new ItemPlacementContext(player, hand, stack, hit);
            ActionResult result = blockItem.place(context);

            if (result.isAccepted()) {
                // Water destroying a lever drops it as an item; mimic that.
                Block.dropStacks(original, world, pos, null);
                return ActionResult.SUCCESS;
            }

            // Placement failed (e.g. entity in the way): put the lever back untouched.
            world.setBlockState(pos, original, Block.NOTIFY_ALL);
            return ActionResult.FAIL;
        });
    }
}
