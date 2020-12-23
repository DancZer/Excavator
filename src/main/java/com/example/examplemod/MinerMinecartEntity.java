package com.example.examplemod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;

public class MinerMinecartEntity extends AbstractMinecartEntity {

    private final SimpleDigger digger = new SimpleDigger(Blocks.POWERED_RAIL, Blocks.REDSTONE_WALL_TORCH);

    public MinerMinecartEntity(EntityType<? extends MinerMinecartEntity> furnaceCart, World world) {
        super(furnaceCart, world);
    }

    public MinerMinecartEntity(World worldIn, double x, double y, double z) {
        super(EntityType.MINECART, worldIn, x, y, z);

    }

    private final double PushForce = 0.1D;
    private boolean isPushedAfterClear;

    public void tick() {
        digger.tick();

        if(digger.IsPathClear){
            if(!isPushedAfterClear){
                isPushedAfterClear = true;
                //push it a bit to the direction
                setMotion(digger.GetDirVector().scale(PushForce));
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

    private class SimpleDigger
    {
        private final int DigDuration = 30;
        private final int DigHeight = 3;
        private final int TorchPlacementDistance = 6;
        private final Block railType;
        private final Block torchType;

        private BlockPos lastTorchPos;
        private BlockPos diggingPos;
        private int digTick = 0;
        private int digHeightTick = 0;

        public boolean IsPathClear;

        private Direction previousDir;

        public SimpleDigger(Block railType, Block torchType)
        {
            this.railType = railType;
            this.torchType = torchType;
        }

        public Vector3d GetDirVector()
        {
            if(previousDir == null) return Vector3d.ZERO;

            switch (previousDir){
                case NORTH:
                case SOUTH:
                case WEST:
                case EAST:
                    return new Vector3d(previousDir.toVector3f()).normalize();
                case DOWN:
                case UP:
                default:
                    return Vector3d.ZERO;
            }

        }

        public void tick()
        {
            BlockPos frontPos = GetFront();

            //stopped
            if(frontPos == null)
            {
                ResetDig();
                IsPathClear = false;
            }else{
                IsPathClear = IsClear(frontPos);

                //nothing to do
                if(IsPathClear) {
                    ResetDig();
                }else{
                    if(diggingPos == null) {
                        StartDig(frontPos);
                        digHeightTick = 0;
                    }else{
                        if(DigTick())
                        {
                            if(digHeightTick == 0){
                                CreateRail();
                            }else if(digHeightTick == 1){
                                CreateTorch();
                            }

                            digHeightTick++;

                            if(digHeightTick <= DigHeight){
                                StartDig(diggingPos.up());
                            }else{
                                ResetDig();
                            }
                        }
                    }
                }
            }
        }

        private boolean IsClear(BlockPos harvestPos){
            for (int i = 0; i < DigHeight; i++) {
                if(!IsBlockClear(harvestPos)) return false;

                harvestPos = harvestPos.up();
            }

            return true;
        }

        private boolean IsBlockClear(BlockPos blockPos){
            BlockState blockState = world.getBlockState(blockPos);

            return blockState.isAir() || blockState.isIn(BlockTags.RAILS) || blockState.isIn(Blocks.WALL_TORCH) || blockState.isIn(Blocks.REDSTONE_WALL_TORCH);
        }

        private void StartDig(BlockPos blockPos)
        {
            diggingPos = blockPos;
            digTick = 0;
        }

        private void ResetDig(){
            diggingPos = null;
            digTick = 0;
            digHeightTick = 0;
        }

        //returns true if destroyed
        private boolean DigTick()
        {
            if(IsBlockClear(diggingPos)) return true;

            BlockState blockState = world.getBlockState(diggingPos);

            if(blockState.isToolEffective(ToolType.PICKAXE) || blockState.isToolEffective(ToolType.SHOVEL))
            {
                digTick++;

                if(digTick > DigDuration){
                    world.removeBlock(diggingPos, true);
                    return true;
                }else{
                    return false;
                }
            }else{
                return false;
            }
        }

        private BlockPos GetFront()
        {
            BlockPos pos = getPosition();

            BlockPos foundFront = GetFrontFromMotionInner(pos);

            if(foundFront == null){
                foundFront = GetFrontFromRailsInner(pos);
            }

            return foundFront;
        }

        private BlockPos GetFrontFromRailsInner(BlockPos position)
        {
            if(IsRailTrack(position.east())){
                return position.west();
            }else if(IsRailTrack(position.west())){
                return position.east();
            }else if(IsRailTrack(position.north())){
                return position.south();
            }else if(IsRailTrack(position.south())){
                return position.north();
            }

            return null;
        }

        private BlockPos GetFrontFromMotionInner(BlockPos position)
        {
            Direction dir = GetMovingDirection();

            if(dir == null){
                dir = previousDir;
            }

            if(dir == null) return null;

            previousDir = dir;

            Direction oppositeDir = dir.getOpposite();

            if(IsRailTrack(position.offset(oppositeDir))){
                return position.offset(dir);
            }

            return null;
        }

        private Direction GetMovingDirection(){
            Vector3d motion = getMotion();

            if(motion.lengthSquared() > 0.01){
                return Direction.getFacingFromVector(motion.x, motion.y, motion.z);
            }

            return null;
        }

        private boolean IsRailTrack(BlockPos targetPos)
        {
            return world.getBlockState(targetPos).isIn(BlockTags.RAILS);
        }

        private void CreateRail()
        {
            if(IsRailTrack(diggingPos)) return;

            world.setBlockState(diggingPos, net.minecraftforge.event.ForgeEventFactory.
                    fireFluidPlaceBlockEvent(world, diggingPos, diggingPos, railType.getDefaultState()), 64); //lava has 3 for water
        }

        private void CreateTorch()
        {
            if(world.getBlockState(diggingPos).isIn(Blocks.TORCH)) return;
            if(lastTorchPos != null && lastTorchPos.withinDistance(new Vector3d(diggingPos.getX(), diggingPos.getY(), diggingPos.getZ()), TorchPlacementDistance)) return;
            if(previousDir == null) return;

            world.setBlockState(diggingPos, net.minecraftforge.event.ForgeEventFactory.
                    fireFluidPlaceBlockEvent(world, diggingPos, diggingPos, torchType.getDefaultState().rotate(world, diggingPos, GetTorchRotation())), 64); //lava has 3 for water

            lastTorchPos = diggingPos;
        }

        private Rotation GetTorchRotation()
        {
            switch (previousDir)
            {
                case NORTH:
                    return Rotation.CLOCKWISE_90;
                case SOUTH:
                    return Rotation.COUNTERCLOCKWISE_90;
                case WEST:
                    return Rotation.NONE;
                case EAST:
                    return Rotation.CLOCKWISE_180;
                case DOWN:
                case UP:
                default:
                    return Rotation.NONE;
            }
        }
    }
}
