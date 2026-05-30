package cn.dancingsnow.neoecoae.items;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SmithingTemplateItem;

import java.util.List;

/**
 * Custom {@link SmithingTemplateItem} subclass that uses a unique description
 * ID
 * per smithing template instead of sharing the vanilla
 * {@code item.minecraft.smithing_template} key.
 * <p>
 * This prevents duplicate translation key errors during Registrate datagen when
 * multiple smithing template items are registered.
 * </p>
 */
public class NESmithingTemplateItem extends SmithingTemplateItem {

    private final String descriptionId;

    public NESmithingTemplateItem(
            String descriptionId,
            Component appliesTo,
            Component ingredients,
            Component upgradeDescription,
            Component baseSlotDescription,
            Component additionsSlotDescription,
            List<ResourceLocation> baseSlotEmptyIcons,
            List<ResourceLocation> additionalSlotEmptyIcons) {
        super(appliesTo, ingredients, upgradeDescription,
                baseSlotDescription, additionsSlotDescription,
                baseSlotEmptyIcons, additionalSlotEmptyIcons);
        this.descriptionId = descriptionId;
    }

    @Override
    public String getDescriptionId() {
        return descriptionId;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return descriptionId;
    }
}
