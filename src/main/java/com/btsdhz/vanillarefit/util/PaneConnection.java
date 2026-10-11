package com.btsdhz.vanillarefit.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 玻璃板的自动连接逻辑（直接覆盖原版那套“只设置东西南北”的逻辑）。
 * 详细规则表见原 NeoForge 版注释与 docs/玻璃板连接规则.md。
 */
public final class PaneConnection {

    private static boolean inPostProcess;
    private static boolean touchedPane;

    private PaneConnection() {
    }

    /** 会与本块互相影响的偏移：8 个水平方向（含 4 个斜角）+ 上下两格，共 10 个。 */
    private static final int[][] NEIGHBOUR_OFFSETS = {
            {-1, 0, -1}, {0, 0, -1}, {1, 0, -1},
            {-1, 0, 0}, {1, 0, 0},
            {-1, 0, 1}, {0, 0, 1}, {1, 0, 1},
            {0, 1, 0}, {0, -1, 0}
    };

    public static void beginPostProcess() {
        inPostProcess = true;
        touchedPane = false;
    }

    public static boolean endPostProcess() {
        boolean touched = touchedPane;
        inPostProcess = false;
        touchedPane = false;
        return touched;
    }

    /** 按当前周围环境重算玻璃板的 12 个部件属性；不是本模组支持的玻璃板时原样返回。 */
    public static BlockState update(BlockState state, BlockGetter level, BlockPos pos) {
        if (inPostProcess) {
            touchedPane = true;
        }
        if (!PaneCornerSupport.isSupportedPane(state)
                || !state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)
                || !(state.getBlock() instanceof IronBarsBlock pane)) {
            return state;
        }

        boolean paneNorth = isPane(level, pos, Direction.NORTH);
        boolean paneEast = isPane(level, pos, Direction.EAST);
        boolean paneSouth = isPane(level, pos, Direction.SOUTH);
        boolean paneWest = isPane(level, pos, Direction.WEST);
        boolean paneNorthEast = isPaneAt(level, pos, 1, -1);
        boolean paneSouthEast = isPaneAt(level, pos, 1, 1);
        boolean paneNorthWest = isPaneAt(level, pos, -1, -1);
        boolean paneSouthWest = isPaneAt(level, pos, -1, 1);

        boolean hasAbove = hasBlock(level, pos.above());
        boolean hasBelow = hasBlock(level, pos.below());
        boolean noBlockAboveOrBelow = !hasBlock(level, pos.above()) && !hasBlock(level, pos.below());
        boolean surroundedByPanes = paneNorth && paneEast && paneSouth && paneWest
                && paneNorthEast && paneSouthEast && paneNorthWest && paneSouthWest;

        boolean connectNorth = connectsTo(pane, level, pos, Direction.NORTH);
        boolean connectEast = connectsTo(pane, level, pos, Direction.EAST);
        boolean connectSouth = connectsTo(pane, level, pos, Direction.SOUTH);
        boolean connectWest = connectsTo(pane, level, pos, Direction.WEST);
        boolean faceNorth = connectNorth && neighbourFacesBack(level, pos, Direction.NORTH);
        boolean faceEast = connectEast && neighbourFacesBack(level, pos, Direction.EAST);
        boolean faceSouth = connectSouth && neighbourFacesBack(level, pos, Direction.SOUTH);
        boolean faceWest = connectWest && neighbourFacesBack(level, pos, Direction.WEST);

        // ==================== 第一组：四角属性 ====================
        boolean northEast = false;
        boolean southEast = false;
        boolean southWest = false;
        boolean northWest = false;

