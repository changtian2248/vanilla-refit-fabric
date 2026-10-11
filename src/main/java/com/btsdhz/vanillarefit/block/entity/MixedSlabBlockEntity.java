package com.btsdhz.vanillarefit.block.entity;

import com.btsdhz.vanillarefit.ModBlockEntities;
import com.btsdhz.vanillarefit.client.MixedSlabClientRefresh;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachmentBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 混合半砖方块实体：一个格子里放两块“不同材质”的半砖。
 *
 * <p>渲染数据通过 Fabric 的 {@link RenderAttachmentBlockEntity} 暴露给模型；
 * 对应 NeoForge 原版里向模型传数据用的 {@code ModelProperty} / {@code ModelData}。
 */
public class MixedSlabBlockEntity extends BlockEntity implements RenderAttachmentBlockEntity {

    private Block slabA = Blocks.STONE_SLAB;
    private Block slabB = Blocks.STONE_SLAB;

    public MixedSlabBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** BlockEntityType.Builder.of 需要的工厂方法。 */
    public static MixedSlabBlockEntity create(BlockPos pos, BlockState state) {
        return new MixedSlabBlockEntity(ModBlockEntities.MIXED_SLAB, pos, state);
    }

    /** 传给客户端模型的渲染数据：[第一块材质, 第二块材质]。 */
    @Override
    public Object getRenderAttachmentData() {
        return new Block[]{slabA, slabB};
    }

    public Block getSlabA() {
        return slabA;
    }

    public Block getSlabB() {
        return slabB;
    }

    /** 设置两块半砖材质，并用“合并上半/北半”判断哪块是 A。 */
    public boolean setBlocks(Block slabA, Block slabB) {
        if (!isUsableSlab(slabA) || !isUsableSlab(slabB)) {
            return false;
        }
        this.slabA = slabA;
        this.slabB = slabB;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        refreshClientModel();
        return true;
    }

    /**
     * 材质变了以后刷新客户端渲染：标脏所在区块，让渲染器用新材质重画。
     */
    private void refreshClientModel() {
        if (level != null && level.isClientSide) {
            MixedSlabClientRefresh.section(level, worldPosition);
        }
    }

    public Block getFirstSlab() {
        return slabA;
    }

    public Block getSecondSlab() {
        return slabB;
    }

    private static boolean isUsableSlab(Block block) {
        return block instanceof SlabBlock && block != Blocks.AIR;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.slabA = readSlab(tag.getString("slab_a"));
        this.slabB = readSlab(tag.getString("slab_b"));
        refreshClientModel();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("slab_a", BuiltInRegistries.BLOCK.getKey(this.slabA).toString());
        tag.putString("slab_b", BuiltInRegistries.BLOCK.getKey(this.slabB).toString());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("slab_a", BuiltInRegistries.BLOCK.getKey(this.slabA).toString());
        tag.putString("slab_b", BuiltInRegistries.BLOCK.getKey(this.slabB).toString());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static Block readSlab(String id) {
        if (id == null || id.isEmpty()) {
            return Blocks.STONE_SLAB;
        }
        try {
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            return block instanceof SlabBlock ? block : Blocks.STONE_SLAB;
        } catch (RuntimeException exception) {
            return Blocks.STONE_SLAB;
        }
    }
}
