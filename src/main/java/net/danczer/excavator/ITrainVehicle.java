package net.danczer.excavator;

import net.minecraft.util.math.Vec3d;

public interface ITrainVehicle {
    MinecartTrain getTrain();
    void setTrain(MinecartTrain train);
    Vec3d getVehicleVelocity();
    void setVehicleVelocity(Vec3d velocity);
    Vec3d getVehiclePos();
}
