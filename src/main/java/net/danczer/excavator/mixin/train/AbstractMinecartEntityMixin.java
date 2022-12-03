package net.danczer.excavator.mixin.train;

import net.danczer.excavator.ITrainVehicle;
import net.danczer.excavator.MinecartTrain;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractMinecartEntity.class)
public class AbstractMinecartEntityMixin implements ITrainVehicle {
    private MinecartTrain minecartTrain;
    protected void moveAsTrain(){
        checkTrainCollision();

        getTrain().updateVelocity();
    }

    private void checkTrainCollision(){
        var this1 = This();
        List<Entity> list = this1.world.getOtherEntities(this1, this1.getBoundingBox().expand(0.2, 0.0, 0.2), EntityPredicates.canBePushedBy(this1));
        if (!list.isEmpty()) {
            for (var entity: list) {
                if(entity instanceof ITrainVehicle vehicle){
                    //TODO check that the vehicle is along the rail
                    connectTrains(this, vehicle);
                }
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "moveOnRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V")
    private void moveOnRail(CallbackInfo info) {
        moveAsTrain();
    }

    private static void connectTrains(ITrainVehicle vehicleA, ITrainVehicle vehicleB){
        var trainA = vehicleA.getTrain();
        var trainB = vehicleB.getTrain();

        //already connected
        if(trainA == trainB) return;

        trainA.merge(trainB.vehicles());
        vehicleB.setTrain(trainA);
    }

    @Override
    public MinecartTrain getTrain() {
        if(minecartTrain == null){
            minecartTrain = new MinecartTrain(this);
        }

        return minecartTrain;
    }

    @Override
    public void setTrain(MinecartTrain train) {
        minecartTrain = train;
    }

    @Override
    public Vec3d getVehicleVelocity() {
        return This().getVelocity();
    }

    @Override
    public void setVehicleVelocity(Vec3d velocity) {
        This().setVelocity(velocity);
    }

    @Override
    public Vec3d getVehiclePos() {
        return This().getPos();
    }

    private AbstractMinecartEntity This(){
        return ((AbstractMinecartEntity)(Object)this);
    }
}