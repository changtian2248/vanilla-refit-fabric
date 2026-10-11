package com.btsdhz.vanillarefit.entity;

import com.btsdhz.vanillarefit.ModEntities;
import com.btsdhz.vanillarefit.util.FenceRopeLogic;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 位于一根栅栏上的绕绳结,可同时挂多条拴绳(每个伙伴栅栏一条)。
 */
public class FenceKnotEntity extends LeashFenceKnotEntity {
    private static final EntityDataAccessor<CompoundTag> DATA_PARTNERS =
            SynchedEntityData.defineId(FenceKnotEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Optional<UUID>> DATA_PENDING_PLAYER =
            SynchedEntityData.defineId(FenceKnotEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private CompoundTag cachedPartnersTag;
    private Map<BlockPos, Boolean> cachedPartners = Map.of();

    public FenceKnotEntity(EntityType<? extends FenceKnotEntity> entityType, Level level) {
        super(entityType, level);
    }

    public FenceKnotEntity(Level level, BlockPos pos) {
        this(ModEntities.FENCE_KNOT, level);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PARTNERS, serializePartners(Map.of()));
        builder.define(DATA_PENDING_PLAYER, Optional.empty());
    }

    // ====== 伙伴连接(每条记录伙伴栅栏 -> 是否消耗过拴绳) ======

    public Map<BlockPos, Boolean> getPartners() {
        CompoundTag tag = this.getEntityData().get(DATA_PARTNERS);
        if (tag != this.cachedPartnersTag) {
            this.cachedPartnersTag = tag;
            this.cachedPartners = deserializePartners(tag);
        }
        return this.cachedPartners;
    }

    public boolean hasPartners() {
        return !this.getPartners().isEmpty();
    }

    public void setPartner(BlockPos partner, boolean consumed) {
        Map<BlockPos, Boolean> partners = new LinkedHashMap<>(this.getPartners());
        partners.put(partner, consumed);
        this.getEntityData().set(DATA_PARTNERS, serializePartners(partners));
    }

    public boolean removePartner(BlockPos partner) {
        Map<BlockPos, Boolean> partners = new LinkedHashMap<>(this.getPartners());
        if (partners.remove(partner) == null) {
            return false;
        }
        this.getEntityData().set(DATA_PARTNERS, serializePartners(partners));
        return true;
    }

    private void clearPartners() {
        this.getEntityData().set(DATA_PARTNERS, serializePartners(Map.of()));
    }

    // ====== 待连接玩家(用于客户端渲染“挂在手上”的拴绳) ======

    public Optional<UUID> getPendingPlayerOpt() {
        return this.getEntityData().get(DATA_PENDING_PLAYER);
    }

    public UUID getPendingPlayer() {
        return this.getEntityData().get(DATA_PENDING_PLAYER).orElse(null);
    }

    public void setPendingPlayer(UUID uuid) {
        this.getEntityData().set(DATA_PENDING_PLAYER, Optional.ofNullable(uuid));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) {
            return false;
        }
        this.cancelAll();
        return true;
    }

