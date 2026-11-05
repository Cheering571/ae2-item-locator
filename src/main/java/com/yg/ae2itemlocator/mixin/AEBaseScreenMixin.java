package com.yg.ae2itemlocator.mixin;

import appeng.client.gui.AEBaseScreen;
import com.yg.ae2itemlocator.client.DriveSlotOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs right after AE2 draws a slot, so the highlight border shares the exact coordinate space
 * and pose of the item it surrounds. Also puts the border above the item but below tooltips.
 */
@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class AEBaseScreenMixin {

    @Inject(method = "renderSlot", at = @At("TAIL"), require = 0, remap = false)
    private void ae2itemlocator$drawSlotHighlight(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        DriveSlotOverlay.renderSlot(graphics, (AEBaseScreen<?>) (Object) this, slot);
    }
}
