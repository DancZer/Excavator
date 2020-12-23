package com.example.examplemod;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.entity.item.minecart.HopperMinecartEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

public class MinerMinecartEntity extends HopperMinecartEntity {

    private static final double PushForce = 0.1;

    private final SimpleMinerMachine minerMachine = new SimpleMinerMachine(this, Blocks.POWERED_RAIL, Blocks.REDSTONE_WALL_TORCH);
    private boolean isPushedAfterClear;

    public MinerMinecartEntity(EntityType<? extends MinerMinecartEntity> furnaceCart, World world) {
        super(furnaceCart, world);
    }

    public MinerMinecartEntity(World worldIn, double x, double y, double z) {
        super(worldIn, x, y, z);
    }

    public int getSizeInventory() {
        return 5;
    }

    public void readAdditional(CompoundNBT compound) {
        super.readAdditional(compound);
        minerMachine.readAdditional(compound);
    }

    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        minerMachine.writeAdditional(compound);
        isPushedAfterClear = false;
    }

    public void tick() {
        minerMachine.tick();

        if(minerMachine.IsPathClear){
            if(!isPushedAfterClear){
                isPushedAfterClear = true;
                //push it a bit to the direction
                setMotion(minerMachine.getDirectoryVector().scale(PushForce));
            }
        }else{
            isPushedAfterClear = false;
            setMotion(Vector3d.ZERO);

            if (this.rand.nextInt(4) == 0) {
                this.world.addParticle(ParticleTypes.LARGE_SMOKE, this.getPosX(), this.getPosY() + 0.8D, this.getPosZ(), 0.0D, 0.0D, 0.0D);
            }
        }

        super.tick();
    }

    @Override
    public Type getMinecartType() {
        return null;
    }
}
