package com.danczer.excavator;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.RailShape;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
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

public class ExcavatorMinecartLogic {

    public enum MiningStatus {
        Rolling(0), MiningInProgress(1), HazardCliff(2), HazardLava(3), HazardWater(4), HazardUnknownFluid(5), DepletedConsumable(6);

        public final int Value;

        MiningStatus(int value) {
            Value = value;
        }

        public static MiningStatus Find(int value) {
            switch (value) {
                case 1:
                    return MiningStatus.MiningInProgress;
                case 2:
                    return MiningStatus.HazardCliff;
                case 3:
                    return MiningStatus.HazardLava;
                case 4:
                    return MiningStatus.HazardWater;
                case 5:
                    return MiningStatus.HazardUnknownFluid;
                case 6:
                    return MiningStatus.DepletedConsumable;
                default:
                    return MiningStatus.Rolling;
            }
        }
    }

    private static final Logger LOGGER = LogManager.getLogger();

    private final static int MiningTimeShovel = 8;
    private final static int MiningTimePickAxe = 19;
    public final static int MiningCountZ = 3;
    private final static int TorchPlacementDistance = 6;

    private final World world;
    private final AbstractMinecartEntity minecartEntity;

    private BlockItem railTypeItem;
    private BlockItem torchTypeItem;

    private Block railTypeBlock;
    private Block torchTypeBlock;

    private BlockPos lastTorchPos;
    private BlockPos miningPos;
    private Direction miningDir;

    private int miningTimerTick = 0;
    private int miningCountTick = 0;
    private int minedBlockCount = 0;
    private int previousProgress = 0;

    private int placedTrackCount = 0;
    private int placedTorchCount = 0;
    private boolean isMinecartTurning;

    public MiningStatus pathMiningStatus = MiningStatus.Rolling;

    public ExcavatorMinecartLogic(AbstractMinecartEntity minecartEntity) {
        this.minecartEntity = minecartEntity;
        this.world = minecartEntity.world;
    }

    public void setRailTypeItem(BlockItem item)
    {
        railTypeItem = item;
        if(item != null){
            railTypeBlock = item.getBlock();
        }else{
            railTypeBlock = null;
        }

        LOGGER.debug("railTypeItem: " + railTypeItem);
        LOGGER.debug("railTypeBlock: " + railTypeBlock);

    }

    public Item getRailTypeItem(){
        return railTypeItem;
    }

    public void setTorchTypeItem(BlockItem item)
    {
        torchTypeItem = item;
        if(item != null){
            torchTypeBlock = item.getBlock();
        }else{
            torchTypeBlock = null;
        }

        LOGGER.debug("railTypeItem: " + torchTypeItem);
        LOGGER.debug("railTypeBlock: " + torchTypeBlock);
    }

    public Item getTorchTypeItem(){
        return torchTypeItem;
    }


    public int getMinedBlockCount() {
        return minedBlockCount;
    }

    public int getPlacedTrackCount() {
        return placedTrackCount;
    }

    public int getPlacedTorchCount() {
        return placedTorchCount;
    }

    public void readAdditional(CompoundNBT compound) {

        long miningPos = compound.getLong("miningPos");

        if (miningPos == 0) {
            this.miningPos = null;
        } else {
            this.miningPos = BlockPos.fromLong(miningPos);
        }

        long torchPos = compound.getLong("lastTorchPos");

        if (torchPos == 0) {
            lastTorchPos = null;
        } else {
            lastTorchPos = BlockPos.fromLong(torchPos);
        }

        int dirIndex = compound.getInt("miningDir");

        if (dirIndex == 0) {
            miningDir = null;
        } else {
            miningDir = Direction.byIndex(dirIndex);
        }

        miningTimerTick = compound.getInt("miningTimerTick");
        miningCountTick = compound.getInt("miningCountTick");
        minedBlockCount = compound.getInt("minedBlockCount");
    }

