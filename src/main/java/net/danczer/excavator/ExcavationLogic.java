package net.danczer.excavator;

import net.minecraft.block.*;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

public class ExcavationLogic {

    private static final int TICK_PER_SECOND = 20;

    public enum MiningStatus {
        Rolling(0),
        Mining(1),
        HazardCliff(2),
        HazardLava(3),
        HazardWater(4),
        HazardUnknownFluid(5),
        MissingToolchain(6),
        InventoryIsFull(7),
        EmergencyStop(8);

        public final int Value;

        MiningStatus(int value) {
            Value = value;
        }

        public static MiningStatus Find(int value) {
            return switch (value) {
                case 1 -> MiningStatus.Mining;
                case 2 -> MiningStatus.HazardCliff;
                case 3 -> MiningStatus.HazardLava;
                case 4 -> MiningStatus.HazardWater;
                case 5 -> MiningStatus.HazardUnknownFluid;
                case 6 -> MiningStatus.MissingToolchain;
                case 7 -> MiningStatus.InventoryIsFull;
                case 8 -> MiningStatus.EmergencyStop;
                default -> MiningStatus.Rolling;
            };
        }
    }

    public static final List<BlockItem> USABLE_RAIL_ITEMS = new ArrayList<>();
    public static final List<BlockItem> USABLE_TORCH_ITEMS = new ArrayList<>();
    public static final List<Block> TORCH_WALL_BLOCKS = new ArrayList<>();
    public static final List<MiningToolItem> USABLE_PICKAXE_ITEMS = new ArrayList<>();
    public static final List<MiningToolItem> USABLE_SHOVEL_ITEMS = new ArrayList<>();

    static {
        USABLE_TORCH_ITEMS.add((BlockItem) Items.TORCH);
        USABLE_TORCH_ITEMS.add((BlockItem)Items.REDSTONE_TORCH);
        USABLE_TORCH_ITEMS.add((BlockItem)Items.SOUL_TORCH);

        TORCH_WALL_BLOCKS.add(Blocks.WALL_TORCH);
        TORCH_WALL_BLOCKS.add(Blocks.REDSTONE_WALL_TORCH);
        TORCH_WALL_BLOCKS.add(Blocks.SOUL_WALL_TORCH);

        USABLE_RAIL_ITEMS.add((BlockItem)Items.RAIL);
        USABLE_RAIL_ITEMS.add((BlockItem)Items.POWERED_RAIL);

        USABLE_PICKAXE_ITEMS.add((MiningToolItem)Items.NETHERITE_PICKAXE);
        USABLE_PICKAXE_ITEMS.add((MiningToolItem)Items.DIAMOND_PICKAXE);
        USABLE_PICKAXE_ITEMS.add((MiningToolItem)Items.GOLDEN_PICKAXE);
        USABLE_PICKAXE_ITEMS.add((MiningToolItem)Items.IRON_PICKAXE);
        USABLE_PICKAXE_ITEMS.add((MiningToolItem)Items.STONE_PICKAXE);
        USABLE_PICKAXE_ITEMS.add((MiningToolItem)Items.WOODEN_PICKAXE);

        USABLE_SHOVEL_ITEMS.add((MiningToolItem)Items.NETHERITE_SHOVEL);
        USABLE_SHOVEL_ITEMS.add((MiningToolItem)Items.DIAMOND_SHOVEL);
        USABLE_SHOVEL_ITEMS.add((MiningToolItem)Items.IRON_SHOVEL);
        USABLE_SHOVEL_ITEMS.add((MiningToolItem)Items.GOLDEN_SHOVEL);
        USABLE_SHOVEL_ITEMS.add((MiningToolItem)Items.STONE_SHOVEL);
        USABLE_SHOVEL_ITEMS.add((MiningToolItem)Items.WOODEN_SHOVEL);
    }

    private final static int MiningCountZ = 3;
    private final static int TorchPlacementDistance = 6;
    private final static float MaxMiningHardness = 50f; //Obsidian

    private final World world;

    private final Inventory excavatorInventory;
    private final AbstractMinecartEntity minecartEntity;

    private BlockPos lastTorchPos;
    private BlockPos miningPos;
    private Direction miningDir;

    private int miningBlockTick = 0;
    private int miningStackTick = 0;
    private int previousMiningBlockTick = 0;

    public BlockItem railType;
    public BlockItem torchType;
    public MiningToolItem pickaxeType;
    public MiningToolItem shovelType;