        // ----- 规则 5：相邻玻璃板的斜对角面片传播到本角 -----
        if (paneNorth) {
            BlockState north = level.getBlockState(pos.north());
            if (connectWest && isOn(north, ModBlockStateProperties.PANE_SOUTH_WEST)) {
                northWest = true;
            }
            if (connectEast && isOn(north, ModBlockStateProperties.PANE_SOUTH_EAST)) {
                northEast = true;
            }
        }
        if (paneSouth) {
            BlockState south = level.getBlockState(pos.south());
            if (connectWest && isOn(south, ModBlockStateProperties.PANE_NORTH_WEST)) {
                southWest = true;
            }
            if (connectEast && isOn(south, ModBlockStateProperties.PANE_NORTH_EAST)) {
                southEast = true;
            }
        }
        if (paneEast) {
            BlockState east = level.getBlockState(pos.east());
            if (connectNorth && isOn(east, ModBlockStateProperties.PANE_NORTH_WEST)) {
                northEast = true;
            }
            if (connectSouth && isOn(east, ModBlockStateProperties.PANE_SOUTH_WEST)) {
                southEast = true;
            }
        }
        if (paneWest) {
            BlockState west = level.getBlockState(pos.west());
            if (connectNorth && isOn(west, ModBlockStateProperties.PANE_NORTH_EAST)) {
                northWest = true;
            }
            if (connectSouth && isOn(west, ModBlockStateProperties.PANE_SOUTH_EAST)) {
                southWest = true;
            }
        }

        // ----- 规则 3：上下都没有方块时，两个方向都有会连接的方块 → 该角为真 -----
        if (noBlockAboveOrBelow) {
            if (connectNorth && connectEast) {
                northEast = true;
            }
            if (connectSouth && connectEast) {
                southEast = true;
            }
            if (connectNorth && connectWest) {
                northWest = true;
            }
            if (connectSouth && connectWest) {
                southWest = true;
            }
        }

        // ----- 规则 2：上下都没有方块时，两个方向 + 它们之间的斜角都有玻璃板 → 该角为真 -----
        if (noBlockAboveOrBelow) {
            if (paneNorth && paneEast && paneNorthEast) {
                northEast = true;
            }
            if (paneSouth && paneEast && paneSouthEast) {
                southEast = true;
            }
            if (paneNorth && paneWest && paneNorthWest) {
                northWest = true;
            }
            if (paneSouth && paneWest && paneSouthWest) {
                southWest = true;
            }
        }

        // ----- 规则 1（角部分）：8 个方向全是玻璃板 → 四角全真 -----
        if (surroundedByPanes) {
            northEast = true;
            southEast = true;
            southWest = true;
            northWest = true;
        }

        // ==================== 第二组：竖直面片 ====================
        boolean northLower = false;
        boolean eastLower = false;
        boolean southLower = false;
        boolean westLower = false;
        boolean northUpper = false;
        boolean eastUpper = false;
        boolean southUpper = false;
        boolean westUpper = false;

        // ----- 规则 9 -----
        if (faceNorth) {
            northLower = true;
            northUpper = true;
        }
        if (faceEast) {
            eastLower = true;
            eastUpper = true;
        }
        if (faceSouth) {
            southLower = true;
            southUpper = true;
        }
        if (faceWest) {
            westLower = true;
            westUpper = true;
        }

        // ----- 规则 8 -----
        if (northEast || southEast || southWest || northWest) {
            northLower = false;
            eastLower = false;
            southLower = false;
            westLower = false;
            northUpper = false;
            eastUpper = false;
            southUpper = false;
            westUpper = false;
        }

        // ----- 规则 7 -----
        BlockState belowPane = level.getBlockState(pos.below());
        if (belowPane.getBlock() instanceof IronBarsBlock) {
            if (bothHalves(belowPane, Direction.NORTH)) {
                northLower = true;
            }
            if (bothHalves(belowPane, Direction.EAST)) {
                eastLower = true;
            }
            if (bothHalves(belowPane, Direction.SOUTH)) {
                southLower = true;
            }
            if (bothHalves(belowPane, Direction.WEST)) {
                westLower = true;
            }
        }
        BlockState abovePane = level.getBlockState(pos.above());
        if (abovePane.getBlock() instanceof IronBarsBlock) {
            if (bothHalves(abovePane, Direction.NORTH)) {
                northUpper = true;
            }
            if (bothHalves(abovePane, Direction.EAST)) {
                eastUpper = true;
            }
            if (bothHalves(abovePane, Direction.SOUTH)) {
                southUpper = true;
            }
            if (bothHalves(abovePane, Direction.WEST)) {
                westUpper = true;
            }
        }

