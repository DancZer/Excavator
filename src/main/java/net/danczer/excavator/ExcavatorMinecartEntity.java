package net.danczer.excavator;

import com.mojang.logging.LogUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ExcavatorMinecartEntity extends StorageMinecartEntity implements Hopper {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TrackedData<Integer> MINING_STATUS;
    private static final List<Item> Rails = new ArrayList<>();
    private static final List<Item> Torches = new ArrayList<>();

    static {
        MINING_STATUS = DataTracker.registerData(ExcavatorMinecartEntity.class, TrackedDataHandlerRegistry.INTEGER);

        Torches.add(Items.TORCH);
        Torches.add(Items.REDSTONE_TORCH);
        Torches.add(Items.SOUL_TORCH);

        Rails.add(Items.RAIL);
        Rails.add(Items.POWERED_RAIL);
    }
    private static final float CollectBlockWithHardness = 3f;
    private static final float MinecartPushForce = 0.005f;

    private final ExcavatorMinecartLogic logic = new ExcavatorMinecartLogic(this);
    private boolean enabled = true;
    private int transferTicker = -1;
    private final BlockPos lastPosition = BlockPos.ORIGIN;

    public ExcavatorMinecartEntity(EntityType<? extends StorageMinecartEntity> entityType, World world) {
        super(entityType, world);

        for (int i = 0; i < Torches.size(); i++) {
            LOGGER.debug("Torches: "+Torches.get(i).getName());
        }
        for (int i = 0; i < Torches.size(); i++) {
            LOGGER.debug("Rails: "+Torches.get(i).getName());
        }
    }

    private ExcavatorMinecartEntity(World world, double x, double y, double z) {
        super(ExcavatorMod.EXCAVATOR_ENTITY, x, y, z, world);
    }

    public static ExcavatorMinecartEntity create(World world, double x, double y, double z){
        return new ExcavatorMinecartEntity(world, x, y, z);
    }

    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(MINING_STATUS, 0);
    }
    public AbstractMinecartEntity.Type getMinecartType() {
        return null;
    }

    protected Item getItem() {
        return ExcavatorMod.EXCAVATOR_MINECART_ITEM;
    }

    public ItemStack getPickBlockStack()
    {
        return new ItemStack(ExcavatorMod.EXCAVATOR_MINECART_ITEM);
    }

    public BlockState getDefaultContainedBlock() {
        return Blocks.REDSTONE_BLOCK.getDefaultState();
    }

    public int getDefaultBlockOffset() {
        return 1;
    }

    public int size() {
        return ExcavatorScreenHandler.InventorySize;
    }

    public ExcavatorScreenHandler getScreenHandler(int id, PlayerInventory playerInventoryIn) {
        return new ExcavatorScreenHandler(id, playerInventoryIn, this);
    }

    public void onActivatorRail(int x, int y, int z, boolean powered) {
        boolean bl = !powered;
        if (bl != this.isEnabled()) {
            this.setEnabled(bl);
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public World getWorld() {
        return this.world;
    }

    public double getHopperX() {
        return this.getX();
    }

    public double getHopperY() {
        return this.getY() + 0.5D;
    }

    public double getHopperZ() {
        return this.getZ();
    }

    public boolean isInventoryFull() {
        for (int i = 0; i < size(); i++) {
            ItemStack itemStack = getStack(i);
            Item item = itemStack.getItem();

            if (item == logic.railTypeItem) continue;
            if (item == logic.torchTypeItem) continue;

            if (itemStack.isEmpty() || itemStack.getCount() < itemStack.getMaxCount()) return false;
        }

        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound compound) {
        logic.writeNbt(compound);

        compound.putInt("TransferCooldown", this.transferTicker);
        compound.putBoolean("Enabled", this.enabled);

        return super.writeNbt(compound);
    }

    @Override
    public void readNbt(NbtCompound compound) {
        super.readNbt(compound);

        logic.readNbt(compound);

        this.transferTicker = compound.getInt("TransferCooldown");
        this.enabled = !compound.contains("Enabled") || compound.getBoolean("Enabled");
    }

    public void tick() {
        ExcavatorMinecartLogic.MiningStatus prevStatus = logic.miningStatus;

        excavatorTick();

        if(logic.miningStatus == ExcavatorMinecartLogic.MiningStatus.Rolling){
            if(prevStatus == ExcavatorMinecartLogic.MiningStatus.Mining){
                LOGGER.debug("Minecart Pushed");
                setVelocity(logic.getDirectoryVector().multiply(MinecartPushForce));
            }
            super.tick();
        }else{
            setVelocity(Vec3d.ZERO);
        }

        hopperTick();
    }

    private void excavatorTick() {
        if (this.world.isClient && this.isAlive() && this.isEnabled()) {
            logic.railTypeItem = findRailTypeItem();
            logic.torchTypeItem = findTorchTypeItem();

            boolean isFull = isInventoryFull();

            LOGGER.debug("isFull: " + isFull + ", railTypeItem: "+logic.railTypeItem+", torchTypeItem:"+logic.torchTypeItem);

            if (isFull) {
                setMiningStatus(ExcavatorMinecartLogic.MiningStatus.InventoryIsFull);
            } else {
                logic.tick();

                setMiningStatus(logic.miningStatus);
            }

            LOGGER.debug("Logic MiningStatus: " + logic.miningStatus);

            if (this.random.nextInt(4) == 0) {
                showMiningStatus();
            }
        }
    }

    public BlockItem findRailTypeItem() {
        return findInventoryItem(Rails);
    }

    public BlockItem findTorchTypeItem() {
        return findInventoryItem(Torches);
    }

    public BlockItem findInventoryItem(List<Item> items) {
        for (int i = 0; i < size(); i++) {
            ItemStack itemStack = getStack(i);

            Item item = itemStack.getItem();

            LOGGER.debug("findInventoryItem: itemStack:"+itemStack.getName()+", item:" + item.getName());
            if (!itemStack.isEmpty() && items.contains(item) && item instanceof BlockItem) {
                return (BlockItem) item;
            }
        }

        return null;
    }

    public boolean reduceInventoryItem(Item item) {
        for (int i = 0; i < size(); i++) {
            ItemStack itemStack = getStack(i);

            if (!itemStack.isEmpty() && itemStack.getItem() == item) {
                itemStack.split(1);
                return true;
            }
        }

        return false;
    }

    private void showMiningStatus() {
        ExcavatorMinecartLogic.MiningStatus miningStatus = getMiningStatus();
        LOGGER.debug("Hazard: " + miningStatus);

        ParticleEffect particleType = null;

        switch (miningStatus) {
            case Mining:
                particleType = ParticleTypes.LARGE_SMOKE;
                break;
            case HazardCliff:
                particleType = ParticleTypes.ENTITY_EFFECT;
                break;
            case HazardLava:
                particleType = ParticleTypes.FALLING_LAVA;
                break;
            case HazardWater:
                particleType = ParticleTypes.FALLING_WATER;
                break;
            case HazardUnknownFluid:
                particleType = ParticleTypes.BUBBLE;
                break;
            case DepletedConsumable:
                particleType = ParticleTypes.WITCH;
                break;
            case InventoryIsFull:
                particleType = ParticleTypes.FALLING_HONEY;
                break;
            case EmergencyStop:
                particleType = ParticleTypes.COMPOSTER;
                break;
            case Rolling:
            default:
                break;
        }

        if (particleType != null) {
            this.world.addParticle(particleType, this.getHopperX(), this.getHopperY() + 0.8D, this.getHopperZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    private ExcavatorMinecartLogic.MiningStatus getMiningStatus() {
        return ExcavatorMinecartLogic.MiningStatus.Find(this.getDataTracker().get(MINING_STATUS));
    }

    private void setMiningStatus(ExcavatorMinecartLogic.MiningStatus miningStatus) {
        this.dataTracker.set(MINING_STATUS, miningStatus.Value);
    }

    private void hopperTick() {
        if (this.world.isClient && this.isAlive() && this.isEnabled()) {
            BlockPos blockpos = this.getBlockPos();
            if (blockpos.equals(this.lastPosition)) {
                --this.transferTicker;
            } else {
                this.setTransferTicker(0);
            }

            if (!this.canTransfer()) {
                this.setTransferTicker(0);
                if (this.captureDroppedItems()) {
                    this.setTransferTicker(4);
                    this.markDirty();
                }
            }
        }
    }

    public void setTransferTicker(int transferTickerIn) {
        this.transferTicker = transferTickerIn;
    }

    /**
     * Returns whether the hopper cart can currently transfer an item.
     */
    public boolean canTransfer() {
        return this.transferTicker > 0;
    }

    public boolean captureDroppedItems() {
        List<ItemEntity> list = this.world.getEntitiesByClass(ItemEntity.class, this.getBoundingBox().expand(0.25, 0.0, 0.25), EntityPredicates.VALID_ENTITY);

        for (ItemEntity itemEntity : list) {
            Item item = itemEntity.getStack().getItem();

            //collect only usefull blocks
            if (item instanceof BlockItem) {
                BlockItem blockItem = (BlockItem) item;

                if (shouldCollectItem(blockItem)) {
                    HopperBlockEntity.extract(this, itemEntity);
                }
            } else {
                HopperBlockEntity.extract(this, itemEntity);
            }
        }

        return false;
    }

    private boolean shouldCollectItem(BlockItem blockItem){
        BlockState blockState = blockItem.getBlock().getDefaultState();

        return blockState.isToolRequired() && blockState.getHardness(world, getBlockPos()) >= CollectBlockWithHardness;
    }

    public void kill() {
        super.kill();
        if (this.world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
            this.dropItem(Blocks.OBSERVER);
            this.dropItem(Blocks.REDSTONE_BLOCK);
            this.dropItem(Blocks.HOPPER);
        }
    }
}
