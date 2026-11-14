package petrolpark.mc.destroy.core.chemistry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeItem;

/** Creates the item given when a molecule is cheated from a recipe viewer. */
public final class MoleculeCheatItems {

    private static final ItemStack ILLEGAL_FISH;
    static {
        ILLEGAL_FISH = new ItemStack(Items.COD);
        ILLEGAL_FISH.set(DataComponents.CUSTOM_NAME, Component.literal("Impossible Fish"));
    }

    private MoleculeCheatItems() {}

    public static ItemStack of(LegacySpecies ingredient) {
        if (ingredient == null) return ItemStack.EMPTY;
        if (ingredient.isHypothetical() || ingredient == DestroyMolecules.PROTON) return ILLEGAL_FISH.copy();

        LegacyMixture mixture;
        if (ingredient.getBoilingPoint() > 273f) {
            mixture = LegacyMixture.pure(ingredient);
        } else {
            // LegacyMixture.pure uses liquid density; gases need the ideal-gas density at RTP.
            mixture = new LegacyMixture();
            mixture.addMolecule(ingredient, (float) DestroyFluids.AIR_MOLAR_DENSITY);
        }
        return TestTubeItem.of(DestroyItems.TEST_TUBE.asStack(), mixture);
    }
}
