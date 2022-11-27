package net.danczer.excavator;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ExcavatorMinecartEntity extends StorageMinecartEntity implements Hopper {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final float CollectBlockWithHardness = 3f;
    private static final float MinecartPushForce = 0.005f;

    private final ExcavatorMinecartLogic logic = new ExcavatorMinecartLogic(this);

    private final List<Item> Rails = new ArrayList<>();
    private final List<Item> Torches = new ArrayList<>();

    private boolean isBlocked = true;
    private int transferTicker = -1;
    private final BlockPos lastPosition = BlockPos.ORIGIN;

    private Random rand;

    public ExcavatorMinecartEntity(EntityType<? extends StorageMinecartEntity> entityEntityType, World worldIn) {
        super(entityEntityType, worldIn);
        Init();
    }

    public ExcavatorMinecartEntity(World worldIn, double x, double y, double z) {
        super(ExcavatorMod.EXCAVATOR_ENTITY, x, y, z, worldIn);
        Init();
    }

    private void Init() {
        rand = new Random();
        Torches.clear();
        Torches.add(Items.TORCH);
        Torches.add(Items.REDSTONE_TORCH);
        Torches.add(Items.SOUL_TORCH);

        Rails.clear();
        Rails.add(Items.RAIL);
        Rails.add(Items.POWERED_RAIL);
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

    public void onActivatorRail(int x, int y, int z, boolean receivingPower) {
        boolean flag = !receivingPower;
        if (flag != this.getBlocked()) {
            this.setBlocked(flag);
        }
    }

    public boolean getBlocked() {
        return this.isBlocked;
    }

    /**
     * Set whether this hopper minecart is being blocked by an activator rail.
     */
    public void setBlocked(boolean blocked) {
        this.isBlocked = blocked;
    }

    /**
     * Returns the worldObj for this tileEntity.
     */
    public World getWorld() {
        return this.world;
    }

    public double getHopperX() {
        return this.getX();
    }

    /**
     * Gets the world Y position for this hopper entity.
     */
    public double getHopperY() {
        return this.getY() + 0.5D;
    }

    /**
     * Gets the world Z position for this hopper entity.
     */
    public double getHopperZ() {
        return this.getZ();
    }

    public boolean isInventoryFull() {
        if (isEmpty()) {
            return false;
        }

        for (int i = 0; i < ExcavatorScreenHandler.InventorySize; i++) {
            ItemStack itemStack = getStack(i);

            if (itemStack.getItem() == logic.railTypeItem) continue;
            if (itemStack.getItem() == logic.torchTypeItem) continue;

            if (itemStack.isEmpty() || itemStack.getCount() < itemStack.getMaxCount()) return false;
        }

        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound compound) {
        logic.writeNbt(compound);

        compound.putInt("TransferCooldown", this.transferTicker);
        compound.putBoolean("Enabled", this.isBlocked);

        return super.writeNbt(compound);
    }

    @Override
    public void readNbt(NbtCompound compound) {
        super.readNbt(compound);

        logic.readNbt(compound);

        this.transferTicker = compound.getInt("TransferCooldown");
        this.isBlocked = !compound.contains("Enabled") || compound.getBoolean("Enabled");
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
        if (this.world.isClient && this.isAlive() && this.getBlocked()) {
            logic.railTypeItem = findRailTypeItem();
            logic.torchTypeItem = findTorchTypeItem();

            boolean isFull = isInventoryFull();

            LOGGER.debug("isFull: " + isFull);

            if (!isFull) {
                logic.tick();

                setMiningStatus(logic.miningStatus);

                LOGGER.debug("Logic MiningStatus: " + logic.miningStatus);
            } else {
                setMiningStatus(ExcavatorMinecartLogic.MiningStatus.DepletedConsumable);
            }
        }

        if (rand.nextInt(4) == 0) {
            showMiningStatus();
        }
    }

    public BlockItem findRailTypeItem() {
        return findInventoryItem(Rails);
    }

    public BlockItem findTorchTypeItem() {
        return findInventoryItem(Torches);
    }

    public BlockItem findInventoryItem(List<Item> items) {
        for (int i = 0; i < ExcavatorScreenHandler.InventorySize; i++) {
            ItemStack itemStack = getStack(i);

            Item item = itemStack.getItem();
            if (!itemStack.isEmpty() && items.contains(item) && item instanceof BlockItem) {
                return (BlockItem) item;
            }
        }

        return null;
    }

    public boolean reduceInventoryItem(Item item) {
        for (int i = 0; i < ExcavatorScreenHandler.InventorySize; i++) {
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
        return ExcavatorMinecartLogic.MiningStatus.Find(this.dataTracker.get(ExcavatorMod.MINING_STATUS));
    }

    private void setMiningStatus(ExcavatorMinecartLogic.MiningStatus miningStatus) {
        this.dataTracker.set(ExcavatorMod.MINING_STATUS, miningStatus.Value);
    }

    private void hopperTick() {
        if (this.world.isClient && this.isAlive() && this.getBlocked()) {
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
                BlockState blockState = blockItem.getBlock().getDefaultState();

                if (item == Items.REDSTONE ||
                        blockState.isToolRequired() && blockState.getHardness(world, getBlockPos()) >= CollectBlockWithHardness) {
                    HopperBlockEntity.extract(this, itemEntity);
                }
            } else {
                HopperBlockEntity.extract(this, itemEntity);
            }
        }

        return false;
    }

    public void kill() {
        super.kill();
        if (this.world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
            this.dropItem(Blocks.OBSERVER);
            this.dropItem(Blocks.REDSTONE_BLOCK);
            this.dropItem(Blocks.HOPPER);
        }
    }

    protected ExcavatorScreenHandler getScreenHandler(int id, PlayerInventory playerInventoryIn) {
        return new ExcavatorScreenHandler(id, playerInventoryIn, this);
    }
}
