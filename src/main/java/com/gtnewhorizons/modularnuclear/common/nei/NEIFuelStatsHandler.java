package com.gtnewhorizons.modularnuclear.common.nei;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.IResource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTUtility;

public class NEIFuelStatsHandler extends TemplateRecipeHandler {

    public static final String OVERLAY_ID = "modularnuclear.fuel_stats";

    public static final int PANEL_X = 6;
    public static final int PANEL_Y = 66;
    public static final int PANEL_W = 154;
    public static final int PANEL_H = 78;

    public static final int SCREEN_X = PANEL_X + 2;
    public static final int SCREEN_Y = PANEL_Y + 2;
    public static final int SCREEN_W = PANEL_W - 4;
    public static final int SCREEN_H = PANEL_H - 4;

    public static final int PLOT_X = 28;
    public static final int PLOT_Y = 78;
    public static final int PLOT_W = 104;
    public static final int PLOT_H = 50;

    // Ambient Temperature (fixed to 20.0 °C for damage curve calibration)
    public static final double AMBIENT_TEMP = 20.0;
    public static final double DEFAULT_AMBIENT_TEMP = 20.0;
    public static final double sAmbientTemp = 20.0;

    private static int sCachedThemeBg = -1;
    private static long sLastThemeCheck = 0;

