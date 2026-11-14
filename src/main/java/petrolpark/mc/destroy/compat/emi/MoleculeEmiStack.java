package petrolpark.mc.destroy.compat.emi;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.config.DestroyAllConfigs;
import petrolpark.mc.destroy.core.chemistry.MoleculeCheatItems;
import petrolpark.mc.destroy.core.chemistry.MoleculeDisplayItem;
import petrolpark.mc.destroy.core.chemistry.MoleculeRenderer;

/**
 * EMI ingredient wrapper for Destroy molecules ({@link LegacySpecies}). Mirrors
 * {@link petrolpark.mc.destroy.compat.jei.MoleculeJEIIngredient} but for the EMI API,
 * letting molecules participate in EMI recipe slots / lookup / search the same way
 * vanilla items and fluids do.
 *
 * <p>Key behaviour:
 * <ul>
 *   <li>{@code getKey()} returns the {@link LegacySpecies} itself — EMI uses key equality
 *       for stack-vs-stack identity, and molecules are immutable singletons keyed by
 *       molecule identity, so referential equality matches structural equality.</li>
 *   <li>{@code getAmount()} carries the molar amount required by the recipe slot
 *       (not the GCD-reduced display ratio EMI would otherwise normalise to). Reactions
 *       are constructed with their raw stoichiometric mol counts (e.g. borax precipitation
 *       at 15:30:60) so the user sees actual quantities.</li>
 *   <li>Rendering reuses Destroy's own {@link MoleculeRenderer} so the molecule glyph
 *       (atom symbols, bonds, charge superscript) renders identically to the JEI side
 *       and the in-game tooltip.</li>
 *   <li>Tooltip text mirrors {@link MoleculeDisplayItem#getLore} so search / hover gives
 *       the same molecular formula, BP, density, tags, etc.</li>
 * </ul>
 */
public class MoleculeEmiStack extends EmiStack {

    private static final ResourceLocation FONT_LOCATION = Destroy.asResource("charge");
    private static final Style CHARGE_FONT = Style.EMPTY.withFont(FONT_LOCATION);

    private final LegacySpecies molecule;

    public MoleculeEmiStack(LegacySpecies molecule, long molAmount) {
        this.molecule = molecule;
        this.amount = molAmount;
    }

    public MoleculeEmiStack(LegacySpecies molecule) {
        this(molecule, 1L);
    }

    public LegacySpecies getMolecule() {
        return molecule;
    }

    @Override
    public ItemStack getItemStack() {
        return molecule == null ? ItemStack.EMPTY : MoleculeCheatItems.of(molecule);
    }

    @Override
    public EmiStack copy() {
        MoleculeEmiStack copy = new MoleculeEmiStack(molecule, amount);
        copy.setChance(getChance());
        copy.setRemainder(getRemainder().copy());
        return copy;
    }

    @Override
    public boolean isEmpty() {
        return molecule == null;
    }

    @Override
    public DataComponentPatch getComponentChanges() {
        return DataComponentPatch.EMPTY;
    }

    @Override
    public Object getKey() {
        return molecule;
    }

    @Override
    public ResourceLocation getId() {
        if (molecule == null) return Destroy.asResource("empty");
        if (molecule.isNovel()) return Destroy.asResource("novel_molecule");
        // LegacySpecies.getFullID() returns "namespace:id" — parseable.
        return ResourceLocation.parse(molecule.getFullID());
    }

    @Override
    public Component getName() {
        return molecule.getName(DestroyAllConfigs.CLIENT.chemistry.iupacNames.get());
    }

    @Override
    public List<Component> getTooltipText() {
        List<Component> lines = new ArrayList<>();
        lines.add(getName());
        lines.addAll(MoleculeDisplayItem.getLore(molecule));
        return lines;
    }

