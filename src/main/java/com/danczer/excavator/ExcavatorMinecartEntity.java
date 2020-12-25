package com.danczer.excavator;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.entity.item.minecart.HopperMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExcavatorMinecartEntity extends HopperMinecartEntity {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final int InventorySize = 5;
    private static final double PushForce = 0.1;

    private final ExcavatorMinecartLogic excavatorMinecartLogic = new ExcavatorMinecartLogic(this, Blocks.RAIL, Blocks.WALL_TORCH);
    //private final MinerLogic minerLogic = new MinerLogic(this, Blocks.POWERED_RAIL, Blocks.REDSTONE_WALL_TORCH);

    private int prevMinedBlockCount;

    public ExcavatorMinecartEntity(FMLPlayMessages.SpawnEntity packet, World worldIn){
        super(ExcavatorMod.EXCAVATOR, worldIn);
    }

    public ExcavatorMinecartEntity(EntityType<? extends ExcavatorMinecartEntity> type, World worldIn) {
        super(type, worldIn);
    }

    public ExcavatorMinecartEntity(World worldIn, double x, double y, double z) {
        super(ExcavatorMod.EXCAVATOR, worldIn);
        preventEntitySpawning = false;

        this.setPosition(x, y, z);
        this.setMotion(Vector3d.ZERO);
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
    }

    public int getSizeInventory() {
        return InventorySize;
    }

    public boolean isInventoryFull() {
        if (isEmpty()) {
            return false;
        }

        for (int i = 0; i < InventorySize; i++) {
            ItemStack itemStack = getStackInSlot(i);

            if (!itemStack.isEmpty() && itemStack.getCount() < itemStack.getMaxStackSize()) return false;
        }

        return true;
    }

    public void readAdditional(CompoundNBT compound) {
        super.readAdditional(compound);
        excavatorMinecartLogic.readAdditional(compound);
    }

    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        excavatorMinecartLogic.writeAdditional(compound);
    }

    @Override
    public IPacket<?> createSpawnPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public void tick() {
        if (!this.world.isRemote && this.isAlive() && this.getBlocked()) {
            boolean isFull = isInventoryFull();

            LOGGER.debug("IsFull: " + isFull);

            if (!isFull) {
                boolean ok = excavatorMinecartLogic.tick();

                LOGGER.debug("Logic Is Ok: " + ok);
                LOGGER.debug("Logic IsPathClear: " + excavatorMinecartLogic.IsPathClear);

                if (ok) {
                    if (excavatorMinecartLogic.IsPathClear) {
                        if (prevMinedBlockCount != excavatorMinecartLogic.getMinedBlockCount()) {
                            prevMinedBlockCount = excavatorMinecartLogic.getMinedBlockCount();
                            //push it a bit to the direction
                            setMotion(excavatorMinecartLogic.getDirectoryVector().scale(PushForce));
                            LOGGER.debug("Logic setMotion: Push");
                        } else {
                            LOGGER.debug("Logic setMotion: leave");
                        }
                    } else {
                        LOGGER.debug("Logic setMotion: ZERO");
                        setMotion(Vector3d.ZERO);

                        if (rand.nextInt(4) == 0) {
                            this.world.addParticle(ParticleTypes.LARGE_SMOKE, this.getPosX(), this.getPosY() + 0.8D, this.getPosZ(), 0.0D, 0.0D, 0.0D);
                            LOGGER.debug("addParticle: LARGE_SMOKE");
                        }
                    }
                } else {
                    LOGGER.debug("Logic setMotion: leave");
                }
            }
        }

        super.tick();
    }

    public void killMinecart(DamageSource source) {
        super.killMinecart(source);
        if (this.world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
            this.entityDropItem(Blocks.OBSERVER);
            this.entityDropItem(Blocks.REDSTONE_BLOCK);
        }

    }
}