    // ====== 逻辑 ======

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        BlockPos selfPos = this.getPos();
        if (!isFence(selfPos)) {
            UUID pending = this.getPendingPlayer();
            if (pending != null) {
                for (Player player : this.level().players()) {
                    if (player.getUUID().equals(pending)) {
                        FenceRopeLogic.clearPending(player);
                        break;
                    }
                }
            }
            this.clearPartners();
            this.discard();
            return;
        }
        for (Map.Entry<BlockPos, Boolean> entry : new ArrayList<>(this.getPartners().entrySet())) {
            BlockPos partner = entry.getKey();
            if (!isFence(partner)) {
                this.removePartner(partner);
                FenceKnotEntity partnerKnot = FenceRopeLogic.findKnot(this.level(), partner);
                if (partnerKnot != null) {
                    partnerKnot.removePartner(selfPos);
                }
                if (entry.getValue()) {
                    this.dropLead();
                }
            }
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 65536.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 self = this.getRopeHoldPosition(0.0F);
        double minX = self.x, minY = self.y, minZ = self.z;
        double maxX = self.x, maxY = self.y, maxZ = self.z;
        for (BlockPos partner : this.getPartners().keySet()) {
            double px = partner.getX() + 0.5;
            double py = partner.getY() + 0.575;
            double pz = partner.getZ() + 0.5;
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
            maxZ = Math.max(maxZ, pz);
        }
        UUID pending = this.getPendingPlayer();
        if (pending != null) {
            for (Player player : this.level().players()) {
                if (player.getUUID().equals(pending)) {
                    Vec3 hand = player.getRopeHoldPosition(0.0F);
                    minX = Math.min(minX, hand.x);
                    minY = Math.min(minY, hand.y);
                    minZ = Math.min(minZ, hand.z);
                    maxX = Math.max(maxX, hand.x);
                    maxY = Math.max(maxY, hand.y);
                    maxZ = Math.max(maxZ, hand.z);
                    break;
                }
            }
        }
        return new AABB(minX - 0.5, minY - 0.5, minZ - 0.5, maxX + 0.5, maxY + 0.5, maxZ + 0.5);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (FenceRopeLogic.hasPlayerLeashedMobs(player, this.level(), this.getPos())) {
            return super.interact(player, hand);
        }

        BlockPos pending = FenceRopeLogic.getPending(player);
        if (pending != null) {
            if (pending.equals(this.getPos())) {
                FenceRopeLogic.clearPending(player);
                this.setPendingPlayer(null);
                if (!this.hasPartners()) {
                    this.discard();
                }
                return InteractionResult.CONSUME;
            }
            boolean creative = player.getAbilities().instabuild;
            FenceRopeLogic.connect(this.level(), pending, this.getPos(), !creative);
            FenceRopeLogic.clearPending(player);
            if (!creative && player.getItemInHand(hand).is(Items.LEAD)) {
                player.getItemInHand(hand).shrink(1);
            }
            this.level().playSound(null, this.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }

        this.cancelAll();
        return InteractionResult.CONSUME;
    }

    private void cancelAll() {
        if (this.level().isClientSide) {
            return;
        }
        for (Map.Entry<BlockPos, Boolean> entry : new ArrayList<>(this.getPartners().entrySet())) {
            BlockPos partner = entry.getKey();
            boolean consumed = entry.getValue();
            FenceKnotEntity partnerKnot = FenceRopeLogic.findKnot(this.level(), partner);
            if (partnerKnot != null) {
                partnerKnot.removePartner(this.getPos());
                if (!partnerKnot.hasPartners() && partnerKnot.getPendingPlayer() == null) {
                    partnerKnot.discard();
                }
            }
            if (consumed) {
                this.dropLead();
            }
        }
        this.clearPartners();
        this.discard();
    }

    private void dropLead() {
        BlockPos p = this.getPos();
        ItemEntity lead = new ItemEntity(this.level(), p.getX() + 0.5, p.getY() + 0.45, p.getZ() + 0.25, new ItemStack(Items.LEAD));
        lead.setDefaultPickUpDelay();
        this.level().addFreshEntity(lead);
    }

    private boolean isFence(BlockPos pos) {
        return pos != null && this.level().getBlockState(pos).is(BlockTags.FENCES);
    }

    // ====== 持久化与同步 ======

    private static CompoundTag serializePartners(Map<BlockPos, Boolean> partners) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Boolean> entry : partners.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("P", entry.getKey().asLong());
            c.putBoolean("C", entry.getValue());
            list.add(c);
        }
        tag.put("L", list);
        return tag;
    }

    private static Map<BlockPos, Boolean> deserializePartners(CompoundTag tag) {
        Map<BlockPos, Boolean> partners = new LinkedHashMap<>();
        ListTag list = tag.getList("L", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            partners.put(BlockPos.of(c.getLong("P")), c.getBoolean("C"));
        }
        return partners;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.putLong("AttachPos", this.getPos().asLong());
        compound.put("Partners", serializePartners(this.getPartners()));
        compound.putString("PendingPlayer", this.getPendingPlayerOpt().map(UUID::toString).orElse(""));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        BlockPos p = BlockPos.of(compound.getLong("AttachPos"));
        this.setPos(p.getX(), p.getY(), p.getZ());
        this.getEntityData().set(DATA_PARTNERS, compound.getCompound("Partners"));
        String pending = compound.getString("PendingPlayer");
        this.getEntityData().set(DATA_PENDING_PLAYER, readPendingPlayer(pending));
    }

    private static Optional<UUID> readPendingPlayer(String pending) {
        if (pending == null || pending.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(pending));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.LEAD);
    }
}