    public void writeAdditional(CompoundNBT compound) {
        compound.putLong("miningPos", miningPos == null ? 0 : miningPos.toLong());
        compound.putLong("lastTorchPos", lastTorchPos == null ? 0 : lastTorchPos.toLong());
        compound.putInt("miningDir", miningDir == null ? 0 : miningDir.getIndex());
        compound.putInt("miningTimerTick", miningTimerTick);
        compound.putInt("miningCountTick", miningCountTick);
        compound.putInt("minedBlockCount", minedBlockCount);
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

    public void tick() {
        if(railTypeBlock == null || torchTypeBlock == null){
            resetMining();
            return;
        }

        BlockPos frontPos = getMiningPlace();

        LOGGER.debug("FrontPos: " + frontPos);

        //stopped
        if (frontPos == null) {
            resetMining();
        } else {
            boolean isPathClear = isFrontHarvested(frontPos);

            LOGGER.debug("IsPathClear: " + isPathClear);
            //nothing to do
            if (isPathClear) {
                resetMining();
            } else {
                pathMiningStatus = getFrontHazard(frontPos);

                if (pathMiningStatus == MiningStatus.MiningInProgress) {
                    if (miningPos == null) {
                        beginMining(frontPos);
                        miningCountTick = 0;

                        LOGGER.debug("beginMining");
                    } else {
                        if (tickMining()) {
                            if (miningCountTick == 0) {
                                createRail();
                            } else if (miningCountTick == 2) {
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
            }
        }
    }

    private BlockPos getMiningPlace() {
        BlockPos pos = minecartEntity.getPosition();

        LOGGER.debug("minecartEntity pos: " + pos);

        if (!isRailTrack(pos)) return null;

        Vector3d motion = minecartEntity.getMotion();

        LOGGER.debug("minecartEntity getMotion: " + motion);

        Direction dir;

        if (motion.lengthSquared() <= 0.0001d) {
            dir = miningDir;
        } else {
            dir = Direction.getFacingFromVector(motion.x, motion.y, motion.z);
        }
        miningDir = dir;

        LOGGER.debug("minecartEntity dir: " + dir);

        if (dir == null) return null;

        BlockState bs = world.getBlockState(pos);
        AbstractRailBlock railBlock = (AbstractRailBlock) bs.getBlock();

        RailShape railShape = railBlock.getRailDirection(bs, world, pos, minecartEntity);

        LOGGER.debug("minecartEntity railShape: " + railShape);
        isMinecartTurning = false;

        //fix detection on turns
        if (dir == Direction.NORTH || dir == Direction.SOUTH) {
            if (railShape == RailShape.NORTH_WEST || railShape == RailShape.SOUTH_WEST) {
                lastTorchPos = null;
                dir = Direction.WEST;
                isMinecartTurning = true;
            }
            if (railShape == RailShape.NORTH_EAST || railShape == RailShape.SOUTH_EAST) {
                lastTorchPos = null;
                dir = Direction.EAST;
                isMinecartTurning = true;
            }
        } else if (dir == Direction.WEST || dir == Direction.EAST) {
            if (railShape == RailShape.NORTH_WEST || railShape == RailShape.NORTH_EAST) {
                lastTorchPos = null;
                dir = Direction.NORTH;
                isMinecartTurning = true;
            }
            if (railShape == RailShape.SOUTH_WEST || railShape == RailShape.SOUTH_EAST) {
                lastTorchPos = null;
                dir = Direction.SOUTH;
                isMinecartTurning = true;
            }
        }

        LOGGER.debug("minecartEntity isMinecartTurning: " + isMinecartTurning);
        LOGGER.debug("minecartEntity adjusted dir: " + dir);

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

    private MiningStatus getFrontHazard(BlockPos pos) {
        BlockPos frontDown = pos.down();
        BlockPos behindFrontDown = pos.down().offset(miningDir);

        MiningStatus miningStatus;

        //front bottom
        if (isAir(frontDown)) return MiningStatus.HazardCliff;
        if (isAir(behindFrontDown)) return MiningStatus.HazardCliff;

        if ((miningStatus = getHazard(frontDown)) != MiningStatus.MiningInProgress) return miningStatus;
        if ((miningStatus = getHazard(behindFrontDown)) != MiningStatus.MiningInProgress) return miningStatus;

        //behind front bottom
        if ((miningStatus = getHazard(pos.offset(miningDir).down())) != MiningStatus.MiningInProgress) return miningStatus;

        //front top
        if ((miningStatus = getHazard(pos.up(MiningCountZ))) != MiningStatus.MiningInProgress) return miningStatus;

        //behind the Front
        if ((miningStatus = getStackHazardous(pos.offset(miningDir))) != MiningStatus.MiningInProgress) return miningStatus;

        //front sides
        if ((miningStatus = getStackHazardous(pos.offset(miningDir.rotateY()))) != MiningStatus.MiningInProgress) return miningStatus;
        if ((miningStatus = getStackHazardous(pos.offset(miningDir.rotateYCCW()))) != MiningStatus.MiningInProgress) return miningStatus;

        return MiningStatus.MiningInProgress;
    }

    private MiningStatus getStackHazardous(BlockPos pos) {
        for (int i = 0; i < MiningCountZ; i++) {
            MiningStatus miningStatus = getHazard(pos);
            if (miningStatus != MiningStatus.MiningInProgress) return miningStatus;
            pos = pos.up();
        }

        return MiningStatus.MiningInProgress;
    }

    private boolean isAir(BlockPos pos) {
        return world.getBlockState(pos).isAir();
    }

    private MiningStatus getHazard(BlockPos pos) {
        FluidState fLuidState = world.getBlockState(pos).getFluidState();

        if (!fLuidState.isEmpty()) {
            if (fLuidState.isTagged(FluidTags.LAVA)) {
                return MiningStatus.HazardLava;
            } else if (fLuidState.isTagged(FluidTags.WATER)) {
                return MiningStatus.HazardWater;
            } else {
                return MiningStatus.HazardUnknownFluid;
            }
        } else {
            return MiningStatus.MiningInProgress;
        }
    }

    private void beginMining(BlockPos blockPos) {
        if (blockPos != null) {
            world.sendBlockBreakProgress(0, blockPos, -1);
        }
        pathMiningStatus = MiningStatus.Rolling;
        miningPos = blockPos;
        miningTimerTick = 0;
    }

    private void resetMining() {
        if (miningPos != null) {
            world.sendBlockBreakProgress(0, miningPos, -1);
        }
        pathMiningStatus = MiningStatus.Rolling;
        miningPos = null;
        miningTimerTick = 0;
        miningCountTick = 0;
    }

    private boolean tickMining() {
        if (isBlockHarvested(miningPos)) return true;

        BlockState blockState = world.getBlockState(miningPos);

        LOGGER.debug("tickMining on " + blockState.getBlock().getRegistryName() + " at " + miningPos + ", getRequiresTool: " + blockState.getRequiresTool() + " ,getHarvestTool: " + (blockState.getHarvestTool() != null ? blockState.getHarvestTool().getName() : "nothing") + ", getBlockHardness: " + blockState.getBlockHardness(world, miningPos));

        boolean isPickAxe = (blockState.getHarvestTool() == ToolType.PICKAXE && blockState.getBlockHardness(world, miningPos) <= 10f) || blockState.getBlock() == Blocks.NETHER_QUARTZ_ORE;
        boolean isShovel = blockState.getHarvestTool() == ToolType.SHOVEL && blockState.getBlockHardness(world, miningPos) <= 10f;

        int miningTime = -1;

        if (isPickAxe || isShovel) {
            miningTimerTick++;
            LOGGER.debug("isPickAxe:" + isPickAxe + ", isShovel:" + isShovel + ", miningTimerTick:" + miningTimerTick);

            if (isPickAxe) {
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
                miningTime = MiningTimePickAxe;
            } else {
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
                miningTime = MiningTimeShovel;
            }

            int progress = (int) ((float) miningTimerTick / miningTime * 10.0F);
            if (progress != previousProgress) {
                world.sendBlockBreakProgress(0, miningPos, progress);
                previousProgress = progress;
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

        world.setBlockState(miningPos, railTypeBlock.getDefaultState().rotate(world, miningPos, getRailRotation()));
        placedTrackCount++;
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

        world.setBlockState(miningPos, torchTypeBlock.getDefaultState().rotate(world, miningPos, getTorchRotation()));
        placedTorchCount++;

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
