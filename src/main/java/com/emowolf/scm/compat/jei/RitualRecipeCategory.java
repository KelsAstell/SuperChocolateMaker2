package com.emowolf.scm.compat.jei;

import com.emowolf.scm.SCM;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 献祭仪式配方分类——在 JEI 中展示祭坛献祭配方。
 * 布局：左侧祭品 → 中间箭头 + 催化剂×9 → 右侧产物/效果描述。
 */
public class RitualRecipeCategory implements IRecipeCategory<RitualRecipe> {

    public static final RecipeType<RitualRecipe> TYPE =
            RecipeType.create(SCM.MODID, "ritual", RitualRecipe.class);

    /** 每行最大字符数（中文为主，约 16 字/行） */
    private static final int MAX_CHARS_PER_LINE = 16;
    /** 绘制区域最大宽度（像素），用于控制 draw 时的换行 */
    private static final int DRAW_MAX_WIDTH = 110;

    private final IDrawable icon;

    public RitualRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(
                new ItemStack(com.emowolf.scm.block.SCMBlocks.ALTAR_CORE.get()));
    }

    @Override
    public RecipeType<RitualRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.chocomaker.ritual.title");
    }

    @Override
    public int getWidth() {
        return 130;
    }

    @Override
    public int getHeight() {
        return 54;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RitualRecipe recipe, IFocusGroup focuses) {
        // 祭品槽位（左）
        builder.addSlot(RecipeIngredientRole.INPUT, 9, 19)
                .addItemStack(new ItemStack(recipe.sacrifice()));

        // 催化剂槽位（中间——标记为 CATALYST，不可点击查询配方）
        builder.addSlot(RecipeIngredientRole.CATALYST, 49, 19)
                .addItemStack(new ItemStack(recipe.catalyst().asItem(), recipe.catalystCount()));

        // 产物槽位（右）
        if (recipe.output() != null && !recipe.output().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 105, 19)
                    .addItemStack(recipe.output());
        } else {
            // 无物品产物：在 tooltip 中显示效果描述（金色 + 自动换行）
            builder.addSlot(RecipeIngredientRole.OUTPUT, 105, 19)
                    .addRichTooltipCallback((view, tooltip) -> {
                        String desc = recipe.description().getString();
                        for (String line : wrapText(desc, MAX_CHARS_PER_LINE)) {
                            tooltip.add(Component.literal(line)
                                    .withStyle(ChatFormatting.GOLD));
                        }
                    });
        }
    }

    @Override
    public void draw(RitualRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 绘制箭头（从祭品指向催化剂 → 产物）
        int arrowColor = 0xFF8B8B8B;
        int ax = 35, ay = 25;
        guiGraphics.fill(ax, ay, ax + 9, ay + 2, arrowColor);
        guiGraphics.fill(ax + 6, ay - 4, ax + 10, ay + 6, arrowColor);

        int bx = 75, by = 25;
        guiGraphics.fill(bx, by, bx + 9, by + 2, arrowColor);
        guiGraphics.fill(bx + 6, by - 4, bx + 10, by + 6, arrowColor);

        // 催化剂数量标注：×9
        guiGraphics.drawString(
                Minecraft.getInstance().font,
                "×" + recipe.catalystCount(),
                72, 10,
                0xFFAAAAAA,
                false
        );

        // 效果描述（无产物配方在输出区域下方绘制，金色带阴影 + 自动换行）
        if (recipe.output() == null || recipe.output().isEmpty()) {
            String desc = recipe.description().getString();
            List<String> lines = wrapText(desc, MAX_CHARS_PER_LINE);

            int drawX = 8;
            int drawY = 40;
            int lineHeight = 10;
            int textColor = 0xFFFFAA00; // 金色

            for (String line : lines) {
                guiGraphics.drawString(
                        Minecraft.getInstance().font,
                        line,
                        drawX, drawY,
                        textColor,
                        true  // shadow 提高可读性
                );
                drawY += lineHeight;
            }
        }
    }

    /**
     * 按指定最大字符数将文本拆成多行。
     * 对于中文文本在字符边界处断行；英文尽量在单词边界处断行。
     */
    private static List<String> wrapText(String text, int maxCharsPerLine) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        StringBuilder currentLine = new StringBuilder();
        int charCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 已有的 \n 保留为显式换行
            if (c == '\n') {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                charCount = 0;
                continue;
            }

            currentLine.append(c);
            // 中文字符和全角标点算 1 个字符位（实际显示宽度约为 ASCII 的 2 倍）
            if (isCJK(c) || isFullWidth(c)) {
                charCount += 2;
            } else {
                charCount++;
            }

            if (charCount >= maxCharsPerLine) {
                // 英文环境下尽量在单词边界换行
                if (i + 1 < text.length() && isAlpha(text.charAt(i + 1)) && isAlpha(c)) {
                    // 当前仍在单词中间，向前找最近空格
                    int lastSpace = currentLine.lastIndexOf(" ");
                    if (lastSpace > 0) {
                        String overflow = currentLine.substring(lastSpace + 1);
                        currentLine.setLength(lastSpace);
                        lines.add(currentLine.toString().trim());
                        currentLine = new StringBuilder(overflow);
                        charCount = overflow.length();
                    } else {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder();
                        charCount = 0;
                    }
                } else {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                    charCount = 0;
                }
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static boolean isCJK(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private static boolean isFullWidth(char c) {
        return c == '（' || c == '）' || c == '→' || c == '，' || c == '。'
                || c == '！' || c == '？' || c == '：' || c == '；'
                || c == '【' || c == '】' || c == '《' || c == '》';
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
