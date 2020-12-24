package com.danczer.excavator;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.RailShape;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MinerLogic {

    private static final Logger LOGGER = LogManager.getLogger();

    private final static int MiningTimeShovel = 8;
    private final static int MiningTimePickAxe = 19;
    private final static int MiningCountZ = 3;
    private final static int TorchPlacementDistance = 6;

    private final World world;
    private final AbstractMinecartEntity minecartEntity;
    private final Block railType;
    private final Block torchType;

    private BlockPos lastTorchPos;
    private BlockPos miningPos;
    private Direction miningDir;

    private int miningTimerTick = 0;
    private int miningCountTick = 0;
    private int minedBlockCount = 0;

    public boolean IsPathClear;

    public MinerLogic(AbstractMinecartEntity minecartEntity, Block railType, Block torchType) {
        this.minecartEntity = minecartEntity;
        this.world = minecartEntity.world;
        this.railType = railType;
        this.torchType = torchType;
    }

    public int getMinedBlockCount(){
        return minedBlockCount;
    }

    public void readAdditional(CompoundNBT compound) {

        long miningPos = compound.getLong("lastMiningPos");

        if(miningPos == 0){
            this.miningPos = null;
        }else{
            this.miningPos = BlockPos.fromLong(miningPos);
        }

        long torchPos = compound.getLong("lastTorchPos");

        if(torchPos == 0){
            lastTorchPos = null;
        }else{
            lastTorchPos = BlockPos.fromLong(torchPos);
        }

        int dirIndex = compound.getInt("lastMiningDir");

        if(dirIndex == -1){
            miningDir = null;
        }else{
             miningDir = Direction.byIndex(dirIndex);
        }

        miningTimerTick = compound.getInt("miningTimerTick");
        miningCountTick = compound.getInt("miningCountTick");
    }

    public void writeAdditional(CompoundNBT compound) {
        compound.putLong("lastMiningPos", miningPos == null ? 0 : miningPos.toLong());
        compound.putLong("lastTorchPos", lastTorchPos == null ? 0 : lastTorchPos.toLong());
        compound.putInt("lastMiningDir", miningDir == null ? -1 : miningDir.getIndex());
        compound.putInt("miningTimerTick", miningTimerTick);
        compound.putInt("miningCountTick", miningCountTick);
    }

    public Vector3d getDirectoryVector() {
        if (miningDir == null) return Vector3d.ZERO;

        switch (miningDir) {
            case NORTH:
            case SOUTH:
            case WEST:
            case EAST:
                return new Vector3d(miningDir.toVector3f()).normalize();
            case DOWN:
            case UP:
            default:
                return Vector3d.ZERO;
        }
    }

    public boolean tick() {
        BlockPos frontPos = getMiningPlace();

        LOGGER.debug("FrontPos: "+frontPos);

        //stopped
        if (frontPos == null) {
            resetMining();
            return false;
        } else {
            IsPathClear = isFrontHarvested(frontPos);

            LOGGER.debug("IsPathClear: "+IsPathClear);
            //nothing to do
            if (IsPathClear) {
                resetMining();
            } else {
                if (miningPos == null) {
                    beginMining(frontPos);
                    miningCountTick = 0;

                    LOGGER.debug("beginMining");
                } else {
                    if (tickMining()) {
                        if (miningCountTick == 0) {
                            createRail();
                        } else if (miningCountTick == 1) {
                            createTorch();
                        }

                        miningCountTick++;

                        if (miningCountTick <= MiningCountZ) {
                            beginMining(miningPos.up());
                        } else {
                            resetMining();
                        }
                    }
                }
            }
            return true;
        }
    }

    private BlockPos getMiningPlace() {
        BlockPos pos = minecartEntity.getPosition();

        LOGGER.debug("minecartEntity pos: "+pos);

        if(!isRailTrack(pos)) return null;

        Vector3d motion =  minecartEntity.getMotion();

        LOGGER.debug("getAdjustedHorizontalFacing getMotion: "+motion);

        Direction dir;

        if(motion.lengthSquared() <= 0.01d){
            dir = miningDir;
        }else{
            dir = Direction.getFacingFromVector(motion.x, motion.y, motion.z);
            miningDir = dir;
        }

        LOGGER.debug("minecartEntity dir: "+dir);

        if(dir == null) return null;

        BlockState bs = world.getBlockState(pos);
        AbstractRailBlock railBlock = (AbstractRailBlock)bs.getBlock();

        RailShape railShape = railBlock.getRailDirection(bs, world, pos, minecartEntity);

        //fix detection on turns
        if(dir == Direction.NORTH || dir == Direction.SOUTH) {
            if (railShape == RailShape.NORTH_WEST || railShape == RailShape.SOUTH_WEST){
                lastTorchPos = null;
                dir = Direction.WEST;
            }
            if(railShape == RailShape.NORTH_EAST || railShape == RailShape.SOUTH_EAST){
                lastTorchPos = null;
                dir = Direction.EAST;
            }
        }else if(dir == Direction.WEST || dir == Direction.EAST){
            if(railShape == RailShape.NORTH_WEST || railShape == RailShape.NORTH_EAST){
                lastTorchPos = null;
                dir = Direction.NORTH;
            }
            if(railShape == RailShape.SOUTH_WEST || railShape == RailShape.SOUTH_EAST) {
                lastTorchPos = null;
                dir = Direction.SOUTH;
            }
        }

        return pos.offset(dir);
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

        return blockState.getCollisionShape(world, blockPos).isEmpty() || blockState.isIn(BlockTags.RAILS);
    }

    private void beginMining(BlockPos blockPos) {
        miningPos = blockPos;
        miningTimerTick = 0;
    }

    private void resetMining() {
        miningPos = null;
        miningTimerTick = 0;
        miningCountTick = 0;
    }

    private boolean tickMining() {
        if (isBlockHarvested(miningPos)) return true;

        LOGGER.debug("tickMining");

        BlockState blockState = world.getBlockState(miningPos);

        boolean isPickAxe =blockState.isToolEffective(ToolType.PICKAXE);
        boolean isShovel = blockState.isToolEffective(ToolType.SHOVEL);

        int miningTime = -1;

        if (isPickAxe || isShovel) {
            miningTimerTick++;
            LOGGER.debug("isPickAxe:"+isPickAxe+", isShovel:"+isShovel+", miningTimerTick:"+miningTimerTick);

            if (isPickAxe) {
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
                miningTime = MiningTimePickAxe;
            } else {
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
                miningTime = MiningTimeShovel;
            }

            if (miningTimerTick > miningTime) {
                world.destroyBlock(miningPos, true);
                minedBlockCount++;
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void createRail() {
        if (isRailTrack(miningPos)) return;

        LOGGER.debug("createRail");

        world.setBlockState(miningPos, railType.getDefaultState().rotate(world, miningPos, getRailRotation())); //lava has 3 for water
    }

    private Rotation getRailRotation() {
        return Rotation.NONE;
    }

    private void createTorch() {
        if (world.getBlockState(miningPos).isIn(Blocks.TORCH)) return;
        if (lastTorchPos != null && lastTorchPos.withinDistance(new Vector3d(miningPos.getX(), miningPos.getY(), miningPos.getZ()), TorchPlacementDistance))
            return;
        if (miningDir == null) return;

        LOGGER.debug("createTorch");

        world.setBlockState(miningPos,torchType.getDefaultState().rotate(world, miningPos, getTorchRotation()));

        lastTorchPos = miningPos;
    }

    private Rotation getTorchRotation() {
        switch (miningDir) {
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
