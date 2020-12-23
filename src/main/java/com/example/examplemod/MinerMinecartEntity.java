package com.example.examplemod;

import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.server.SPlayerDiggingPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.ToolType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

public class MinerMinecartEntity extends AbstractMinecartEntity {

    private static final DataParameter<Boolean> POWERED = EntityDataManager.createKey(MinerMinecartEntity.class, DataSerializers.BOOLEAN);

    private final float maxPush = 0.005f;
    private final float maxSpeed = 0.01f;
    private final SimpleDigger digger = new SimpleDigger();

    private int fuel;
    public double pushX;
    public double pushZ;

    /** The fuel item used to make the minecart move. */
    private static final Ingredient BURNABLE_FUELS = Ingredient.fromItems(Items.COAL, Items.CHARCOAL);

    public MinerMinecartEntity(EntityType<? extends MinerMinecartEntity> furnaceCart, World world) {
        super(furnaceCart, world);
    }

    public MinerMinecartEntity(World worldIn, double x, double y, double z) {
        super(EntityType.MINECART, worldIn, x, y, z);
    }

    protected void registerData() {
        super.registerData();
        this.dataManager.register(POWERED, false);
    }

    /**
     * Called to update the entity's position/logic.
     */
    public void tick() {
        digger.tick();

        if(digger.IsPathClear){
            if(pushX ==  0.0D && pushZ == 0.0D){
                pushX = maxPush;
                pushZ = maxPush;
            }
        }else{
            setMotion(Vector3d.ZERO);
        }

        super.tick();
        if (!this.world.isRemote()) {
            if (this.fuel > 0) {
                --this.fuel;
            }

            if (this.fuel <= 0) {
                this.pushX = 0.0D;
                this.pushZ = 0.0D;
            }

            this.setMinecartPowered(this.fuel > 0);
        }

        if (this.isMinecartPowered() && this.rand.nextInt(4) == 0) {
            this.world.addParticle(ParticleTypes.LARGE_SMOKE, this.getPosX(), this.getPosY() + 0.8D, this.getPosZ(), 0.0D, 0.0D, 0.0D);
        }

    }

    protected double getMaximumSpeed() {
        return maxSpeed;
    }

    protected void moveAlongTrack(BlockPos pos, BlockState state) {
        super.moveAlongTrack(pos, state);
    }

    protected void applyDrag() {
        this.setMotion(this.getMotion().mul(0.8D, 0.0D, 0.8D).add(this.pushX, 0.0D, this.pushZ));

        super.applyDrag();
    }

    public ActionResultType processInitialInteract(PlayerEntity player, Hand hand) {
        ActionResultType ret = super.processInitialInteract(player, hand);
        if (ret.isSuccessOrConsume()) return ret;
        ItemStack itemstack = player.getHeldItem(hand);
        if (BURNABLE_FUELS.test(itemstack) && this.fuel + 100 <= 100) {
            if (!player.abilities.isCreativeMode) {
                itemstack.shrink(1);
            }

            this.fuel += 100;
        }

        if (this.fuel > 0) {
            this.pushX = Math.min(getPosX() - player.getPosX(), maxPush);
            this.pushZ = Math.min(getPosZ() - player.getPosZ(), maxPush);
        }

        return ActionResultType.func_233537_a_(this.world.isRemote);
    }

    protected void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        compound.putDouble("PushX", this.pushX);
        compound.putDouble("PushZ", this.pushZ);
        compound.putShort("Fuel", (short)this.fuel);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    protected void readAdditional(CompoundNBT compound) {
        super.readAdditional(compound);
        this.pushX = compound.getDouble("PushX");
        this.pushZ = compound.getDouble("PushZ");
        this.fuel = compound.getShort("Fuel");
    }

    protected boolean isMinecartPowered() {
        return this.dataManager.get(POWERED);
    }

    protected void setMinecartPowered(boolean powered) {
        this.dataManager.set(POWERED, powered);
    }

    public BlockState getDefaultDisplayTile() {
        return Blocks.FURNACE.getDefaultState().with(FurnaceBlock.FACING, Direction.SOUTH).with(FurnaceBlock.LIT, Boolean.valueOf(this.isMinecartPowered()));
    }

    @Override
    public Type getMinecartType() {
        return null;
    }

    private class SimpleDigger
    {
        final int DigDuration = 30;
        final int DigHeight = 3;

        private BlockPos diggingPos;
        private int digTick = 0;
        private int digHeightTick = 0;

