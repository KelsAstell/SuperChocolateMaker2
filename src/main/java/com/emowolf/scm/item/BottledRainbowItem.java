package com.emowolf.scm.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import com.emowolf.scm.SCM;

import javax.annotation.Nullable;
import java.util.*;

public class BottledRainbowItem extends Item {

    public static final String TAG_COLOR = "RainbowColor";

    /** 自定义标签：任意模组可将自己的可染色方块加入此标签 */
    public static final TagKey<Block> DYEABLE = BlockTags.create(new ResourceLocation(SCM.MODID, "dyeable"));

    /** 方块 → 颜色 映射表，O(1) 查找；可通过 {@link #registerDyeableBlock} 扩展 */
    private static final Map<Block, DyeColor> DYEABLE_MAP = new HashMap<>();

    /** 所有可染色方块类型后缀（长后缀优先，避免 _stained_glass_pane 被 _stained_glass 误匹配） */
    private static final String[] DYEABLE_SUFFIXES = {
            "_concrete_powder", "_stained_glass_pane", "_concrete", "_stained_glass", "_wool"
    };

    /** 注册原版 16 色羊毛、混凝土、混凝土粉末、染色玻璃、染色玻璃板 */
    static {
        for (DyeColor color : DyeColor.values()) {
            String name = color.getName();
            registerDyeableBlock(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name + "_wool")), color);
            registerDyeableBlock(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name + "_concrete")), color);
            registerDyeableBlock(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name + "_concrete_powder")), color);
            registerDyeableBlock(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name + "_stained_glass")), color);
            registerDyeableBlock(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name + "_stained_glass_pane")), color);
        }
    }

    /**
     * 注册自定义可染色方块（供本模组或其他模组调用）。
     * 注册后，该方块可被瓶装彩虹染色，且颜色映射正确。
     */
    public static void registerDyeableBlock(Block block, DyeColor color) {
        if (block != null) {
            DYEABLE_MAP.put(block, color);
        }
    }

    public BottledRainbowItem(Properties properties) {
        super(properties);
    }

    /** 获取物品当前选择的颜色 */
    public static DyeColor getColor(ItemStack stack) {
        return DyeColor.byId(stack.getOrCreateTag().getInt(TAG_COLOR) % 16);
    }

    /** 设置物品颜色 */
    public static void setColor(ItemStack stack, int colorIndex) {
        stack.getOrCreateTag().putInt(TAG_COLOR, ((colorIndex % 16) + 16) % 16);
    }

    /** 循环切换颜色（+1 或 -1） */
    public static void cycleColor(ItemStack stack, int delta) {
        int current = stack.getOrCreateTag().getInt(TAG_COLOR);
        setColor(stack, current + delta);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = level.getBlockState(pos);

        if (!isDyeableBlock(state)) return InteractionResult.PASS;

        DyeColor color = getColor(stack);

        if (player != null && player.isShiftKeyDown()) {
            dyeArea(level, pos, state, color, 16);
        } else {
            dyeBlock(level, pos, state, color);
        }

        level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        DyeColor color = getColor(stack);
        tooltipComponents.add(Component.translatable("item.chocomaker.bottled_rainbow.color",
                Component.translatable("color.chocomaker." + color.getName())));
        tooltipComponents.add(Component.translatable("item.chocomaker.bottled_rainbow.desc"));
    }

    /* ========== 方块染色核心逻辑 ========== */

    /** 获取方块的注册表路径（path 部分），找不到时返回空字符串 */
    private static String getBlockPath(Block block) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key != null ? key.getPath() : "";
    }

    /** 获取方块的注册表命名空间（modid），找不到时返回 "minecraft" */
    private static String getBlockModId(Block block) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key != null ? key.getNamespace() : "minecraft";
    }

    /**
     * 判断方块是否可被染色：
     * 1. HashMap 中已注册（原版 + 主动注册的模组方块）
     * 2. 属于 vanilla WOOL 标签（自动兼容模组羊毛）
     * 3. 属于自定义 scm:dyeable 标签（模组可通过 datapack 添加）
     * 4. 注册表名以 _concrete / _concrete_powder 结尾（兼容遵循命名规范的模组方块）
     */
    private static boolean isDyeableBlock(BlockState state) {
        Block block = state.getBlock();
        if (DYEABLE_MAP.containsKey(block)) return true;
        if (state.is(BlockTags.WOOL)) return true;
        if (state.is(DYEABLE)) return true;
        String path = getBlockPath(block);
        for (String suffix : DYEABLE_SUFFIXES) {
            if (path.endsWith(suffix)) return true;
        }
        return false;
    }

    private static void dyeBlock(Level level, BlockPos pos, BlockState originalState, DyeColor color) {
        Block newBlock = getDyedBlock(originalState, color);
        if (newBlock != null && newBlock != originalState.getBlock()) {
            level.setBlock(pos, newBlock.defaultBlockState(), 3);
        }
    }

    /** 区域染色：从起点 BFS 扩散，至多 maxCount 个同类方块 */
    private static void dyeArea(Level level, BlockPos startPos, BlockState originalState, DyeColor color, int maxCount) {
        Block newBlock = getDyedBlock(originalState, color);
        if (newBlock == null || newBlock == originalState.getBlock()) return;

        Block originalBlock = originalState.getBlock();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(startPos);
        visited.add(startPos);

        int count = 0;
        while (!queue.isEmpty() && count < maxCount) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);
            if (currentState.getBlock() != originalBlock) continue;

            level.setBlock(current, newBlock.defaultBlockState(), 3);
            count++;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
    }

    /**
     * 根据原始方块 + 目标颜色，获取染色后的方块。
     * 查找策略（依次尝试）：
     * 1. HashMap 注册表（原版羊毛/混凝土/混凝土粉末 + 主动注册的模组方块）
     * 2. 注册表名替换（兼容命名规范的模组方块）
     * 3. vanilla WOOL / scm:dyeable 标签回退（未注册但属于标签的模组方块）
     */
    @Nullable
    private static Block getDyedBlock(BlockState originalState, DyeColor color) {
        Block original = originalState.getBlock();

        // 策略1: HashMap 直接查找 → 知道原始颜色，构造同类型目标方块
        DyeColor originalColor = DYEABLE_MAP.get(original);
        if (originalColor != null) {
            return findSameTypeBlock(original, originalColor, color);
        }

        // 策略2: 注册表名替换（适用于遵循 vanilla 命名规范的模组方块）
        String path = getBlockPath(original);
        String modId = getBlockModId(original);
        for (String suffix : DYEABLE_SUFFIXES) {
            if (path.endsWith(suffix)) {
                String colorPrefix = path.substring(0, path.length() - suffix.length());
                if (DyeColor.byName(colorPrefix, null) != null) {
                    Block result = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(modId, color.getName() + suffix));
                    if (result != null) return result;
                }
                break;
            }
        }

        // 策略3: WOOL 标签回退 → 映射到原版对应颜色羊毛
        if (originalState.is(BlockTags.WOOL)) {
            return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(color.getName() + "_wool"));
        }

        // 策略4: scm:dyeable 标签回退 → 映射到原版对应颜色羊毛
        if (originalState.is(DYEABLE)) {
            return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(color.getName() + "_wool"));
        }

        return null;
    }

    /**
     * 在同类型方块中，根据原始颜色找到目标颜色的对应方块。
     * 例如：白色羊毛(原始) + 红色(目标) → 红色羊毛
     */
    @Nullable
    private static Block findSameTypeBlock(Block original, DyeColor originalColor, DyeColor targetColor) {
        String path = getBlockPath(original);
        if (path.isEmpty()) return null;

        // 确定方块类型后缀
        String suffix = null;
        for (String s : DYEABLE_SUFFIXES) {
            if (path.endsWith(s)) {
                suffix = s;
                break;
            }
        }
        if (suffix == null) return null;

        // 构造目标方块注册表名并查找
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(targetColor.getName() + suffix));
    }
}
