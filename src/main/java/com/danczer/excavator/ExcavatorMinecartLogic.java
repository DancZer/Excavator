package com.danczer.excavator;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.fluid.FluidState;
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

    public enum Hazard{
        Unknown(0), None(1), Cliff(2), Lava(3), Water(4), UnknownFluid(5), MissingFuel(6);

        public final int Value;

        Hazard(int value) {
            Value = value;
        }

        public static Hazard Find(int value){
            switch (value){
                case 1: return Hazard.None;
                case 2: return Hazard.Cliff;
                case 3: return Hazard.Lava;
                case 4: return Hazard.Water;
                case 5: return Hazard.UnknownFluid;
                case 6: return Hazard.MissingFuel;
                default:
                    return Hazard.Unknown;
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
    public final Block railType;
    public final Block torchType;

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

    public boolean IsPathClear;
    public Hazard PathHazard = Hazard.Unknown;

    public ExcavatorMinecartLogic(AbstractMinecartEntity minecartEntity, Block railType, Block torchType) {
        this.minecartEntity = minecartEntity;
        this.world = minecartEntity.world;
        this.railType = railType;
        this.torchType = torchType;
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

    public boolean tick() {
        BlockPos frontPos = getMiningPlace();

        LOGGER.debug("FrontPos: " + frontPos);

        //stopped
        if (frontPos == null) {
            resetMining();
            return false;
        } else {
            IsPathClear = isFrontHarvested(frontPos);

            LOGGER.debug("IsPathClear: " + IsPathClear);
            //nothing to do
            if (IsPathClear) {
                resetMining();
            } else {
                PathHazard = getFrontHazard(frontPos);

                if(PathHazard == Hazard.None) {
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
            }

            return true;
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

    private Hazard getFrontHazard(BlockPos pos){
        BlockPos frontDown = pos.down();
        BlockPos behindFrontDown = pos.down().offset(miningDir);

        Hazard hazard;

        //front bottom
        if(isAir(frontDown)) return Hazard.Cliff;
        if(isAir(behindFrontDown)) return Hazard.Cliff;

        if((hazard = getHazard(frontDown)) != Hazard.None) return hazard;
        if((hazard = getHazard(behindFrontDown)) != Hazard.None) return hazard;

        //behind front bottom
        if((hazard = getHazard(pos.offset(miningDir).down())) != Hazard.None) return hazard;

        //front top
        if((hazard = getHazard(pos.up(MiningCountZ))) != Hazard.None) return hazard;

        //behind the Front
        if((hazard = getStackHazardous(pos.offset(miningDir))) != Hazard.None)  return hazard;

        //front sides
        if((hazard = getStackHazardous(pos.offset(miningDir.rotateY()))) != Hazard.None)  return hazard;
        if((hazard = getStackHazardous(pos.offset(miningDir.rotateYCCW()))) != Hazard.None)  return hazard;

        return Hazard.None;
    }

    private Hazard getStackHazardous(BlockPos pos){
        for (int i = 0; i < MiningCountZ; i++) {
            Hazard hazard = getHazard(pos);
            if(hazard != Hazard.None) return hazard;
            pos = pos.up();
        }

        return Hazard.None;
    }

    private boolean isAir(BlockPos pos){
        return world.getBlockState(pos).isAir();
    }

    private Hazard getHazard(BlockPos pos){
        FluidState fLuidState = world.getBlockState(pos).getFluidState();

        if(!fLuidState.isEmpty()){
            if(fLuidState.isTagged(FluidTags.LAVA)) {
                return Hazard.Lava;
            }else if(fLuidState.isTagged(FluidTags.WATER)) {
                return Hazard.Water;
            }else{
                return Hazard.UnknownFluid;
            }
        }else{
            return Hazard.None;
        }
    }

    private void beginMining(BlockPos blockPos) {
        if(blockPos != null) {
            world.sendBlockBreakProgress(0, blockPos, -1);
        }
        PathHazard = Hazard.Unknown;
        miningPos = blockPos;
        miningTimerTick = 0;
    }

    private void resetMining() {
        if(miningPos != null) {
            world.sendBlockBreakProgress(0, miningPos, -1);
        }
        PathHazard = Hazard.Unknown;
        miningPos = null;
        miningTimerTick = 0;
        miningCountTick = 0;
    }

    private boolean tickMining() {
        if (isBlockHarvested(miningPos)) return true;

        LOGGER.debug("tickMining");

        BlockState blockState = world.getBlockState(miningPos);

        boolean isPickAxe = blockState.isToolEffective(ToolType.PICKAXE);
        boolean isShovel = blockState.isToolEffective(ToolType.SHOVEL);

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

        world.setBlockState(miningPos, railType.getDefaultState().rotate(world, miningPos, getRailRotation()));
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

        world.setBlockState(miningPos, torchType.getDefaultState().rotate(world, miningPos, getTorchRotation()));
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
