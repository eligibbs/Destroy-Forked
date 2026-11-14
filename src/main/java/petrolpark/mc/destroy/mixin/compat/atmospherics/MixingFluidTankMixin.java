package petrolpark.mc.destroy.mixin.compat.atmospherics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import petrolpark.mc.destroy.core.fluid.GeniusFluidTankBehaviour;

@Pseudo
@Mixin(targets = "com.enzonic.aeronauticswinds.thermal.MixingFluidTank", remap = false)
public abstract class MixingFluidTankMixin {

    @Inject(method = "fill", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void destroy$mergeMixtures(FluidStack resource, FluidAction action,
                                       CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() != 0) return;

        int merged = GeniusFluidTankBehaviour.tryMergeMixture((SmartFluidTank) (Object) this, resource, action);
        if (merged > 0) cir.setReturnValue(merged);
    }
}