        // ----- 规则 6 -----
        if (faceNorth) {
            if (hasAbove) {
                northUpper = true;
            }
            if (hasBelow) {
                northLower = true;
            }
        }
        if (faceEast) {
            if (hasAbove) {
                eastUpper = true;
            }
            if (hasBelow) {
                eastLower = true;
            }
        }
        if (faceSouth) {
            if (hasAbove) {
                southUpper = true;
            }
            if (hasBelow) {
                southLower = true;
            }
        }
        if (faceWest) {
            if (hasAbove) {
                westUpper = true;
            }
            if (hasBelow) {
                westLower = true;
            }
        }

        // ----- 规则 4 -----
        if (northEast && northWest) {
            northLower = false;
            northUpper = false;
        }
        if (southEast && southWest) {
            southLower = false;
            southUpper = false;
        }
        if (northEast && southEast) {
            eastLower = false;
            eastUpper = false;
        }
        if (northWest && southWest) {
            westLower = false;
            westUpper = false;
        }

        // ----- 规则 1（竖直部分） -----
        if (surroundedByPanes) {
            northLower = false;
            eastLower = false;
            southLower = false;
            westLower = false;
            northUpper = false;
            eastUpper = false;
            southUpper = false;
            westUpper = false;
        }

        return state
                .setValue(CrossCollisionBlock.NORTH, northLower)
                .setValue(CrossCollisionBlock.EAST, eastLower)
                .setValue(CrossCollisionBlock.SOUTH, southLower)
                .setValue(CrossCollisionBlock.WEST, westLower)
                .setValue(ModBlockStateProperties.PANE_NORTH_UP, northUpper)
                .setValue(ModBlockStateProperties.PANE_EAST_UP, eastUpper)
                .setValue(ModBlockStateProperties.PANE_SOUTH_UP, southUpper)
                .setValue(ModBlockStateProperties.PANE_WEST_UP, westUpper)
                .setValue(ModBlockStateProperties.PANE_NORTH_EAST, northEast)
                .setValue(ModBlockStateProperties.PANE_SOUTH_EAST, southEast)
                .setValue(ModBlockStateProperties.PANE_SOUTH_WEST, southWest)
                .setValue(ModBlockStateProperties.PANE_NORTH_WEST, northWest);
    }

    private static boolean isPane(BlockGetter level, BlockPos pos, Direction direction) {
        return isPaneAt(level, pos.relative(direction));
    }

    private static boolean isPaneAt(BlockGetter level, BlockPos pos, int dx, int dz) {
        return isPaneAt(level, pos.offset(dx, 0, dz));
    }

    private static boolean isPaneAt(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof IronBarsBlock;
    }

    private static boolean hasBlock(BlockGetter level, BlockPos pos) {
        return !level.getBlockState(pos).isAir();
    }

    private static boolean isOn(BlockState state, Property<Boolean> property) {
        return state.hasProperty(property) && state.getValue(property);
    }

    private static boolean neighbourFacesBack(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState neighbour = level.getBlockState(pos.relative(direction));
        if (!(neighbour.getBlock() instanceof IronBarsBlock)) {
            return true;
        }
        boolean neighbourHasCorner = isOn(neighbour, ModBlockStateProperties.PANE_NORTH_EAST)
                || isOn(neighbour, ModBlockStateProperties.PANE_SOUTH_EAST)
                || isOn(neighbour, ModBlockStateProperties.PANE_NORTH_WEST)
                || isOn(neighbour, ModBlockStateProperties.PANE_SOUTH_WEST);
        if (!neighbourHasCorner) {
            return true;
        }
        Direction facing = direction.getOpposite();
        return isOn(neighbour, lowerProperty(facing)) || isOn(neighbour, upperProperty(facing));
    }

    private static Property<Boolean> lowerProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> CrossCollisionBlock.NORTH;
            case EAST -> CrossCollisionBlock.EAST;
            case SOUTH -> CrossCollisionBlock.SOUTH;
            case WEST -> CrossCollisionBlock.WEST;
            default -> throw new IllegalArgumentException("不是水平方向: " + direction);
        };
    }

    private static Property<Boolean> upperProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> ModBlockStateProperties.PANE_NORTH_UP;
            case EAST -> ModBlockStateProperties.PANE_EAST_UP;
            case SOUTH -> ModBlockStateProperties.PANE_SOUTH_UP;
            case WEST -> ModBlockStateProperties.PANE_WEST_UP;
            default -> throw new IllegalArgumentException("不是水平方向: " + direction);
        };
    }

    private static boolean bothHalves(BlockState pane, Direction direction) {
        return isOn(pane, lowerProperty(direction)) && isOn(pane, upperProperty(direction));
    }

    private static boolean connectsTo(IronBarsBlock pane, BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighbor = level.getBlockState(neighborPos);
        return pane.attachsTo(neighbor, neighbor.isFaceSturdy(level, neighborPos, direction.getOpposite()));
    }

    /** 世界生成结束后，把一个区块里参与部件化的玻璃板/铁栏杆刷到稳定状态。 */
    public static void refreshChunk(LevelChunk chunk, Level level) {
        List<BlockPos> seeds = collectPanesInChunk(chunk);
        if (seeds.isEmpty()) {
            return;
        }
        Set<BlockPos> inQueue = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : seeds) {
            if (inQueue.add(pos)) {
                queue.add(pos);
            }
        }
        int budget = seeds.size() * 8 + 64;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPos pos = queue.poll();
            inQueue.remove(pos);
            BlockState state = level.getBlockState(pos);
            if (!PaneCornerSupport.isSupportedPane(state)) {
                continue;
            }
            BlockState next = update(state, level, pos);
            if (next == state) {
                continue;
            }
            level.setBlock(pos, next, 2 | 16);
            for (int[] offset : NEIGHBOUR_OFFSETS) {
                BlockPos neighbor = mutable.setWithOffset(pos, offset[0], offset[1], offset[2]).immutable();
                if (level.getBlockState(neighbor).getBlock() instanceof IronBarsBlock && inQueue.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
    }

    /** 补上“水平斜对角”这条原版邻居级联覆盖不到的依赖。 */
    public static void refreshDiagonals(LevelAccessor level, BlockState state, BlockPos pos,
                                        int flags, int recursionLeft) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Direction side = facing.getClockWise();
            BlockPos diagonal = new BlockPos(x + facing.getStepX() + side.getStepX(), y,
                    z + facing.getStepZ() + side.getStepZ());
            if (!PaneCornerSupport.isSupportedPane(level.getBlockState(diagonal))) {
                continue;
            }
            level.neighborShapeChanged(side.getOpposite(), state, diagonal, pos, flags, recursionLeft);
        }
    }

    private static List<BlockPos> collectPanesInChunk(LevelChunk chunk) {
        List<BlockPos> result = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos chunkPos = chunk.getPos();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()
                    || !section.getStates().maybeHas(state -> state.getBlock() instanceof IronBarsBlock)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getMinSection() + index);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (PaneCornerSupport.isSupportedPane(state)) {
                            result.add(new BlockPos(chunkPos.getMinBlockX() + x, baseY + y,
                                    chunkPos.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }
        return result;
    }
}
