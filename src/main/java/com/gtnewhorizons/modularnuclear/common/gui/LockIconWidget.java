package com.gtnewhorizons.modularnuclear.common.gui;

import java.util.function.BooleanSupplier;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

/**
 * A 10x10 lock icon showing whether auto-fit on window resize is locked. Its two states are halves of gui/lock.png, so
 * resource packs can restyle it.
 */
public class LockIconWidget extends Widget<LockIconWidget> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("modularnuclear", "textures/gui/lock.png");
    private static final UITexture LOCKED = UITexture.builder()
        .location(TEXTURE)
        .imageSize(20, 10)
        .xy(0, 0, 10, 10)
        .build();
    private static final UITexture UNLOCKED = UITexture.builder()
        .location(TEXTURE)
        .imageSize(20, 10)
        .xy(10, 0, 10, 10)
        .build();

    private final BooleanSupplier lockedSupplier;

    public LockIconWidget(BooleanSupplier lockedSupplier) {
        this.lockedSupplier = lockedSupplier;
        size(10, 10);
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        boolean locked = lockedSupplier != null && lockedSupplier.getAsBoolean();
        (locked ? LOCKED : UNLOCKED).draw(0, 0, 10, 10);
    }
}
