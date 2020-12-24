package com.danczer.excavator;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.minecart.HopperMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExcavatorEntity extends HopperMinecartEntity {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final int InventorySize = 5;
    private static final double PushForce = 0.1;

    private final MinerLogic minerLogic = new MinerLogic(this, Blocks.RAIL, Blocks.WALL_TORCH);
    //private final MinerLogic minerLogic = new MinerLogic(this, Blocks.POWERED_RAIL, Blocks.REDSTONE_WALL_TORCH);
    private int prevMinedBlockCount;

    public ExcavatorEntity(EntityType<? extends ExcavatorEntity> furnaceCart, World world) {
        super(furnaceCart, world);
    }

    public ExcavatorEntity(World worldIn, double x, double y, double z) {
        super(worldIn, x, y, z);
    }

    public int getSizeInventory() {
        return InventorySize;
    }

    public boolean isInventoryFull() {
        if(isEmpty()){
            return false;
        }

        for (int i = 0; i < InventorySize; i++) {
            ItemStack itemStack = getStackInSlot(i);

            if(!itemStack.isEmpty() && itemStack.getCount() < itemStack.getMaxStackSize()) return false;
        }

        return true;
    }

    public void readAdditional(CompoundNBT compound) {
        super.readAdditional(compound);
        minerLogic.readAdditional(compound);
        prevMinedBlockCount = compound.getInt("prevMinedBlockCount");
    }

    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        minerLogic.writeAdditional(compound);
        compound.putInt("prevMinedBlockCount", prevMinedBlockCount);
    }

    public void tick() {

        boolean isFull = isInventoryFull();

        LOGGER.debug("IsFull: "+isFull);

        if(!isFull){
            boolean ok = minerLogic.tick();

            LOGGER.debug("Logic Is Ok: "+ok);
            LOGGER.debug("Logic IsPathClear: "+minerLogic.IsPathClear);

            if(ok){
                if(minerLogic.IsPathClear){
                    if(prevMinedBlockCount != minerLogic.getMinedBlockCount()){
                        prevMinedBlockCount = minerLogic.getMinedBlockCount();
                        //push it a bit to the direction
                        setMotion(minerLogic.getDirectoryVector().scale(PushForce));
                        LOGGER.debug("Logic setMotion: Push");
                    }else{
                        LOGGER.debug("Logic setMotion: leave");
                    }
                }else{
                    LOGGER.debug("Logic setMotion: ZERO");
                    setMotion(Vector3d.ZERO);
                }
            }else{
                LOGGER.debug("Logic setMotion: leave");
            }

            if (rand.nextInt(2) == 0) {
                world.addParticle(ParticleTypes.LARGE_SMOKE, this.getPosX(), this.getPosY() + 0.8D, this.getPosZ(), 0.0D, -0.1D, 0.0D);
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

    @Override
    public Type getMinecartType() {
        return null;
    }
}
