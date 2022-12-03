package net.danczer.excavator;

import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;

public class MinecartTrain{
    private final ArrayList<ITrainVehicle> trainVehicles = new ArrayList<>();

    public MinecartTrain(ITrainVehicle vehicle) {
        trainVehicles.add(vehicle);
    }

    public void updateVelocity(){
        if(trainVehicles.size() <=1) return;

        Vec3d[] coupleVelocity = new Vec3d[trainVehicles.size()-1];

        for (int i = 0; i < coupleVelocity.length; i++) {
            var vehicleA = trainVehicles.get(i).getVehicleVelocity();
            var vehicleB = trainVehicles.get(i+1).getVehicleVelocity();
            coupleVelocity[i] = vehicleA.add(vehicleB).multiply(0.5);
        }

        Vec3d[] trainVelocity = new Vec3d[trainVehicles.size()];

        for (int i = 0; i < coupleVelocity.length; i++) {
            if(i == 0){
                trainVelocity[i] = coupleVelocity[i];
            }else{
                trainVelocity[i] = trainVelocity[i].add(coupleVelocity[i]).multiply(0.5);
            }

            if(i == coupleVelocity.length-1){
                trainVelocity[i+1] = coupleVelocity[i];
            }else{
                trainVelocity[i+1] = trainVelocity[i+1].add(coupleVelocity[i]).multiply(0.5);
            }
        }

        for (int i = 0; i < trainVehicles.size()-1; i++) {
            var minecart = trainVehicles.get(i);

            Vec3d dir = minecart.getVehicleVelocity().normalize();
            minecart.setVehicleVelocity(dir.multiply(trainVelocity[i]));
        }
    }

    public int size(){
        return trainVehicles.size();
    }

    public void merge(ArrayList<ITrainVehicle> vehicles){
        for (var vehicle : vehicles) {
            var fistDist = distanceAlongTheRail(vehicle, trainVehicles.get(0));
            var lastDist = distanceAlongTheRail(vehicle, trainVehicles.get(trainVehicles.size()-1));

            if(fistDist < lastDist){
                trainVehicles.add(0, vehicle);
            }else{
                trainVehicles.add(vehicle);
            }
        }
    }

    private double distanceAlongTheRail(ITrainVehicle vehicleA, ITrainVehicle vehicleB) {
        var diff = vehicleA.getVehiclePos().subtract(vehicleB.getVehiclePos());

        return Math.abs(diff.x) + Math.abs(diff.y) + Math.abs(diff.z);
    }

    public ArrayList<ITrainVehicle> vehicles(){
        return trainVehicles;
    }
}
