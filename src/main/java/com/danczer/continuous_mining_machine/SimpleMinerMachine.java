package com.danczer.continuous_mining_machine;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;

public class SimpleMinerMachine {
    private final static int MiningTimeShovel = 8;
    private final static int MiningTimePickAxe = 19;
    private final static int MiningCountZ = 3;
    private final static int TorchPlacementDistance = 6;

    private final World world;
    private final AbstractMinecartEntity minecartEntity;
    private final Block railType;
    private final Block torchType;

    private BlockPos lastTorchPos;
    private BlockPos lastMiningPos;
    private Direction lastMiningDir;

    private int miningTimerTick = 0;
    private int miningCountTick = 0;

    public boolean IsPathClear;

    public SimpleMinerMachine(AbstractMinecartEntity minecartEntity, Block railType, Block torchType) {
        this.minecartEntity = minecartEntity;
        this.world = minecartEntity.world;
        this.railType = railType;
        this.torchType = torchType;
    }

    public void readAdditional(CompoundNBT compound) {

        long miningPos = compound.getLong("lastMiningPos");

        if(miningPos == 0){
            lastMiningPos = null;
        }else{
            lastMiningPos = BlockPos.fromLong(miningPos);
        }

        long torchPos = compound.getLong("lastTorchPos");

        if(torchPos == 0){
            lastTorchPos = null;
        }else{
            lastTorchPos = BlockPos.fromLong(torchPos);
        }

        int dirIndex = compound.getInt("lastMiningDir");


        if(dirIndex == -1){
            lastMiningDir = null;
        }else{
             lastMiningDir = Direction.byIndex(dirIndex);
        }

        miningTimerTick = compound.getInt("miningTimerTick");
        miningCountTick = compound.getInt("miningCountTick");
    }

    public void writeAdditional(CompoundNBT compound) {
        compound.putLong("lastMiningPos", lastMiningPos == null ? 0 : lastMiningPos.toLong());
        compound.putLong("lastTorchPos", lastTorchPos == null ? 0 : lastTorchPos.toLong());
        compound.putInt("lastMiningDir", lastMiningDir == null ? -1 : lastMiningDir.getIndex());
        compound.putInt("miningTimerTick", miningTimerTick);
        compound.putInt("miningCountTick", miningCountTick);
    }

    public Vector3d getDirectoryVector() {
        if (lastMiningDir == null) return Vector3d.ZERO;

        switch (lastMiningDir) {
            case NORTH:
            case SOUTH:
            case WEST:
            case EAST:
                return new Vector3d(lastMiningDir.toVector3f()).normalize();
            case DOWN:
            case UP:
            default:
                return Vector3d.ZERO;
        }
    }

    public void tick() {
        BlockPos frontPos = getFrontPos();

        //stopped
        if (frontPos == null) {
            resetMining();
            IsPathClear = false;
        } else {
            IsPathClear = isFrontHarvested(frontPos);

            //nothing to do
            if (IsPathClear) {
                resetMining();
            } else {
                if (lastMiningPos == null) {
                    beginMining(frontPos);
                    miningCountTick = 0;
                } else {
                    if (tickMining()) {
                        if (miningCountTick == 0) {
                            createRail();
                        } else if (miningCountTick == 1) {
                            createTorch();
                        }

                        miningCountTick++;

                        if (miningCountTick <= MiningCountZ) {
                            beginMining(lastMiningPos.up());
                        } else {
                            resetMining();
                        }
                    }
                }
            }
        }
    }

    private BlockPos getFrontPos() {
        BlockPos pos = minecartEntity.getPosition();

        BlockPos foundFrontPos = getFrontPosFromMotion(pos);

        if (foundFrontPos == null) {
            foundFrontPos = getFrontPosFromSurroundingRails(pos);
        }

        return foundFrontPos;
    }