    public MiningStatus miningStatus = MiningStatus.Rolling;

    public ExcavationLogic(AbstractMinecartEntity minecartEntity, Inventory inventory) {
        this.minecartEntity = minecartEntity;
        this.excavatorInventory = inventory;
        this.world = minecartEntity.getWorld();

        if(isItemListContainsNull(USABLE_TORCH_ITEMS)){
            throw new NullPointerException("Invalid Torch in the usable list for excavator!");
        }

        if(isItemListContainsNull(USABLE_RAIL_ITEMS)){
            throw new NullPointerException("Invalid Rail in the usable list for excavator!");
        }

        if(isItemListContainsNull(USABLE_PICKAXE_ITEMS)){
            throw new NullPointerException("Invalid Pickaxe in the usable list for excavator!");
        }

        if(isItemListContainsNull(USABLE_SHOVEL_ITEMS)){
            throw new NullPointerException("Invalid Shovel in the usable list for excavator!");
        }
    }

    private <T extends Item> boolean isItemListContainsNull(List<T> list) {
        for (Object obj: list) {
            if(obj == null) return true;
        }

        return false;
    }

    public void readNbt(NbtCompound compound) {

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
            miningDir = Direction.byId(dirIndex);
        }

        miningBlockTick = compound.getInt("miningTimerTick");
        miningStackTick = compound.getInt("miningCountTick");
    }

    public void updateExcavatorToolchain() {

        int latestTorchItemIdx = Integer.MAX_VALUE;
        int latestRailItemIdx = Integer.MAX_VALUE;
        int latestPickaxeItemIdx = Integer.MAX_VALUE;
        int latestShovelItemIdx = Integer.MAX_VALUE;

        torchType = null;
        railType = null;
        pickaxeType = null;
        shovelType = null;

        for (int i = 0; i < excavatorInventory.size(); i++) {
            ItemStack itemStack = excavatorInventory.getStack(i);
            Item item = itemStack.getItem();

            if(itemStack.isEmpty()) continue;

            if (item instanceof BlockItem) {
                int idx;

                if((idx = USABLE_TORCH_ITEMS.indexOf(item)) >=0 && latestTorchItemIdx > idx){
                    latestTorchItemIdx = idx;
                    torchType = (BlockItem) item;
                }

                if((idx = USABLE_RAIL_ITEMS.indexOf(item)) >=0 && latestRailItemIdx > idx){
                    latestRailItemIdx = idx;
                    railType = (BlockItem) item;
                }
            }else if (item instanceof MiningToolItem) {
                int idx;

                if((idx = USABLE_PICKAXE_ITEMS.indexOf(item)) >=0 && latestPickaxeItemIdx > idx){
                    latestPickaxeItemIdx = idx;
                    pickaxeType = (MiningToolItem) item;
                }

                if((idx = USABLE_SHOVEL_ITEMS.indexOf(item)) >=0 && latestShovelItemIdx > idx){
                    latestShovelItemIdx = idx;
                    shovelType = (MiningToolItem) item;
                }
            }
        }
    }


    public void writeNbt(NbtCompound compound) {
        compound.putLong("miningPos", miningPos == null ? 0 : miningPos.asLong());
        compound.putLong("lastTorchPos", lastTorchPos == null ? 0 : lastTorchPos.asLong());
        compound.putInt("miningDir", miningDir == null ? 0 : miningDir.getId());
        compound.putInt("miningTimerTick", miningBlockTick);
        compound.putInt("miningCountTick", miningStackTick);
    }

    public Vec3d getDirectoryVector() {
        if (miningDir == null) return Vec3d.ZERO;

        switch (miningDir) {
            case NORTH:
            case SOUTH:
            case WEST:
            case EAST:
                Vec3d vec = new Vec3d(miningDir.getUnitVector());
                vec.normalize();
                return vec;
            case DOWN:
            case UP:
            default:
                return Vec3d.ZERO;
        }
    }

    public boolean isToolchainSet(){
        return railType != null && pickaxeType != null && shovelType != null;
    }

    public void tick() {
        if (!isToolchainSet()) {
            resetMining();
            miningStatus = MiningStatus.MissingToolchain;
            return;
        }

        if(isInventoryFull()){
            miningStatus = MiningStatus.InventoryIsFull;
            return;
        }

        BlockPos minecartPos = minecartEntity.getBlockPos();

        BlockPos frontPos = getMiningPlace(minecartPos);

        //not on rail or other issue
        if (frontPos == null) {
            resetMining();
        } else {
            miningStatus = checkFrontStatus(frontPos, minecartPos);

            //nothing to do
            if (miningStatus == MiningStatus.Rolling) {
                miningDone(frontPos);
            } else if (miningStatus == MiningStatus.Mining) {
                if (miningPos == null) {
                    beginMining(frontPos.offset(Direction.UP, MiningCountZ - 1));
                    miningStackTick = 0;
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

    public boolean isInventoryFull() {
        for (int i = 0; i < excavatorInventory.size(); i++) {
            ItemStack itemStack = excavatorInventory.getStack(i);
            Item item = itemStack.getItem();

            if (isToolchainItem(item)) continue;

            if (itemStack.isEmpty() || itemStack.getCount() < itemStack.getMaxCount()) return false;
        }

        return true;
    }

    private boolean isToolchainItem(Item item) {
        if (item instanceof BlockItem) {
            return USABLE_TORCH_ITEMS.contains(item) || USABLE_RAIL_ITEMS.contains(item);
        }else if (item instanceof MiningToolItem) {
            return USABLE_PICKAXE_ITEMS.contains(item) || USABLE_SHOVEL_ITEMS.contains(item);
        }

        return false;
    }

    private BlockPos getMiningPlace(BlockPos pos) {
        if (!isRailTrack(pos)) return null;

        Vec3d motion = minecartEntity.getVelocity();

        Direction dir;

        if (motion.lengthSquared() <= 0.0001d) {
            dir = miningDir;
        } else {
            dir = Direction.getFacing(motion.x, 0, motion.z);
        }

        if (dir == null) return null;

        BlockState bs = world.getBlockState(pos);
        AbstractRailBlock railBlock = (AbstractRailBlock) bs.getBlock();

        RailShape railShape = bs.get(railBlock.getShapeProperty());


        //fix detection on turns
        if (dir == Direction.NORTH || dir == Direction.SOUTH) {
            if (railShape == RailShape.NORTH_WEST || railShape == RailShape.SOUTH_WEST) {
                lastTorchPos = null;
                dir = Direction.WEST;
            }
            if (railShape == RailShape.NORTH_EAST || railShape == RailShape.SOUTH_EAST) {
                lastTorchPos = null;
                dir = Direction.EAST;
            }
        } else if (dir == Direction.WEST || dir == Direction.EAST) {
            if (railShape == RailShape.NORTH_WEST || railShape == RailShape.NORTH_EAST) {
                lastTorchPos = null;
                dir = Direction.NORTH;
            }
            if (railShape == RailShape.SOUTH_WEST || railShape == RailShape.SOUTH_EAST) {
                lastTorchPos = null;
                dir = Direction.SOUTH;
            }
        }

        boolean isMinecartAscending =
                railShape == RailShape.ASCENDING_EAST && dir == Direction.EAST ||
                        railShape == RailShape.ASCENDING_WEST && dir == Direction.WEST ||
                        railShape == RailShape.ASCENDING_NORTH && dir == Direction.NORTH ||
                        railShape == RailShape.ASCENDING_SOUTH && dir == Direction.SOUTH;

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
        if ((miningStatus = checkPosStackStatus(frontPos.offset(miningDir.rotateYClockwise()))) != MiningStatus.Mining)
            return miningStatus;
        if ((miningStatus = checkPosStackStatus(frontPos.offset(miningDir.rotateYCounterclockwise()))) != MiningStatus.Mining)
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
            if (fLuidState.isIn(FluidTags.LAVA)) {
                return MiningStatus.HazardLava;
            } else if (fLuidState.isIn(FluidTags.WATER)) {
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
            world.setBlockBreakingInfo(0, miningPos, -1);
        }
    }

    private void miningDone(BlockPos frontPos) {
        createRailAndTorch(frontPos);
        resetMining();
    }

    private void resetMining() {
        if (miningPos != null) {
            world.setBlockBreakingInfo(0, miningPos, -1);
        }
        miningStatus = MiningStatus.Rolling;
        miningPos = null;
        miningBlockTick = 0;
        miningStackTick = 0;
    }

    private boolean tickBlockMining() {
        if (isBlockHarvested(miningPos)) return true;

        BlockState blockState = world.getBlockState(miningPos);

        float blockHardness = blockState.getHardness(world, miningPos);

        boolean mineAllowed = blockHardness >= 0f && blockHardness < MaxMiningHardness;

        boolean byHand = !blockState.isToolRequired();
        boolean isPickAxe = pickaxeType.isSuitableFor(blockState);
        boolean isShovel = shovelType.isSuitableFor(blockState);

        float pickAxeSpeed = pickaxeType.getMiningSpeedMultiplier(new ItemStack(pickaxeType), blockState);
        float shovelSpeed = shovelType.getMiningSpeedMultiplier(new ItemStack(shovelType), blockState);

        if(isPickAxe && isShovel){
            if(pickAxeSpeed > shovelSpeed){
                isShovel = false;
            }
        }

        if (mineAllowed && (byHand || isPickAxe || isShovel)) {
            miningBlockTick++;

            float miningSpeed = 1.5f;

            if (isPickAxe) {
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
                miningSpeed /= pickAxeSpeed;
            } else if(isShovel){
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
                miningSpeed /= shovelSpeed;
            }else{
                world.playSound(0.0, 0.0, 0.0, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0F, 1.0F, true);
            }

            float timeToBreakTheBlock = blockHardness * miningSpeed * TICK_PER_SECOND;

            if (miningBlockTick > previousMiningBlockTick + 5) {
                int progress = Math.min((int) ((miningBlockTick / timeToBreakTheBlock)*10f), 10);

                world.setBlockBreakingInfo(0, miningPos, progress);
                previousMiningBlockTick = miningBlockTick;
            }

            if (miningBlockTick > timeToBreakTheBlock) {
                world.breakBlock(miningPos, true);
                previousMiningBlockTick = 0;
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
        //TODO check torch along the line
        if (railCreated) {
            createTorch(frontPos.offset(Direction.UP, 2));
        }
    }

    private boolean createRail(BlockPos blockPos) {
        if (isRailTrack(blockPos) || isRailTrack(blockPos.offset(Direction.DOWN, 1))) return false;

        if (railType != null) {
            if (reduceInventoryItem(railType)) {
                world.setBlockState(blockPos, railType.getBlock().getDefaultState().rotate(getRailRotation()));

                return true;
            }
        }

        return false;
    }

    private BlockRotation getRailRotation() {
       return BlockRotation.NONE;
    }

    private void createTorch(BlockPos blockPos) {
        if (torchType == null) return; //optional
        if (miningDir == null) return;

        BlockState targetBlockState = world.getBlockState(blockPos);

        //find existing torch
        for (BlockItem torch : USABLE_TORCH_ITEMS) {
            Block wallBlock = getTorchWallBlock(torch.getBlock());
            if(targetBlockState.isOf(wallBlock)){
                lastTorchPos = blockPos;
                return;
            }
        }

        if (lastTorchPos != null && lastTorchPos.isWithinDistance(new Vec3i(blockPos.getX(), blockPos.getY(), blockPos.getZ()), TorchPlacementDistance))
            return;

        Direction torchDir = null;
        if (!isAir(blockPos.offset(miningDir.rotateYClockwise(), 1))) {
            torchDir = miningDir.rotateYCounterclockwise();
        } else if (!isAir(blockPos.offset(miningDir.rotateYCounterclockwise(), 1))) {
            torchDir = miningDir.rotateYClockwise();
        }

        //place torch
        if (torchDir != null && reduceInventoryItem(torchType)) {
            Block wallBlock = getTorchWallBlock(torchType.getBlock());
            BlockState wallBlockState = wallBlock.getDefaultState();

            if(wallBlockState.contains(Properties.HORIZONTAL_FACING)){
                world.setBlockState(blockPos, wallBlockState.with(Properties.HORIZONTAL_FACING, torchDir));
            }else{
                world.setBlockState(blockPos, wallBlockState);
            }

            lastTorchPos = blockPos;
        }
    }

    private Block getTorchWallBlock(Block block){
        for (Block wallBlock: TORCH_WALL_BLOCKS) {
            if(block.getClass().isAssignableFrom(wallBlock.getClass())){
                return wallBlock;
            }
        }

        return block;
    }

    private boolean reduceInventoryItem(Item item) {
        for (int i = 0; i < excavatorInventory.size(); i++) {
            ItemStack itemStack = excavatorInventory.getStack(i);

            if (!itemStack.isEmpty() && itemStack.getItem() == item) {
                itemStack.split(1);
                return true;
            }
        }

        return false;
    }
}