    /**
     * Hover tooltip — converts the molecule's display data into the
     * {@link ClientTooltipComponent} list EMI's slot widgets actually render.
     *
     * <p><b>Why this override exists:</b> {@link EmiStack#getTooltip} only inserts a
     * {@code RemainderTooltipComponent} if a non-empty remainder is set. It does <i>not</i>
     * fall back to {@link #getTooltipText}. Without this override, hovering a molecule
     * slot shows an empty tooltip.</p>
     *
     * <p>Component order mirrors JEI's
     * {@code MoleculeJEIIngredient.MoleculeJEIIngredientRenderer.getTooltip} — name, then
     * the rendered molecular-structure glyph (so the player can see the atom-and-bond
     * drawing inside the tooltip card), then the property lore (formula, boiling point,
     * density, tags, etc.).</p>
     */
    @Override
    public List<ClientTooltipComponent> getTooltip() {
        List<ClientTooltipComponent> list = new ArrayList<>();
        if (molecule != null) {
            boolean iupac = DestroyAllConfigs.CLIENT.chemistry.iupacNames.get();
            list.add(ClientTooltipComponent.create(molecule.getName(iupac).getVisualOrderText()));
            // Glyph component — renders the actual molecular-structure drawing inside
            // the tooltip via MoleculeRenderer. Construct the carrier MoleculeTooltip
            // then ask it for the ClientTooltipComponent, the same path the in-game
            // MoleculeDisplayItem hover follows.
            list.add(new MoleculeDisplayItem.ClientMoleculeTooltipComponent(
                new MoleculeDisplayItem.MoleculeTooltip(molecule)));
            for (Component line : MoleculeDisplayItem.getLore(molecule)) {
                list.add(ClientTooltipComponent.create(line.getVisualOrderText()));
            }
        }
        list.addAll(super.getTooltip());
        return list;
    }

    /**
     * Slot-level rendering — EMI calls this with the standard
     * {@code RENDER_ICON | RENDER_AMOUNT | RENDER_INGREDIENT} flag set when drawing the
     * stack inside a {@link dev.emi.emi.api.widget.SlotWidget}. The icon path delegates
     * to Destroy's {@link MoleculeRenderer} so the molecular-structure glyph (atom
     * symbols, bonds, optional charge superscript) shows up identically to the JEI side
     * and the in-game vat HUD. Amount rendering is left to EMI's default — pulling
     * {@code getAmount()} as a label below the slot — so the raw stoichiometric mole
     * count (e.g. 15:30:60 for borax precipitation) renders rather than the GCD-reduced
     * 1:2:4 EMI's auto-bridge from JEI would produce.
     */
    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
        if (molecule == null) return;
        if ((flags & EmiIngredient.RENDER_ICON) != 0) {
            renderMolecule(graphics, x, y);
        }
        if ((flags & EmiIngredient.RENDER_AMOUNT) != 0) {
            // Default EMI amount overlay: the bottom-right number on the slot.
            String amountText = String.valueOf(amount);
            PoseStack ps = graphics.pose();
            ps.pushPose();
            ps.translate(0, 0, 200);
            Font font = Minecraft.getInstance().font;
            graphics.drawString(font, amountText,
                x + 19 - 2 - font.width(amountText), y + 6 + 3, 0xFFFFFF, true);
            ps.popPose();
        }
    }

    private void renderMolecule(GuiGraphics graphics, int x, int y) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);

        if (DestroyAllConfigs.CLIENT.chemistry.fancyJEIRendering.get()) {
            MoleculeRenderer renderer = molecule.getRenderer();
            MultiBufferSource.BufferSource buffer = graphics.bufferSource();
            renderer.renderItem(0, 0, 16, 16, poseStack, buffer);
            buffer.endBatch();

            if (molecule.getCharge() != 0) {
                String s = molecule.getCharge() > 0 ? "+" : "-";
                if (Math.abs(molecule.getCharge()) > 1) {
                    s = Math.abs(molecule.getCharge()) + s;
                }
                poseStack.pushPose();
                poseStack.translate(0, 0, 100);
                Font font = Minecraft.getInstance().font;
                FormattedCharSequence chargeText = FormattedCharSequence.forward(s, CHARGE_FONT);
                graphics.drawString(font, chargeText, 17 - font.width(chargeText), -1, 0xFFFFFF, true);
                poseStack.popPose();
            }
        } else {
            graphics.renderItem(MoleculeDisplayItem.with(molecule), 0, 0);
        }
        poseStack.popPose();
    }
}
