package com.yg.ae2itemlocator.mixin;

import appeng.client.gui.AEBaseScreen;
import com.yg.ae2itemlocator.client.DriveSlotOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs right after a slot is drawn, so the highlight border shares the exact coordinate space and
 * pose of the item it surrounds, and lands above the item but below tooltips.
 *
 * <p>AE2 19 overrides {@code renderSlot} on {@code AEBaseScreen} itself; the 1.20.1 line of AE2
 * (15.x) does not, so this hooks the vanilla method and filters to AE2 screens.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AEBaseScreenMixin {

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void ae2itemlocator$drawSlotHighlight(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if ((Object) this instanceof AEBaseScreen<?> screen) {
            DriveSlotOverlay.renderSlot(graphics, screen, slot);
        }
    }
}
