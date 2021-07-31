package com.danczer.excavator;

import net.minecraft.block.*;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.RailShape;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExcavatorMinecartLogic {

    public enum MiningStatus {
        Rolling(0), Mining(1), HazardCliff(2), HazardLava(3), HazardWater(4), HazardUnknownFluid(5), DepletedConsumable(6), EmergencyStop(7);

        public final int Value;

        MiningStatus(int value) {
            Value = value;
        }

        public static MiningStatus Find(int value) {
            switch (value) {
                case 1:
                    return MiningStatus.Mining;
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
                case 7:
                    return MiningStatus.EmergencyStop;
                default:
                    return MiningStatus.Rolling;
            }
        }
    }

    private static final Logger LOGGER = LogManager.getLogger();


    private final static int MiningTimeShovel = 8;
    private final static int MiningTimePickAxe = 19;
    private final static int MiningCountZ = 3;
    private final static int TorchPlacementDistance = 6;
    private final static float MaxMiningHardness = 50f; //Obsidian

    private final World world;
    private final ExcavatorMinecartEntity minecartEntity;

    private static final Item pickaxeItem = Items.IRON_PICKAXE;
    private static final Item shovelItem = Items.IRON_SHOVEL;

    private BlockPos lastTorchPos;
    private BlockPos miningPos;
    private Direction miningDir;

    private int miningBlockTick = 0;
    private int miningStackTick = 0;
    private int previousProgress = 0;

    private boolean isMinecartTurning;

    public BlockItem railTypeItem;
    public BlockItem torchTypeItem;

    public MiningStatus miningStatus = MiningStatus.Rolling;

    public ExcavatorMinecartLogic(ExcavatorMinecartEntity minecartEntity) {
        this.minecartEntity = minecartEntity;
        this.world = minecartEntity.world;
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

        miningBlockTick = compound.getInt("miningTimerTick");
        miningStackTick = compound.getInt("miningCountTick");
    }

    public void writeAdditional(CompoundNBT compound) {
        compound.putLong("miningPos", miningPos == null ? 0 : miningPos.toLong());
        compound.putLong("lastTorchPos", lastTorchPos == null ? 0 : lastTorchPos.toLong());
        compound.putInt("miningDir", miningDir == null ? 0 : miningDir.getIndex());
        compound.putInt("miningTimerTick", miningBlockTick);
        compound.putInt("miningCountTick", miningStackTick);
    }

    public Vector3d getDirectoryVector() {
        if (miningDir == null) return Vector3d.ZERO;

        switch (miningDir) {
            case NORTH:
            case SOUTH:
            case WEST:
            case EAST:
                return new Vector3d(toVector3f(miningDir)).normalize();
            case DOWN:
            case UP:
            default:
                return Vector3d.ZERO;
        }
    }

    public Vector3f toVector3f(Direction dir) {
        return new Vector3f((float)dir.getXOffset(), (float)dir.getYOffset(), (float)dir.getZOffset());
    }

    public void tick() {
        if (railTypeItem == null || torchTypeItem == null) {
            resetMining();
            miningStatus = MiningStatus.DepletedConsumable;
            return;
        }

        BlockPos minecartPos = minecartEntity.getPosition();
        LOGGER.debug("minecartEntity minecartPos: " + minecartPos);

        BlockPos frontPos = getMiningPlace(minecartPos);

        LOGGER.debug("FrontPos: " + frontPos);

        //not on rail or other issue
        if (frontPos == null) {
            resetMining();
        } else {
            miningStatus = checkFrontStatus(frontPos, minecartPos);

            LOGGER.debug("miningStatus: " + miningStatus);

            //nothing to do
            if (miningStatus == MiningStatus.Rolling) {
                miningDone(frontPos);
            } else if (miningStatus == MiningStatus.Mining) {
                if (miningPos == null) {
                    beginMining(frontPos.offset(Direction.UP, MiningCountZ - 1));
                    miningStackTick = 0;

                    LOGGER.debug("beginMining");
                } else {
                    boolean isBlockMined = tickBlockMining();

                    if (isBlockMined) {
                        miningStackTick++;

                        if (miningStackTick > MiningCountZ) {
                            miningDone(frontPos);
                        } else { //mining of the stack is done
                            beginMining(miningPos.down());
                        }
                    }
                }
            }
        }
    }

    private BlockPos getMiningPlace(BlockPos pos) {
        if (!isRailTrack(pos)) return null;

        Vector3d motion = minecartEntity.getMotion();

        LOGGER.debug("minecartEntity getMotion: " + motion);

        Direction dir;

        if (motion.lengthSquared() <= 0.0001d) {
            dir = miningDir;
        } else {
            dir = Direction.getFacingFromVector(motion.x, motion.y, motion.z);
        }

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

        boolean isMinecartAscending =
                railShape == RailShape.ASCENDING_EAST && dir == Direction.EAST ||
                        railShape == RailShape.ASCENDING_WEST && dir == Direction.WEST ||
                        railShape == RailShape.ASCENDING_NORTH && dir == Direction.NORTH ||
                        railShape == RailShape.ASCENDING_SOUTH && dir == Direction.SOUTH;

        LOGGER.debug("minecartEntity isMinecartTurning: " + isMinecartTurning);
        LOGGER.debug("minecartEntity adjusted dir: " + dir);

        miningDir = dir;

        BlockPos resultPos = pos.offset(dir);

        if (isMinecartAscending) {
            return resultPos.up();
        } else {
            return resultPos;
        }
    }

    private boolean isRailTrack(BlockPos targetPos) {
        BlockState blockState = world.getBlockState(targetPos);

        return blockState.isIn(BlockTags.RAILS);
    }

    private boolean isFrontHarvested(BlockPos pos) {
        for (int i = 0; i < MiningCountZ; i++) {
            if (!isBlockHarvested(pos)) return false;

            pos = pos.up();
        }

        return true;
    }


    private MiningStatus checkFrontStatus(BlockPos frontPos, BlockPos minecartPos) {
        if(isStopSign(minecartPos) || isStopSign(frontPos)){
            return MiningStatus.EmergencyStop;
        }

        BlockPos frontDown = frontPos.down();
        BlockPos behindFrontDown = frontPos.down().offset(miningDir);

        MiningStatus miningStatus;

        //front bottom
        if (isAir(frontDown)) return MiningStatus.HazardCliff;
        if (isAir(behindFrontDown)) return MiningStatus.HazardCliff;

        if ((miningStatus = checkStatusAt(frontDown)) != MiningStatus.Mining) return miningStatus;
        if ((miningStatus = checkStatusAt(behindFrontDown)) != MiningStatus.Mining) return miningStatus;

        //behind front bottom
        if ((miningStatus = checkStatusAt(frontPos.offset(miningDir).down())) != MiningStatus.Mining)
            return miningStatus;

        //front top
        if ((miningStatus = checkStatusAt(frontPos.up(MiningCountZ))) != MiningStatus.Mining) return miningStatus;

        //behind the Front
        if ((miningStatus = checkPosStackStatus(frontPos.offset(miningDir))) != MiningStatus.Mining)
            return miningStatus;

        //front sides
        if ((miningStatus = checkPosStackStatus(frontPos.offset(miningDir.rotateY()))) != MiningStatus.Mining)
            return miningStatus;
        if ((miningStatus = checkPosStackStatus(frontPos.offset(miningDir.rotateYCCW()))) != MiningStatus.Mining)
            return miningStatus;

        if (isFrontHarvested(frontPos)) {
            return MiningStatus.Rolling;
        } else {
            return MiningStatus.Mining;
        }
    }

    private MiningStatus checkPosStackStatus(BlockPos pos) {
        for (int i = 0; i < MiningCountZ; i++) {
            MiningStatus miningStatus = checkStatusAt(pos);
            if (miningStatus != MiningStatus.Mining) return miningStatus;
            pos = pos.up();
        }

        return MiningStatus.Mining;
    }

    private boolean isBlockHarvested(BlockPos blockPos) {
        BlockState blockState = world.getBlockState(blockPos);

        return blockState.getCollisionShape(world, blockPos).isEmpty() || blockState.isIn(BlockTags.RAILS);
    }

    private boolean isStopSign(BlockPos blockPos) {
        for (int i = 0; i < MiningCountZ; i++) {
            BlockState blockState = world.getBlockState(blockPos);
            if (blockState.isIn(BlockTags.SIGNS) || blockState.isIn(BlockTags.WALL_SIGNS)|| blockState.isIn(BlockTags.STANDING_SIGNS)) return true;
            blockPos = blockPos.up();
        }

        return false;
    }

    private boolean isAir(BlockPos pos) {
        return world.getBlockState(pos).isAir();
    }

    private MiningStatus checkStatusAt(BlockPos pos) {
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
            return MiningStatus.Mining;
        }
    }

    private void beginMining(BlockPos blockPos) {
        miningStatus = MiningStatus.Mining;
        miningPos = blockPos;
        miningBlockTick = 0;
        if (miningPos != null) {
            world.sendBlockBreakProgress(0, miningPos, -1);
        }
    }

    private void miningDone(BlockPos frontPos) {
        createRailAndTorch(frontPos);
        resetMining();
    }

    private void resetMining() {
        if (miningPos != null) {
            world.sendBlockBreakProgress(0, miningPos, -1);
        }
        miningStatus = MiningStatus.Rolling;
        miningPos = null;
        miningBlockTick = 0;
        miningStackTick = 0;
    }

    private boolean tickBlockMining() {
        if (isBlockHarvested(miningPos)) return true;

        BlockState blockState = world.getBlockState(miningPos);

        float blockHardness = blockState.getBlockHardness(world, miningPos);

        boolean mineAllowed = blockHardness >= 0f && blockHardness < MaxMiningHardness;

        boolean byHand = !blockState.getRequiresTool();
        boolean isPickAxe = pickaxeItem.canHarvestBlock(blockState);
        boolean isShovel = shovelItem.canHarvestBlock(blockState);

        float pickAxeSpeed = pickaxeItem.getDestroySpeed(new ItemStack(pickaxeItem), blockState);
        float shovelSpeed = shovelItem.getDestroySpeed(new ItemStack(shovelItem), blockState);

        LOGGER.debug("tickMining on " + blockState.getBlock().getRegistryName() + " at " + miningPos +
                ", mineAllowed: " + mineAllowed +
                ", byHand: " + byHand +
                ", pickaxe canHarvestBlock: " + isPickAxe +
                " ,pickaxe getDestroySpeed: " + pickAxeSpeed +
                " ,shovelItem canHarvestBlock: " + isShovel +
                " ,shovelItem getDestroySpeed: " + shovelSpeed +
                " ,BlockHardness: " + blockHardness);


        int miningTime = -1;

        if (mineAllowed && (byHand || isPickAxe || isShovel)) {
            miningBlockTick++;
            LOGGER.debug("byHand:" + byHand +"isPickAxe:" + isPickAxe + ", isShovel:" + isShovel + ", miningBlockTick:" + miningBlockTick);

            float miningSpeed;

            if (isPickAxe) {
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
                miningTime = MiningTimePickAxe;
                miningSpeed = pickAxeSpeed;
            } else {
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
                miningTime = MiningTimeShovel;
                miningSpeed = shovelSpeed;
            }

            float damage = miningSpeed / blockHardness / 30f;
            if (damage > 1) {
                damage = 1f;
            }

            float ticks = (float) Math.ceil(1 / damage);
            float seconds = ticks / 20;

            LOGGER.debug("Mining time in ticks:" + ticks + ", sec:" + seconds);

            int progress = (int) ((float) miningBlockTick / miningTime * 10.0F);
            if (progress != previousProgress && progress % 5 == 0) {
                world.sendBlockBreakProgress(0, miningPos, progress);
                previousProgress = progress;
            }

            if (miningBlockTick > miningTime) {
                world.destroyBlock(miningPos, true);
                return true;
            } else {
                return false;
            }
        } else {
            miningStatus = MiningStatus.EmergencyStop;
            return false;
        }
    }

    private void createRailAndTorch(BlockPos frontPos) {
        boolean railCreated = createRail(frontPos.offset(Direction.UP, 0));

        //Do not create torch if rolling on existing rails
        if (railCreated) {
            createTorch(frontPos.offset(Direction.UP, 2));
        }
    }

    private boolean createRail(BlockPos blockPos) {
        if (isRailTrack(blockPos) || isRailTrack(blockPos.offset(Direction.DOWN, 1))) return false;

        if (railTypeItem != null) {
            if (minecartEntity.reduceInventoryItem(railTypeItem)) {
                world.setBlockState(blockPos, railTypeItem.getBlock().getDefaultState().rotate(world, blockPos, getRailRotation()));

                return true;
            }
        }

        return false;
    }

    private Rotation getRailRotation() {
        return Rotation.NONE;
    }

    private void createTorch(BlockPos blockPos) {
        if (lastTorchPos != null && lastTorchPos.withinDistance(new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ()), TorchPlacementDistance))
            return;

        if (miningDir == null) return;
        if (torchTypeItem == null) return;

        Block torchBlock;
        if (torchTypeItem == Items.TORCH) {
            torchBlock = Blocks.WALL_TORCH;
        } else if (torchTypeItem == Items.REDSTONE_TORCH) {
            torchBlock = Blocks.REDSTONE_WALL_TORCH;
            blockPos = blockPos.down(); //one down
        } else if (torchTypeItem == Items.SOUL_TORCH) {
            torchBlock = Blocks.SOUL_WALL_TORCH;
        } else {
            torchBlock = null;
        }

        BlockState targetBlockState = world.getBlockState(blockPos);

        if (targetBlockState.isIn(Blocks.WALL_TORCH)
                || targetBlockState.isIn(Blocks.REDSTONE_TORCH)
                || targetBlockState.isIn(Blocks.SOUL_TORCH)) {
            lastTorchPos = blockPos;
            return;
        }

        //create new torch
        if (torchBlock != null) {
            Direction torchDir = null;
            if (!isAir(blockPos.offset(miningDir.rotateY(), 1))) {
                torchDir = miningDir.rotateYCCW();
            } else if (!isAir(blockPos.offset(miningDir.rotateYCCW(), 1))) {
                torchDir = miningDir.rotateY();
            }

            //place torch
            if (torchDir != null && minecartEntity.reduceInventoryItem(torchTypeItem)) {
                world.setBlockState(blockPos, torchBlock.getDefaultState().with(HorizontalBlock.HORIZONTAL_FACING, torchDir));
                lastTorchPos = blockPos;
            }
        }
    }
}