        public boolean IsPathClear;

        public SimpleDigger()
        {
        }

        public void tick()
        {
            BlockPos harvestPos = GetFront();

            //stopped
            if(harvestPos == null)
            {
                ResetDig();
                IsPathClear = false;
                return;
            }

            IsPathClear = IsClear(harvestPos);

            //nothing to do
            if(IsPathClear) return;

            if(diggingPos == null) {
                StartDig(harvestPos);
                digHeightTick = 0;
            }else{
                if(DigTick())
                {
                    if(digHeightTick == 0){
                        CreateRail();

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

        private boolean IsClear(BlockPos harvestPos){
            for (int i = 0; i < DigHeight; i++) {
                if(!IsBlockClear(harvestPos)) return false;

                harvestPos = harvestPos.up();
            }

            return true;
        }

        private boolean IsBlockClear(BlockPos blockPos){
            BlockState blockState = world.getBlockState(blockPos);

            return blockState.isAir() || blockState.isIn(BlockTags.RAILS);
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
            BlockPos targetPos = getPosition();

            if(IsRailTrack(targetPos.east())){
                return targetPos.west();
            }else if(IsRailTrack(targetPos.west())){
                return targetPos.east();
            }else if(IsRailTrack(targetPos.north())){
                return targetPos.south();
            }else if(IsRailTrack(targetPos.south())){
                return targetPos.north();
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
                    fireFluidPlaceBlockEvent(world, diggingPos, diggingPos, Blocks.RAIL.getDefaultState()), 64); //lava has 3 for water
        }
    }


    private class Digger
    {
        private final Logger field_225418_c = LogManager.getLogger();
        private int ticks;
        private boolean receivedFinishDiggingPacket;
        private ServerWorld world;
        private BlockPos destroyPos;
        private BlockPos delayedDestroyPos;
        private boolean isDestroyingBlock;
        public ServerPlayerEntity player;
        private GameType gameType = GameType.SURVIVAL;
        private int initialDamage;
        private int initialBlockDamage;
        private int durabilityRemainingOnBlock;


        /**
         * Get if we are in creative game mode.
         */
        public boolean isCreative() {
            return gameType.isCreative();
        }

        public void tick() {
            ++this.ticks;
            if (this.receivedFinishDiggingPacket) {
                BlockState blockstate = this.world.getBlockState(this.delayedDestroyPos);
                if (blockstate.isAir(world, delayedDestroyPos)) {
                    this.receivedFinishDiggingPacket = false;
                } else {
                    float f = this.func_229859_a_(blockstate, this.delayedDestroyPos, this.initialBlockDamage);
                    if (f >= 1.0F) {
                        this.receivedFinishDiggingPacket = false;
                        this.tryHarvestBlock(this.delayedDestroyPos);
                    }
                }
            } else if (this.isDestroyingBlock) {
                BlockState blockstate1 = this.world.getBlockState(this.destroyPos);
                if (blockstate1.isAir(world, destroyPos)) {
                    this.world.sendBlockBreakProgress(this.player.getEntityId(), this.destroyPos, -1);
                    this.durabilityRemainingOnBlock = -1;
                    this.isDestroyingBlock = false;
                } else {
                    this.func_229859_a_(blockstate1, this.destroyPos, this.initialDamage);
                }
            }

        }

        private float func_229859_a_(BlockState p_229859_1_, BlockPos p_229859_2_, int p_229859_3_) {
            int i = this.ticks - p_229859_3_;
            float f = p_229859_1_.getPlayerRelativeBlockHardness(this.player, this.player.world, p_229859_2_) * (float)(i + 1);
            int j = (int)(f * 10.0F);
            if (j != this.durabilityRemainingOnBlock) {
                this.world.sendBlockBreakProgress(this.player.getEntityId(), p_229859_2_, j);
                this.durabilityRemainingOnBlock = j;
            }

            return f;
        }

        public void func_225416_a(BlockPos p_225416_1_, CPlayerDiggingPacket.Action p_225416_2_, Direction p_225416_3_, int p_225416_4_) {
            double d0 = this.player.getPosX() - ((double)p_225416_1_.getX() + 0.5D);
            double d1 = this.player.getPosY() - ((double)p_225416_1_.getY() + 0.5D) + 1.5D;
            double d2 = this.player.getPosZ() - ((double)p_225416_1_.getZ() + 0.5D);
            double d3 = d0 * d0 + d1 * d1 + d2 * d2;
            double dist = player.getAttribute(net.minecraftforge.common.ForgeMod.REACH_DISTANCE.get()).getValue() + 1;
            net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event = net.minecraftforge.common.ForgeHooks.onLeftClickBlock(player, p_225416_1_, p_225416_3_);
            if (event.isCanceled() || (!this.isCreative() && event.getUseItem() == net.minecraftforge.eventbus.api.Event.Result.DENY)) { // Restore block and te data
                player.connection.sendPacket(new SPlayerDiggingPacket(p_225416_1_, world.getBlockState(p_225416_1_), p_225416_2_, false, "mod canceled"));
                world.notifyBlockUpdate(p_225416_1_, world.getBlockState(p_225416_1_), world.getBlockState(p_225416_1_), 3);
                return;
            }
            dist *= dist;
            if (d3 > dist) {
                this.player.connection.sendPacket(new SPlayerDiggingPacket(p_225416_1_, this.world.getBlockState(p_225416_1_), p_225416_2_, false, "too far"));
            } else if (p_225416_1_.getY() >= p_225416_4_) {
                this.player.connection.sendPacket(new SPlayerDiggingPacket(p_225416_1_, this.world.getBlockState(p_225416_1_), p_225416_2_, false, "too high"));
            } else {
                if (p_225416_2_ == CPlayerDiggingPacket.Action.START_DESTROY_BLOCK) {
                    if (!this.world.isBlockModifiable(this.player, p_225416_1_)) {
                        this.player.connection.sendPacket(new SPlayerDiggingPacket(p_225416_1_, this.world.getBlockState(p_225416_1_), p_225416_2_, false, "may not interact"));
                        return;
                    }

                    if (this.isCreative()) {
                        this.func_229860_a_(p_225416_1_, p_225416_2_, "creative destroy");
                        return;
                    }

                    if (this.player.blockActionRestricted(this.world, p_225416_1_, this.gameType)) {
                        this.player.connection.sendPacket(new SPlayerDiggingPacket(p_225416_1_, this.world.getBlockState(p_225416_1_), p_225416_2_, false, "block action restricted"));
                        return;
                    }

                    this.initialDamage = this.ticks;
                    float f = 1.0F;
                    BlockState blockstate = this.world.getBlockState(p_225416_1_);
                    if (!blockstate.isAir(world, p_225416_1_)) {
                        if (event.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY)
                            blockstate.onBlockClicked(this.world, p_225416_1_, this.player);
                        f = blockstate.getPlayerRelativeBlockHardness(this.player, this.player.world, p_225416_1_);
                    }

                    if (!blockstate.isAir(world, p_225416_1_) && f >= 1.0F) {
                        this.func_229860_a_(p_225416_1_, p_225416_2_, "insta mine");
                    } else {
                        if (this.isDestroyingBlock) {
                            this.player.connection.sendPacket(new SPlayerDiggingPacket(this.destroyPos, this.world.getBlockState(this.destroyPos), CPlayerDiggingPacket.Action.START_DESTROY_BLOCK, false, "abort destroying since another started (client insta mine, server disagreed)"));
                        }

                        this.isDestroyingBlock = true;
                        this.destroyPos = p_225416_1_.toImmutable();
                        int i = (int)(f * 10.0F);
                        this.world.sendBlockBreakProgress(this.player.getEntityId(), p_225416_1_, i);
                        this.player.connection.sendPacket(new SPlayerDiggingPacket(p_225416_1_, this.world.getBlockState(p_225416_1_), p_225416_2_, true, "actual start of destroying"));
                        this.durabilityRemainingOnBlock = i;
                    }
                } else if (p_225416_2_ == CPlayerDiggingPacket.Action.STOP_DESTROY_BLOCK) {
                    if (p_225416_1_.equals(this.destroyPos)) {
                        int j = this.ticks - this.initialDamage;
                        BlockState blockstate1 = this.world.getBlockState(p_225416_1_);
                        if (!blockstate1.isAir()) {
                            float f1 = blockstate1.getPlayerRelativeBlockHardness(this.player, this.player.world, p_225416_1_) * (float)(j + 1);
                            if (f1 >= 0.7F) {
                                this.isDestroyingBlock = false;
                                this.world.sendBlockBreakProgress(this.player.getEntityId(), p_225416_1_, -1);
                                this.func_229860_a_(p_225416_1_, p_225416_2_, "destroyed");
                                return;
                            }

                            if (!this.receivedFinishDiggingPacket) {
                                this.isDestroyingBlock = false;
                                this.receivedFinishDiggingPacket = true;
                                this.delayedDestroyPos = p_225416_1_;
                                this.initialBlockDamage = this.initialDamage;
                            }
                        }
                    }

                    this.player.connection.sendPacket(new SPlayerDiggingPacket(p_225416_1_, this.world.getBlockState(p_225416_1_), p_225416_2_, true, "stopped destroying"));
                } else if (p_225416_2_ == CPlayerDiggingPacket.Action.ABORT_DESTROY_BLOCK) {
                    this.isDestroyingBlock = false;
                    if (!Objects.equals(this.destroyPos, p_225416_1_)) {
                        field_225418_c.warn("Mismatch in destroy block pos: " + this.destroyPos + " " + p_225416_1_);
                        this.world.sendBlockBreakProgress(this.player.getEntityId(), this.destroyPos, -1);
                        this.player.connection.sendPacket(new SPlayerDiggingPacket(this.destroyPos, this.world.getBlockState(this.destroyPos), p_225416_2_, true, "aborted mismatched destroying"));
                    }

                    this.world.sendBlockBreakProgress(this.player.getEntityId(), p_225416_1_, -1);
                    this.player.connection.sendPacket(new SPlayerDiggingPacket(p_225416_1_, this.world.getBlockState(p_225416_1_), p_225416_2_, true, "aborted destroying"));
                }

            }
        }

        public void func_229860_a_(BlockPos p_229860_1_, CPlayerDiggingPacket.Action p_229860_2_, String p_229860_3_) {
            if (this.tryHarvestBlock(p_229860_1_)) {
                this.player.connection.sendPacket(new SPlayerDiggingPacket(p_229860_1_, this.world.getBlockState(p_229860_1_), p_229860_2_, true, p_229860_3_));
            } else {
                this.player.connection.sendPacket(new SPlayerDiggingPacket(p_229860_1_, this.world.getBlockState(p_229860_1_), p_229860_2_, false, p_229860_3_));
            }

        }

        /**
         * Attempts to harvest a block
         */
        public boolean tryHarvestBlock(BlockPos pos) {
            BlockState blockstate = this.world.getBlockState(pos);
            int exp = net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(world, gameType, player, pos);
            if (exp == -1) {
                return false;
            } else {
                TileEntity tileentity = this.world.getTileEntity(pos);
                Block block = blockstate.getBlock();
                if ((block instanceof CommandBlockBlock || block instanceof StructureBlock || block instanceof JigsawBlock) && !this.player.canUseCommandBlock()) {
                    this.world.notifyBlockUpdate(pos, blockstate, blockstate, 3);
                    return false;
                } else if (player.getHeldItemMainhand().onBlockStartBreak(pos, player)) {
                    return false;
                } else if (this.player.blockActionRestricted(this.world, pos, this.gameType)) {
                    return false;
                } else {
                    if (this.isCreative()) {
                        removeBlock(pos, false);
                        return true;
                    } else {
                        ItemStack itemstack = this.player.getHeldItemMainhand();
                        ItemStack itemstack1 = itemstack.copy();
                        boolean flag1 = blockstate.canHarvestBlock(this.world, pos, this.player); // previously player.func_234569_d_(blockstate)
                        itemstack.onBlockDestroyed(this.world, blockstate, pos, this.player);
                        if (itemstack.isEmpty() && !itemstack1.isEmpty())
                            net.minecraftforge.event.ForgeEventFactory.onPlayerDestroyItem(this.player, itemstack1, Hand.MAIN_HAND);
                        boolean flag = removeBlock(pos, flag1);

                        if (flag && flag1) {
                            block.harvestBlock(this.world, this.player, pos, blockstate, tileentity, itemstack1);
                        }

                        if (flag && exp > 0)
                            blockstate.getBlock().dropXpOnBlockBreak(world, pos, exp);

                        return true;
                    }
                }
            }
        }

        private boolean removeBlock(BlockPos p_180235_1_, boolean canHarvest) {
            BlockState state = this.world.getBlockState(p_180235_1_);
            boolean removed = state.removedByPlayer(this.world, p_180235_1_, this.player, canHarvest, this.world.getFluidState(p_180235_1_));
            if (removed)
                state.getBlock().onPlayerDestroy(this.world, p_180235_1_, state);
            return removed;
        }
    }
}
