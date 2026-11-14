package petrolpark.mc.destroy.mixin.compat.create;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeSerializer;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternItem;
import petrolpark.mc.destroy.content.processing.trypolithography.recipe.CircuitSequencedAssemblyRecipe;

@Mixin(SequencedAssemblyRecipe.class)
public abstract class SequencedAssemblyRecipeMixin {

    @Shadow protected SequencedAssemblyRecipeSerializer serializer;
    @Shadow protected List<SequencedRecipe<?>> sequence;
    @Shadow protected int loops;

    @Shadow private int getStep(ItemStack stack) { throw new AssertionError(); }

    /**
     * Intercepts transitional item acquisition during intermediate steps.
     * When past step 0, uses the in-flight input stack to preserve accumulated components.
     */
    @WrapOperation(
            method = "advance",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/processing/sequenced/SequencedAssemblyRecipe;getTransitionalItem()Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack destroy$conserveInFlightComponents(
            SequencedAssemblyRecipe instance,
            Operation<ItemStack> original,
            ResourceLocation id,
            ItemStack input,
            RandomSource random
    ) {
        int step = getStep(input);
        if (step > 0) {
            return input;
        }
        return original.call(instance);
    }

    /**
     * Ensures circuit patterns are stamped onto the output for trypolithography recipes.
     */
    @Inject(
            method = "advance",
            at = @At("RETURN"),
            cancellable = true
    )
    private void destroy$carryCircuitPattern(
            ResourceLocation id,
            ItemStack input,
            RandomSource random,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (serializer instanceof CircuitSequencedAssemblyRecipe.Serializer) {
            ItemStack result = cir.getReturnValue();
            if (result != null && !result.isEmpty()) {
                CircuitPatternItem.putPattern(result, CircuitPatternItem.getPattern(input));
            }
        }
    }
}