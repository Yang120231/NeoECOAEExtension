package cn.dancingsnow.neoecoae.compat.appmek;

import cn.dancingsnow.neoecoae.all.NECreativeTabs;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.compat.ae2.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.compat.appmek.item.ECOChemicalStorageCellItem;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.common.crafting.ConditionalRecipe;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

/**
 * Registers Applied Mekanistics chemical storage cell housing and cells.
 * <p>
 * Item registration only occurs when the {@code appmek} mod is loaded,
 * via the
 * {@link cn.dancingsnow.neoecoae.api.integration.Integration @Integration}
 * annotation on {@link AppMekIntegration}.
 * <p>
 * All AppMek recipe outputs (including disassembly recipes) are guarded by
 * {@code forge:mod_loaded("appmek")} via {@link ConditionalRecipe}
 * during datagen.
 * </p>
 */
public class NEAppMekItems {
        static {
                REGISTRATE.defaultCreativeTab(NECreativeTabs.ECO);
        }

        // ═══════════════════════════════════════════════════════════════
        // Chemical Cell Housing
        // ═══════════════════════════════════════════════════════════════

        public static final ItemEntry<net.minecraft.world.item.Item> ECO_CHEMICAL_CELL_HOUSING = REGISTRATE
                        .item("eco_chemical_cell_housing", net.minecraft.world.item.Item::new)
                        .recipe((ctx, prov) -> {
                                var shaped = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get())
                                                .pattern("ABA")
                                                .pattern("B B")
                                                .pattern("CCC")
                                                .define('A', NEItems.CRYSTAL_MATRIX)
                                                .define('B', Tags.Items.DUSTS_REDSTONE)
                                                .define('C', NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT)
                                                .unlockedBy("has_crystal_matrix",
                                                                RegistrateRecipeProvider.has(NEItems.CRYSTAL_MATRIX))
                                                .unlockedBy("has_redstone",
                                                                RegistrateRecipeProvider.has(Tags.Items.DUSTS_REDSTONE))
                                                .unlockedBy("has_black_tungsten_alloy",
                                                                RegistrateRecipeProvider.has(
                                                                                NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT));
                                ConditionalRecipe.builder()
                                                .addCondition(new ModLoadedCondition("appmek"))
                                                .addRecipe(shaped::save)
                                                .build(prov, ctx.getId());
                        })
                        .lang("ECO Storage Matrix Housing (Chemical)")
                        .register();

        // ═══════════════════════════════════════════════════════════════
        // Chemical Storage Cells
        // ═══════════════════════════════════════════════════════════════

        public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_16M = REGISTRATE
                        .item("eco_chemical_storage_cell_16m", p -> new ECOChemicalStorageCellItem(
                                        p.stacksTo(1).rarity(Rarity.UNCOMMON),
                                        ECOTier.L4))
                        .recipe((ctx, prov) -> {
                                var shapeless = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                                                .requires(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING)
                                                .requires(NEItems.ECO_CELL_COMPONENT_16M)
                                                .unlockedBy("has_16m_component", RegistrateRecipeProvider
                                                                .has(NEItems.ECO_CELL_COMPONENT_16M));
                                ConditionalRecipe.builder()
                                                .addCondition(new ModLoadedCondition("appmek"))
                                                .addRecipe(shapeless::save)
                                                .build(prov, ctx.getId());
                                StorageCellDisassemblyRecipe disassembly = new StorageCellDisassemblyRecipe(
                                                ctx.get(),
                                                List.of(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING.asStack(),
                                                                NEItems.ECO_CELL_COMPONENT_16M.asStack()));
                                ConditionalRecipe.builder()
                                                .addCondition(new ModLoadedCondition("appmek"))
                                                .addRecipe(r -> prov.accept(disassembly))
                                                .build(prov, ctx.getId().withSuffix("_disassembly"));
                        })
                        .lang("ECO - LE4 Storage Matrix (Chemical)")
                        .register();

        public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_64M = REGISTRATE
                        .item("eco_chemical_storage_cell_64m", p -> new ECOChemicalStorageCellItem(
                                        p.stacksTo(1).rarity(Rarity.RARE),
                                        ECOTier.L6))
                        .recipe((ctx, prov) -> {
                                var shapeless = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                                                .requires(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING)
                                                .requires(NEItems.ECO_CELL_COMPONENT_64M)
                                                .unlockedBy("has_64m_component", RegistrateRecipeProvider
                                                                .has(NEItems.ECO_CELL_COMPONENT_64M));
                                ConditionalRecipe.builder()
                                                .addCondition(new ModLoadedCondition("appmek"))
                                                .addRecipe(shapeless::save)
                                                .build(prov, ctx.getId());
                                StorageCellDisassemblyRecipe disassembly = new StorageCellDisassemblyRecipe(
                                                ctx.get(),
                                                List.of(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING.asStack(),
                                                                NEItems.ECO_CELL_COMPONENT_64M.asStack()));
                                ConditionalRecipe.builder()
                                                .addCondition(new ModLoadedCondition("appmek"))
                                                .addRecipe(r -> prov.accept(disassembly))
                                                .build(prov, ctx.getId().withSuffix("_disassembly"));
                        })
                        .lang("ECO - LE6 Storage Matrix (Chemical)")
                        .register();

        public static final ItemEntry<ECOChemicalStorageCellItem> ECO_CHEMICAL_CELL_256M = REGISTRATE
                        .item("eco_chemical_storage_cell_256m", p -> new ECOChemicalStorageCellItem(
                                        p.stacksTo(1).rarity(Rarity.EPIC),
                                        ECOTier.L9))
                        .recipe((ctx, prov) -> {
                                var shapeless = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get())
                                                .requires(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING)
                                                .requires(NEItems.ECO_CELL_COMPONENT_256M)
                                                .unlockedBy("has_256m_component", RegistrateRecipeProvider
                                                                .has(NEItems.ECO_CELL_COMPONENT_256M));
                                ConditionalRecipe.builder()
                                                .addCondition(new ModLoadedCondition("appmek"))
                                                .addRecipe(shapeless::save)
                                                .build(prov, ctx.getId());
                                StorageCellDisassemblyRecipe disassembly = new StorageCellDisassemblyRecipe(
                                                ctx.get(),
                                                List.of(NEAppMekItems.ECO_CHEMICAL_CELL_HOUSING.asStack(),
                                                                NEItems.ECO_CELL_COMPONENT_256M.asStack()));
                                ConditionalRecipe.builder()
                                                .addCondition(new ModLoadedCondition("appmek"))
                                                .addRecipe(r -> prov.accept(disassembly))
                                                .build(prov, ctx.getId().withSuffix("_disassembly"));
                        })
                        .lang("ECO - LE9 Storage Matrix (Chemical)")
                        .register();

        public static void register() {
        }
}