    private BlockPos getFrontPosFromSurroundingRails(BlockPos position) {
        if (isRailTrack(position.east())) {
            return position.west();
        } else if (isRailTrack(position.west())) {
            return position.east();
        } else if (isRailTrack(position.north())) {
            return position.south();
        } else if (isRailTrack(position.south())) {
            return position.north();
        }

        return null;
    }

    private BlockPos getFrontPosFromMotion(BlockPos position) {
        Direction dir = getMotionDirection();

        if (dir == null) {
            dir = lastMiningDir;
        }

        if (dir == null) return null;

        lastMiningDir = dir;

        Direction oppositeDir = dir.getOpposite();

        if (isRailTrack(position.offset(oppositeDir))) {
            return position.offset(dir);
        }

        return null;
    }

    private Direction getMotionDirection() {
        Vector3d motion = minecartEntity.getMotion();

        if (motion.lengthSquared() > 0.01) {
            return Direction.getFacingFromVector(motion.x, motion.y, motion.z);
        }

        return null;
    }

    private boolean isRailTrack(BlockPos targetPos) {
        return world.getBlockState(targetPos).isIn(BlockTags.RAILS);
    }

    private boolean isFrontHarvested(BlockPos pos) {
        for (int i = 0; i < MiningCountZ; i++) {
            if (!isBlockHarvested(pos)) return false;

            pos = pos.up();
        }

        return true;
    }

    private boolean isBlockHarvested(BlockPos blockPos) {
        BlockState blockState = world.getBlockState(blockPos);

        return blockState.isAir() || blockState.isIn(BlockTags.RAILS) || blockState.isIn(Blocks.WALL_TORCH) || blockState.isIn(Blocks.REDSTONE_WALL_TORCH);
    }

    private void beginMining(BlockPos blockPos) {
        lastMiningPos = blockPos;
        miningTimerTick = 0;
    }

    private void resetMining() {
        lastMiningPos = null;
        miningTimerTick = 0;
        miningCountTick = 0;
    }

    private boolean tickMining() {
        if (isBlockHarvested(lastMiningPos)) return true;

        BlockState blockState = world.getBlockState(lastMiningPos);

        boolean isPickAxe =blockState.isToolEffective(ToolType.PICKAXE);
        boolean isShovel = blockState.isToolEffective(ToolType.SHOVEL);

        int miningTime = -1;

        if (isPickAxe || isShovel) {
            miningTimerTick++;

            if (isPickAxe) {
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
                miningTime = MiningTimePickAxe;
            } else if (isShovel) {
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
                miningTime = MiningTimeShovel;
            }

            if (miningTimerTick > miningTime) {
                world.destroyBlock(lastMiningPos, true);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void createRail() {
        if (isRailTrack(lastMiningPos)) return;

        world.setBlockState(lastMiningPos, railType.getDefaultState().rotate(world, lastMiningPos, getRailRotation())); //lava has 3 for water
    }

    private Rotation getRailRotation() {
        return Rotation.NONE;
    }

    private void createTorch() {
        if (world.getBlockState(lastMiningPos).isIn(Blocks.TORCH)) return;
        if (lastTorchPos != null && lastTorchPos.withinDistance(new Vector3d(lastMiningPos.getX(), lastMiningPos.getY(), lastMiningPos.getZ()), TorchPlacementDistance))
            return;
        if (lastMiningDir == null) return;

        world.setBlockState(lastMiningPos,torchType.getDefaultState().rotate(world, lastMiningPos, getTorchRotation()));

        lastTorchPos = lastMiningPos;
    }

    private Rotation getTorchRotation() {
        switch (lastMiningDir) {
            case NORTH:
                return Rotation.CLOCKWISE_90;
            case SOUTH:
                return Rotation.COUNTERCLOCKWISE_90;
            case WEST:
                return Rotation.NONE;
            case EAST:
                return Rotation.CLOCKWISE_180;
            case DOWN:
            case UP:
            default:
                return Rotation.NONE;
        }
    }
}