    public static int getThemeBackgroundColor() {
        long now = System.currentTimeMillis();
        if (sCachedThemeBg != -1 && now - sLastThemeCheck < 4000) {
            return sCachedThemeBg;
        }
        sLastThemeCheck = now;
        try {
            ResourceLocation loc = new ResourceLocation("nei:textures/gui/recipebg.png");
            IResource res = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(loc);
            if (res != null) {
                try (InputStream in = res.getInputStream()) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) {
                        int sx = Math.min(30, img.getWidth() - 1);
                        int sy = Math.min(30, img.getHeight() - 1);
                        sCachedThemeBg = img.getRGB(sx, sy) | 0xFF000000;
                        return sCachedThemeBg;
                    }
                }
            }
        } catch (Throwable ignored) {}
        sCachedThemeBg = 0xFFC6C6C6;
        return sCachedThemeBg;
    }

    public static boolean isDarkTheme() {
        int col = getThemeBackgroundColor();
        int r = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int b = col & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) < 128.0;
    }

    public static void drawThemePanel(int x, int y, int w, int h) {
        boolean dark = isDarkTheme();
        int highlight = dark ? 0xFF555555 : 0xFFFFFFFF;
        int shadow = dark ? 0xFF141414 : 0xFF373737;
        int bg = getThemeBackgroundColor();

        GuiDraw.drawRect(x + 1, y, w - 2, 1, shadow);
        GuiDraw.drawRect(x, y + 1, 1, h - 2, shadow);
        GuiDraw.drawRect(x + 1, y + h - 1, w - 2, 1, highlight);
        GuiDraw.drawRect(x + w - 1, y + 1, 1, h - 2, highlight);
        GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, bg);
    }

    public static void drawSunkenBox(int x, int y, int w, int h, int fillCol) {
        boolean dark = isDarkTheme();
        int shadow = dark ? 0xFF141414 : 0xFF373737;
        int highlight = dark ? 0xFF555555 : 0xFFFFFFFF;

        GuiDraw.drawRect(x, y, w, 1, shadow);
        GuiDraw.drawRect(x, y, 1, h, shadow);
        GuiDraw.drawRect(x + 1, y + h - 1, w - 2, 1, highlight);
        GuiDraw.drawRect(x + w - 1, y + 1, 1, h - 2, highlight);
        GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, fillCol);
    }

    public static void drawSlotBox(int x, int y) {
        boolean dark = isDarkTheme();
        int shadow = dark ? 0xFF141414 : 0xFF373737;
        int highlight = dark ? 0xFF555555 : 0xFFFFFFFF;
        int fill = dark ? 0xFF222222 : 0xFF8B8B8B;

        GuiDraw.drawRect(x, y, 18, 1, shadow);
        GuiDraw.drawRect(x, y, 1, 18, shadow);
        GuiDraw.drawRect(x + 17, y, 1, 18, highlight);
        GuiDraw.drawRect(x, y + 17, 18, 1, highlight);
        GuiDraw.drawRect(x + 1, y + 1, 16, 16, fill);
    }

    public static boolean isMouseOverPlot(int x, int y) {
        return x >= PLOT_X && x <= PLOT_X + PLOT_W && y >= PLOT_Y && y <= PLOT_Y + PLOT_H;
    }

    private static java.lang.reflect.Field fieldGuiLeft;
    private static java.lang.reflect.Field fieldGuiTop;

    static {
        try {
            fieldGuiLeft = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("guiLeft");
            fieldGuiLeft.setAccessible(true);
        } catch (Throwable t) {
            try {
                fieldGuiLeft = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("field_147003_i");
                fieldGuiLeft.setAccessible(true);
            } catch (Throwable ignored) {}
        }
        try {
            fieldGuiTop = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("guiTop");
            fieldGuiTop.setAccessible(true);
        } catch (Throwable t) {
            try {
                fieldGuiTop = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("field_147009_r");
                fieldGuiTop.setAccessible(true);
            } catch (Throwable ignored) {}
        }
    }

    private static int getGuiLeft(GuiRecipe<?> gui) {
        if (fieldGuiLeft != null) {
            try {
                return fieldGuiLeft.getInt(gui);
            } catch (Throwable ignored) {}
        }
        return (gui.width - 176) / 2;
    }

    private static int getGuiTop(GuiRecipe<?> gui) {
        if (fieldGuiTop != null) {
            try {
                return fieldGuiTop.getInt(gui);
            } catch (Throwable ignored) {}
        }
        return (gui.height - 166) / 2;
    }

    public static Point getMouseInRecipe(GuiRecipe<?> gui, int recipeIndex) {
        if (gui == null) return null;
        Point mousepos = GuiDraw.getMousePosition();
        Point offset = gui.getRecipePosition(recipeIndex);
        if (offset == null) return null;
        return new Point(mousepos.x - getGuiLeft(gui) - offset.x, mousepos.y - getGuiTop(gui) - offset.y);
    }

    public class CachedFuelStatsRecipe extends CachedRecipe {

        public final FuelData fuelData;
        public final NuclearFuelType fuel;
        public final PositionedStack fuelStack;

        public CachedFuelStatsRecipe(FuelData fuelData, List<ItemStack> items) {
            this.fuelData = fuelData;
            this.fuel = (fuelData != null) ? findFuelType(fuelData) : null;
            List<ItemStack> displayItems = new ArrayList<>();
            if (items != null) {
                for (ItemStack is : items) {
                    if (is != null && is.getItem() != null
                        && Item.itemRegistry.getNameForObject(is.getItem()) != null) {
                        displayItems.add(is);
                    }
                }
            }
            if (displayItems.isEmpty()) {
                ItemStack fallback;
                if (fuelData != null && fuelData.isLiquid) {
                    fallback = new ItemStack(net.minecraft.init.Items.water_bucket);
                    fallback.setStackDisplayName(fuelData.name);
                } else if (ItemList.RodThorium.hasBeenSet()) {
                    fallback = ItemList.RodThorium.get(1L);
                } else {
                    fallback = new ItemStack(net.minecraft.init.Items.coal);
                }
                displayItems.add(fallback);
            }
            this.fuelStack = new PositionedStack(displayItems, 12, 10);
        }

        public CachedFuelStatsRecipe(NuclearFuelType fuel, List<ItemStack> items) {
            this.fuel = fuel;
            this.fuelData = (fuel != null) ? findFuelData(fuel) : null;
            List<ItemStack> displayItems = new ArrayList<>();
            if (items != null) {
                for (ItemStack is : items) {
                    if (is != null && is.getItem() != null
                        && Item.itemRegistry.getNameForObject(is.getItem()) != null) {
                        displayItems.add(is);
                    }
                }
            }
            if (displayItems.isEmpty()) {
                ItemStack fallback = (ItemList.RodThorium.hasBeenSet()) ? ItemList.RodThorium.get(1L)
                    : new ItemStack(net.minecraft.init.Items.coal);
                displayItems.add(fallback);
            }
            this.fuelStack = new PositionedStack(displayItems, 12, 10);
        }

        @Override
        public PositionedStack getIngredient() {
            return fuelStack;
        }

        @Override
        public PositionedStack getResult() {
            return fuelStack;
        }
    }

    public static NuclearFuelType findFuelType(FuelData fd) {
        if (fd == null) return null;
        for (NuclearFuelType nft : NuclearFuelType.values()) {
            if (nft.name()
                .equalsIgnoreCase(fd.id) || nft.displayName.equalsIgnoreCase(fd.name)) {
                return nft;
            }
        }
        return null;
    }

    public static FuelData findFuelData(NuclearFuelType fuel) {
        if (fuel == null) return null;
        for (FuelData fd : ReactorDataRegistry.getAllFuels()) {
            if (fd.id.equalsIgnoreCase(fuel.name()) || fd.name.equalsIgnoreCase(fuel.displayName)) {
                return fd;
            }
        }
        return null;
    }

    private static void addIfSet(List<ItemStack> list, ItemList item) {
        if (item != null && item.hasBeenSet()) {
            ItemStack stack = item.get(1L);
            if (stack != null && stack.getItem() != null
                && Item.itemRegistry.getNameForObject(stack.getItem()) != null) {
                list.add(stack);
            }
        }
    }

    public static List<ItemStack> getFuelStacks(NuclearFuelType fuel) {
        List<ItemStack> list = new ArrayList<>();
        switch (fuel) {
            case GLOWSTONE -> addIfSet(list, ItemList.RodGlowstone);
            case LITHIUM -> addIfSet(list, ItemList.RodLithium);
            case THORIUM -> {
                addIfSet(list, ItemList.RodThorium);
                addIfSet(list, ItemList.RodThorium2);
                addIfSet(list, ItemList.RodThorium4);
            }
            case URANIUM -> {
                addIfSet(list, ItemList.RodUranium);
                addIfSet(list, ItemList.RodUranium2);
                addIfSet(list, ItemList.RodUranium4);
            }
            case MOX -> {
                addIfSet(list, ItemList.RodMOX);
                addIfSet(list, ItemList.RodMOX2);
                addIfSet(list, ItemList.RodMOX4);
            }
            case HD_URANIUM -> {
                addIfSet(list, ItemList.RodHighDensityUranium);
                addIfSet(list, ItemList.RodHighDensityUranium2);
                addIfSet(list, ItemList.RodHighDensityUranium4);
            }
            case HD_PLUTONIUM -> {
                addIfSet(list, ItemList.RodHighDensityPlutonium);
                addIfSet(list, ItemList.RodHighDensityPlutonium2);
                addIfSet(list, ItemList.RodHighDensityPlutonium4);
            }
            case EXCITED_URANIUM -> {
                addIfSet(list, ItemList.RodExcitedUranium);
                addIfSet(list, ItemList.RodExcitedUranium2);
                addIfSet(list, ItemList.RodExcitedUranium4);
            }
            case EXCITED_PLUTONIUM -> {
                addIfSet(list, ItemList.RodExcitedPlutonium);
                addIfSet(list, ItemList.RodExcitedPlutonium2);
                addIfSet(list, ItemList.RodExcitedPlutonium4);
            }
            case NAQUADAH -> {
                addIfSet(list, ItemList.RodNaquadah);
                addIfSet(list, ItemList.RodNaquadah2);
                addIfSet(list, ItemList.RodNaquadah4);
            }
            case NAQUADRIA -> {
                addIfSet(list, ItemList.RodNaquadria);
                addIfSet(list, ItemList.RodNaquadria2);
                addIfSet(list, ItemList.RodNaquadria4);
            }
            case TIBERIUM -> {
                addIfSet(list, ItemList.RodTiberium);
                addIfSet(list, ItemList.RodTiberium2);
                addIfSet(list, ItemList.RodTiberium4);
            }
            case THE_CORE -> addIfSet(list, ItemList.RodNaquadah32);
        }
        return list;
    }

    public NEIFuelStatsHandler() {}

    public static ItemStack getControllerStack() {
        if (ModMetaTileEntities.reactor != null) {
            return ModMetaTileEntities.reactor.copy();
        }
        if (gregtech.api.GregTechAPI.METATILEENTITIES[ModMetaTileEntities.ID_NUCLEAR_REACTOR] != null) {
            return gregtech.api.GregTechAPI.METATILEENTITIES[ModMetaTileEntities.ID_NUCLEAR_REACTOR].getStackForm(1L);
        }
        return null;
    }

    @Override
    public String getHandlerId() {
        return OVERLAY_ID;
    }

    @Override
    public String getRecipeName() {
        return StatCollector.canTranslate("modularnuclear.nei.fuel_stats.name")
            ? StatCollector.translateToLocal("modularnuclear.nei.fuel_stats.name")
            : "MPTR fuels";
    }

    @Override
    public String getGuiTexture() {
        return "gregtech:textures/gui/nei/neutron_interaction_atlas.png";
    }

    @Override
    public String getOverlayIdentifier() {
        return OVERLAY_ID;
    }

    @Override
    public int recipiesPerPage() {
        return 1;
    }

    @Override
    public void loadTransferRects() {
        this.transferRects.add(new RecipeTransferRect(new Rectangle(11, 9, 18, 18), getOverlayIdentifier()));
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (outputId != null && outputId.equals(getOverlayIdentifier())) {
            loadAllFuels();
        } else {
            super.loadCraftingRecipes(outputId, results);
        }
    }

    @Override
    public int getRecipeHeight(int recipe) {
        return 166;
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result == null) return;
        if (isController(result)) {
            loadAllFuels();
            return;
        }
        FuelData fd = ReactorDataRegistry.findFuel(result);
        if (fd == null) {
            try {
                FluidStack fs = GTUtility.getFluidForFilledItem(result, true);
                if (fs != null) {
                    fd = ReactorDataRegistry.findFuel(fs);
                }
            } catch (Throwable ignored) {}
        }
        if (fd != null) {
            List<ItemStack> stacks = getFuelStacks(fd);
            if (stacks.isEmpty()) stacks = Collections.singletonList(result.copy());
            this.arecipes.add(new CachedFuelStatsRecipe(fd, stacks));
            return;
        }
        for (FuelData f : ReactorDataRegistry.getAllFuels()) {
            if (matches(result, f)) {
                this.arecipes.add(new CachedFuelStatsRecipe(f, getFuelStacks(f)));
            }
        }
        if (this.arecipes.isEmpty()) {
            for (NuclearFuelType fuel : NuclearFuelType.values()) {
                if (matches(result, fuel)) {
                    this.arecipes.add(new CachedFuelStatsRecipe(fuel, getFuelStacks(fuel)));
                }
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) return;

        // Clicking on the nuclear reactor catalyst shows all fuels
        if (isController(ingredient)) {
            loadAllFuels();
            return;
        }

        FuelData fd = ReactorDataRegistry.findFuel(ingredient);
        if (fd == null) {
            try {
                FluidStack fs = GTUtility.getFluidForFilledItem(ingredient, true);
                if (fs != null) {
                    fd = ReactorDataRegistry.findFuel(fs);
                }
            } catch (Throwable ignored) {}
        }
        if (fd != null) {
            List<ItemStack> stacks = getFuelStacks(fd);
            if (stacks.isEmpty()) stacks = Collections.singletonList(ingredient.copy());
            this.arecipes.add(new CachedFuelStatsRecipe(fd, stacks));
            return;
        }
        for (FuelData f : ReactorDataRegistry.getAllFuels()) {
            if (matches(ingredient, f)) {
                this.arecipes.add(new CachedFuelStatsRecipe(f, getFuelStacks(f)));
            }
        }
        if (this.arecipes.isEmpty()) {
            for (NuclearFuelType fuel : NuclearFuelType.values()) {
                if (matches(ingredient, fuel)) {
                    this.arecipes.add(new CachedFuelStatsRecipe(fuel, getFuelStacks(fuel)));
                }
            }
        }
    }

    private void loadAllFuels() {
        List<FuelData> all = ReactorDataRegistry.getAllFuels();
        if (all != null && !all.isEmpty()) {
            for (FuelData fd : all) {
                this.arecipes.add(new CachedFuelStatsRecipe(fd, getFuelStacks(fd)));
            }
        } else {
            for (NuclearFuelType fuel : NuclearFuelType.values()) {
                this.arecipes.add(new CachedFuelStatsRecipe(fuel, getFuelStacks(fuel)));
            }
        }
    }

    private static boolean isController(ItemStack stack) {
        if (stack == null) return false;
        ItemStack ctrl = getControllerStack();
        return ctrl != null && ctrl.isItemEqual(stack);
    }

    public static ItemStack getFluidDisplayStack(String fluidName) {
        if (fluidName == null || fluidName.isEmpty()) return null;
        try {
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (fluid == null && fluidName.startsWith("fluid.")) {
                fluid = FluidRegistry.getFluid(fluidName.substring(6));
            }
            if (fluid == null && !fluidName.startsWith("fluid.")) {
                fluid = FluidRegistry.getFluid("fluid." + fluidName);
            }
            if (fluid != null && ItemList.Display_Fluid.hasBeenSet()) {
                ItemStack display = GTUtility.getFluidDisplayStack(new FluidStack(fluid, 1000), false);
                if (display != null && display.getItem() != null
                    && Item.itemRegistry.getNameForObject(display.getItem()) != null) {
                    return display;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static ItemStack getFluidContainerStack(String fluidName) {
        if (fluidName == null || fluidName.isEmpty()) return null;
        try {
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (fluid == null && fluidName.startsWith("fluid.")) {
                fluid = FluidRegistry.getFluid(fluidName.substring(6));
            }
            if (fluid == null && !fluidName.startsWith("fluid.")) {
                fluid = FluidRegistry.getFluid("fluid." + fluidName);
            }
            if (fluid != null) {
                FluidStack fs = new FluidStack(fluid, 1000);
                if (ItemList.Cell_Empty.hasBeenSet()) {
                    ItemStack filled = GTUtility.fillFluidContainer(fs, ItemList.Cell_Empty.get(1L), true, true);
                    if (filled != null && filled.getItem() != null
                        && Item.itemRegistry.getNameForObject(filled.getItem()) != null) {
                        return filled;
                    }
                }
                for (net.minecraftforge.fluids.FluidContainerRegistry.FluidContainerData data : net.minecraftforge.fluids.FluidContainerRegistry
                    .getRegisteredFluidContainerData()) {
                    if (data != null && data.fluid != null
                        && data.fluid.getFluid() == fluid
                        && data.filledContainer != null) {
                        if (data.filledContainer.getItem() != null
                            && Item.itemRegistry.getNameForObject(data.filledContainer.getItem()) != null) {
                            return data.filledContainer.copy();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void addIfUnique(List<ItemStack> list, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;
        if (Item.itemRegistry.getNameForObject(stack.getItem()) == null) return;
        for (ItemStack existing : list) {
            if (GTUtility.areStacksEqual(existing, stack, true)) return;
        }
        list.add(stack);
    }

    public static List<ItemStack> getFuelStacks(FuelData fd) {
        List<ItemStack> list = new ArrayList<>();
        if (fd == null) return list;

        if (fd.isLiquid) {
            if (fd.matching != null) {
                for (String pattern : fd.matching) {
                    ItemStack display = getFluidDisplayStack(pattern);
                    if (display != null) {
                        addIfUnique(list, display);
                        break;
                    }
                }
                for (String pattern : fd.matching) {
                    ItemStack container = getFluidContainerStack(pattern);
                    if (container != null) {
                        addIfUnique(list, container);
                    }
                }
            }
            if (list.isEmpty()) {
                ItemStack display = getFluidDisplayStack(fd.id);
                if (display != null) addIfUnique(list, display);
            }
            if (!list.isEmpty()) {
                return list;
            }
            ItemStack fallback = new ItemStack(net.minecraft.init.Items.water_bucket);
            fallback.setStackDisplayName(fd.name);
            list.add(fallback);
            return list;
        }

        if (fd.matching != null) {
            for (String pattern : fd.matching) {
                ItemStack stack = ReactorDataRegistry.resolveItemStack(pattern);
                if (stack != null) {
                    addIfUnique(list, stack);
                }
            }
        }
        if (list.isEmpty()) {
            NuclearFuelType nft = findFuelType(fd);
            if (nft != null) {
                list.addAll(getFuelStacks(nft));
            }
        }
        return list;
    }

    private boolean matches(ItemStack target, FuelData fd) {
        if (target == null || fd == null) return false;
        if (fd.isLiquid) {
            FluidStack fs = null;
            try {
                if (ItemList.Display_Fluid.hasBeenSet()) {
                    fs = GTUtility.getFluidFromDisplayStack(target);
                }
            } catch (Throwable ignored) {}
            if (fs == null) {
                try {
                    fs = GTUtility.getFluidForFilledItem(target, true);
                } catch (Throwable ignored) {}
            }
            if (fs != null) {
                return ReactorDataRegistry.matchesFluid(fs, fd.matching);
            }
        }
        if (ReactorDataRegistry.matchesItem(target, fd.matching)) return true;
        List<ItemStack> items = getFuelStacks(fd);
        for (ItemStack item : items) {
            if (GTUtility.areStacksEqual(target, item, true)) return true;
            if (target.getItem() == item.getItem()) {
                return target.getItemDamage() == OreDictionary.WILDCARD_VALUE
                    || item.getItemDamage() == OreDictionary.WILDCARD_VALUE
                    || target.getItemDamage() == item.getItemDamage();
            }
        }
        return false;
    }

    private boolean matches(ItemStack target, NuclearFuelType fuel) {
        if (target == null || fuel == null) return false;
        NuclearFuelType resolved = NuclearFuelType.fromName(target.getUnlocalizedName());
        if (resolved == fuel) return true;

        List<ItemStack> items = getFuelStacks(fuel);
        for (ItemStack item : items) {
            if (GTUtility.areStacksEqual(target, item, true)) return true;
            if (target.getItem() == item.getItem()) {
                return target.getItemDamage() == OreDictionary.WILDCARD_VALUE
                    || item.getItemDamage() == OreDictionary.WILDCARD_VALUE
                    || target.getItemDamage() == item.getItemDamage();
            }
        }
        return false;
    }

    @Override
    public void drawBackground(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);
        if (!(cached instanceof CachedFuelStatsRecipe recipe)) return;

        // Slot box for the fuel rod icon
        drawSlotBox(11, 9);

        // Chart container panel matching theme background
        drawThemePanel(PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        // Sunken monitor display screen (neutral dark graphite fill)
        drawSunkenBox(SCREEN_X, SCREEN_Y, SCREEN_W, SCREEN_H, 0xFF141619);

        // Inner plot area backdrop (neutral deep dark charcoal)
        GuiDraw.drawRect(PLOT_X, PLOT_Y, PLOT_W, PLOT_H, 0xFF101114);

        // Inner plot border (neutral subtle graphite outline)
        GuiDraw.drawRect(PLOT_X - 1, PLOT_Y - 1, PLOT_W + 2, 1, 0xFF2A2D34);
        GuiDraw.drawRect(PLOT_X - 1, PLOT_Y + PLOT_H, PLOT_W + 2, 1, 0xFF2A2D34);
        GuiDraw.drawRect(PLOT_X - 1, PLOT_Y, 1, PLOT_H, 0xFF2A2D34);
        GuiDraw.drawRect(PLOT_X + PLOT_W, PLOT_Y, 1, PLOT_H, 0xFF2A2D34);

        FuelData fd = recipe.fuelData;
        double floorTemp = (fd != null) ? fd.floorTemp : (recipe.fuel != null ? recipe.fuel.floorTemp : 2300.0);
        double maxTemp = Math.ceil((floorTemp + 200.0) / 500.0) * 500.0;

        // Subtle neutral grid lines (10% white)
        for (int p = 25; p <= 100; p += 25) {
            int gy = PLOT_Y + PLOT_H - (int) Math.round((p / 100.0) * PLOT_H);
            GuiDraw.drawRect(PLOT_X, gy, PLOT_W, 1, 0x18FFFFFF);
        }

        // Vertical grid lines
        double step = (maxTemp > 3000.0) ? 1000.0 : 500.0;
        for (double t = step; t < maxTemp; t += step) {
            int gx = PLOT_X + (int) Math.round((t / maxTemp) * PLOT_W);
            GuiDraw.drawRect(gx, PLOT_Y, 1, PLOT_H, 0x18FFFFFF);
        }
    }

    @Override
    public void drawExtras(int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return;
        CachedRecipe cached = this.arecipes.get(recipeIndex);
        if (!(cached instanceof CachedFuelStatsRecipe recipe)) return;

        FuelData fd = recipe.fuelData;
        double peakTemp = (fd != null) ? fd.peakReactivityTemp
            : (recipe.fuel != null ? recipe.fuel.peakReactivityTemp : 770.0);
        double floorTemp = (fd != null) ? fd.floorTemp : (recipe.fuel != null ? recipe.fuel.floorTemp : 2300.0);
        double t10 = peakTemp + FuelDamageCurveSolver.P_10_PERCENT * (floorTemp - peakTemp);
        double maxTemp = Math.ceil((floorTemp + 200.0) / 500.0) * 500.0;

        PhysicsData physics = ReactorDataRegistry.getPhysics();
        double wear = (fd != null && fd.t10WearFactor != null && fd.t10WearFactor > 1.0) ? fd.t10WearFactor
            : (recipe.fuel != null && recipe.fuel.wearFactor > 1.0 ? recipe.fuel.wearFactor
                : (physics != null ? physics.t10WearFactor : FuelDamageCurveSolver.DEFAULT_T10_WEAR_FACTOR));

        FuelDamageCurveSolver.FuelCurveStats stats = FuelDamageCurveSolver
            .solve(recipe.fuel, peakTemp, floorTemp, AMBIENT_TEMP, wear);

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        boolean dark = isDarkTheme();
        int titleColor = dark ? 0xFFFFFFFF : 0x111111;
        int subtitleColor = dark ? 0xFFCCCCCC : 0x333333;
        EnumChatFormatting primaryFormat = dark ? EnumChatFormatting.WHITE : EnumChatFormatting.BLACK;
        EnumChatFormatting dimFormat = dark ? EnumChatFormatting.GRAY : EnumChatFormatting.DARK_GRAY;

        // Fuel display title (auto-scaled if longer than header width)
        String displayName = (fd != null) ? fd.name
            : (recipe.fuel != null ? recipe.fuel.displayName + " Fuel" : "Fuel");
        String title = EnumChatFormatting.BOLD + displayName;
        int titleWidth = font.getStringWidth(title);
        float titleScale = (titleWidth > 115) ? (115.0f / titleWidth) : 1.0f;
        drawScaledString(title, 36, 10, titleScale, titleColor);

        // Non-heading stats scaled at 0.75x to prevent right-edge clipping
        final float STATS_SCALE = 0.75f;

        // Line 1: Peak & Floor Reactivity Temperatures
        String l1 = String.format(
            Locale.US,
            "Peak: %s%.0f °C%s | Floor: %s%.0f °C",
            EnumChatFormatting.DARK_AQUA,
            peakTemp,
            dimFormat,
            EnumChatFormatting.RED,
            floorTemp);
        drawScaledString(l1, 36, 22, STATS_SCALE, subtitleColor);

        // Line 2: 10% Reactivity Point & Damage Ratio (calculated dynamically for sAmbientTemp)
        String l2 = String.format(
            Locale.US,
            "10%% Point: %s%.0f °C%s (Ratio: %s%s%s)",
            EnumChatFormatting.BLUE,
            t10,
            dimFormat,
            EnumChatFormatting.DARK_GREEN,
            NEINeutronInteractionHandler.formatPercentFromPercent(stats.damageRatioPercent),
            dimFormat);
        drawScaledString(l2, 12, 33, STATS_SCALE, subtitleColor);

        // Line 3: Fission Multiplier & Base Neutron Output
        double effFiss = (fd != null) ? fd.getEffectiveFissionMultiplier(physics)
            : (recipe.fuel != null ? recipe.fuel.getEffectiveFissionMultiplier(physics) : 1.0);
        double effFastFiss = (fd != null) ? fd.getEffectiveFastFissionMultiplier(physics)
            : (recipe.fuel != null ? recipe.fuel.getEffectiveFastFissionMultiplier(physics) : 0.1);
        int effNeutrons = (fd != null) ? fd.getEffectiveBaseNeutrons(physics)
            : (recipe.fuel != null ? recipe.fuel.getEffectiveBaseNeutrons(physics) : 4);

        String l3 = String.format(
            Locale.US,
            "Th: %s%sx%s | Fast: %s%sx%s | %s%d n/t",
            EnumChatFormatting.DARK_PURPLE,
            NEINeutronInteractionHandler.formatNumber(effFiss),
            dimFormat,
            EnumChatFormatting.BLUE,
            NEINeutronInteractionHandler.formatNumber(effFastFiss),
            dimFormat,
            EnumChatFormatting.DARK_RED,
            effNeutrons);
        drawScaledString(l3, 12, 44, STATS_SCALE, subtitleColor);

        // Line 4: Durability & Growth Parameter k (calculated dynamically for sAmbientTemp)
        int durability = (fd != null) ? fd.defaultDurability
            : (recipe.fuel != null ? recipe.fuel.defaultDurability : 160000);
        String l4 = String.format(
            Locale.US,
            "Durability: %s%,d%s | k: %s%.6f",
            EnumChatFormatting.DARK_GREEN,
            durability,
            dimFormat,
            primaryFormat,
            stats.k);
        drawScaledString(l4, 12, 55, STATS_SCALE, subtitleColor);

        // --- DYNAMIC VECTOR PLOT RENDERING ---
        double damageAtFloor = stats.calculateDamage(floorTemp);
        double maxDamage = Math.max(6.0, Math.ceil(damageAtFloor * 1.1));

        // Sub-pixel 0.5x labels for axes and legend
        GL11.glPushMatrix();
        GL11.glScalef(0.5f, 0.5f, 1.0f);

        // 1. Legend at top of plot screen (Amber & Coral Red)
        GuiDraw.drawRect((SCREEN_X + 6) * 2, (SCREEN_Y + 4) * 2 + 3, 10, 2, 0xFFF59E0B);
        font.drawString("Reactivity", (SCREEN_X + 13) * 2, (SCREEN_Y + 3) * 2, 0xFFF59E0B);

        GuiDraw.drawRect((SCREEN_X + 78) * 2, (SCREEN_Y + 4) * 2 + 3, 10, 2, 0xFFF43F5E);
        font.drawString("Durability loss / t", (SCREEN_X + 85) * 2, (SCREEN_Y + 3) * 2, 0xFFF43F5E);

        // 2. Left Axis: Reactivity % labels (Warm Gold)
        for (int p = 0; p <= 100; p += 25) {
            int gy = PLOT_Y + PLOT_H - (int) Math.round((p / 100.0) * PLOT_H);
            String pStr = p + "%";
            int sw = font.getStringWidth(pStr);
            font.drawString(pStr, (PLOT_X - 2) * 2 - sw, (gy - 2) * 2, 0xFFFBBF24);
        }

        // 3. Right Axis: Damage rate labels (Coral Red)
        for (int p = 0; p <= 100; p += 25) {
            int gy = PLOT_Y + PLOT_H - (int) Math.round((p / 100.0) * PLOT_H);
            double dVal = (p / 100.0) * maxDamage;
            String dStr = String.format(Locale.US, "%.1f", dVal);
            font.drawString(dStr, (PLOT_X + PLOT_W + 3) * 2, (gy - 2) * 2, 0xFFF87171);
        }

        // 4. Bottom Axis: Temperature labels (Neutral Silver)
        double step = (maxTemp > 3000.0) ? 1000.0 : 500.0;
        font.drawString("0", (PLOT_X - 1) * 2, (PLOT_Y + PLOT_H + 3) * 2, 0xFF9CA3AF);
        for (double t = step; t < maxTemp; t += step) {
            int gx = PLOT_X + (int) Math.round((t / maxTemp) * PLOT_W);
            String tStr = (t >= 1000.0 && t % 1000.0 == 0) ? String.format(Locale.US, "%.0fk", t / 1000.0)
                : String.valueOf((int) t);
            int sw = font.getStringWidth(tStr);
            font.drawString(tStr, gx * 2 - sw / 2, (PLOT_Y + PLOT_H + 3) * 2, 0xFF9CA3AF);
        }
        font.drawString("°C", (PLOT_X + PLOT_W + 1) * 2, (PLOT_Y + PLOT_H + 3) * 2, 0xFF9CA3AF);

        GL11.glPopMatrix();

        // 5. Draw Dynamic Anti-aliased Vector Curves
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(2.0f);

        // Reactivity curve (Golden Amber: #F59E0B)
        GL11.glColor4f(0.96f, 0.62f, 0.04f, 1.0f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= PLOT_W; i++) {
            double temp = (i / (double) PLOT_W) * maxTemp;
            double r = (fd != null) ? fd.calculateReactivity(temp)
                : (recipe.fuel != null ? recipe.fuel.calculateReactivity(temp) : 0.0);
            float y = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, r)) * PLOT_H);
            GL11.glVertex2f(PLOT_X + i, y);
        }
        GL11.glEnd();

        // Durability damage curve (Coral Red: #F43F5E)
        GL11.glColor4f(0.95f, 0.25f, 0.37f, 1.0f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= PLOT_W; i++) {
            double temp = (i / (double) PLOT_W) * maxTemp;
            double d = stats.calculateDamage(temp);
            float y = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, d / maxDamage)) * PLOT_H);
            GL11.glVertex2f(PLOT_X + i, y);
        }
        GL11.glEnd();

        // 6. Interactive Crosshair & Guide Lines on Hover
        Point mouseInRecipe = null;
        if (Minecraft.getMinecraft().currentScreen instanceof GuiRecipe<?>guiRecipe) {
            mouseInRecipe = getMouseInRecipe(guiRecipe, recipeIndex);
        }

        if (mouseInRecipe != null && isMouseOverPlot(mouseInRecipe.x, mouseInRecipe.y)) {
            int mx = mouseInRecipe.x;
            double tempAtMouse = ((mx - PLOT_X) / (double) PLOT_W) * maxTemp;
            double rAtMouse = (fd != null) ? fd.calculateReactivity(tempAtMouse)
                : (recipe.fuel != null ? recipe.fuel.calculateReactivity(tempAtMouse) : 0.0);
            double dAtMouse = stats.calculateDamage(tempAtMouse);
            float yr = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, rAtMouse)) * PLOT_H);
            float yd = (float) (PLOT_Y + PLOT_H - Math.max(0.0, Math.min(1.0, dAtMouse / maxDamage)) * PLOT_H);

            // Vertical cursor line (neutral white)
            GL11.glLineWidth(1.0f);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.6f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(mx, PLOT_Y);
            GL11.glVertex2f(mx, PLOT_Y + PLOT_H);
            GL11.glEnd();

            // Horizontal projection to left axis for Reactivity (Amber)
            GL11.glColor4f(0.96f, 0.62f, 0.04f, 0.5f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(PLOT_X, yr);
            GL11.glVertex2f(mx, yr);
            GL11.glEnd();

            // Horizontal projection to right axis for Damage (Coral Red)
            GL11.glColor4f(0.95f, 0.25f, 0.37f, 0.5f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(mx, yd);
            GL11.glVertex2f(PLOT_X + PLOT_W, yd);
            GL11.glEnd();

            // Snap indicator dots (diamonds)
            drawIndicatorDot(mx, yr, 0.96f, 0.62f, 0.04f);
            drawIndicatorDot(mx, yd, 0.95f, 0.25f, 0.37f);
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }

    private static void drawScaledString(String text, float x, float y, float scale, int color) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0f);
        GL11.glScalef(scale, scale, 1.0f);
        font.drawString(text, 0, 0, color, false);
        GL11.glPopMatrix();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawIndicatorDot(float x, float y, float r, float g, float b) {
        // Outer colored diamond (radius 2.5)
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x - 2.5f, y);
        GL11.glVertex2f(x, y + 2.5f);
        GL11.glVertex2f(x + 2.5f, y);
        GL11.glVertex2f(x, y - 2.5f);
        GL11.glEnd();

        // Inner white diamond core (radius 1.0)
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x - 1.0f, y);
        GL11.glVertex2f(x, y + 1.0f);
        GL11.glVertex2f(x + 1.0f, y);
        GL11.glVertex2f(x, y - 1.0f);
        GL11.glEnd();
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipeIndex) {
        currenttip = super.handleTooltip(gui, currenttip, recipeIndex);
        if (currenttip == null) currenttip = new ArrayList<>();
        if (recipeIndex < 0 || recipeIndex >= arecipes.size()) return currenttip;
        CachedRecipe cached = this.arecipes.get(recipeIndex);
        if (!(cached instanceof CachedFuelStatsRecipe recipe)) return currenttip;

        Point mouseInRecipe = getMouseInRecipe(gui, recipeIndex);
        if (mouseInRecipe != null) {
            FuelData fd = recipe.fuelData;
            double peakTemp = (fd != null) ? fd.peakReactivityTemp
                : (recipe.fuel != null ? recipe.fuel.peakReactivityTemp : 770.0);
            double floorTemp = (fd != null) ? fd.floorTemp : (recipe.fuel != null ? recipe.fuel.floorTemp : 2300.0);
            PhysicsData physics = ReactorDataRegistry.getPhysics();
            double wear = (fd != null && fd.t10WearFactor != null && fd.t10WearFactor > 1.0) ? fd.t10WearFactor
                : (recipe.fuel != null && recipe.fuel.wearFactor > 1.0 ? recipe.fuel.wearFactor
                    : (physics != null ? physics.t10WearFactor : FuelDamageCurveSolver.DEFAULT_T10_WEAR_FACTOR));

            FuelDamageCurveSolver.FuelCurveStats stats = FuelDamageCurveSolver
                .solve(recipe.fuel, peakTemp, floorTemp, AMBIENT_TEMP, wear);

            if (isMouseOverPlot(mouseInRecipe.x, mouseInRecipe.y)) {
                double maxTemp = Math.ceil((floorTemp + 200.0) / 500.0) * 500.0;
                double temp = ((mouseInRecipe.x - PLOT_X) / (double) PLOT_W) * maxTemp;
                double r = (fd != null) ? fd.calculateReactivity(temp)
                    : (recipe.fuel != null ? recipe.fuel.calculateReactivity(temp) : 0.0);
                double d = stats.calculateDamage(temp);

                currenttip.add(
                    EnumChatFormatting.WHITE + ""
                        + EnumChatFormatting.BOLD
                        + String.format(Locale.US, "%.0f °C", temp));

                String peakNote = (Math.abs(temp - peakTemp) <= (maxTemp / PLOT_W * 0.75))
                    ? EnumChatFormatting.GOLD + " (Peak)"
                    : "";
                currenttip.add(
                    EnumChatFormatting.YELLOW + "Reactivity: "
                        + NEINeutronInteractionHandler.formatNumber(r * 100.0)
                        + "%"
                        + peakNote);

                String dmgNote = (temp <= AMBIENT_TEMP) ? EnumChatFormatting.GRAY + " (None)" : "";
                currenttip.add(
                    EnumChatFormatting.RED + "Durability damage: "
                        + NEINeutronInteractionHandler.formatNumber(d)
                        + " / t"
                        + dmgNote);
            }
        }
        return currenttip;
    }
}
