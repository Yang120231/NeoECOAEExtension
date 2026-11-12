package cn.dancingsnow.neoecoae.compat.gto;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.platform.recipe.RecipeJson;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtolib.api.recipe.RecipeBuilder;
import com.gtolib.api.recipe.RecipeType;
import com.gtolib.utils.RegistriesUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.LoggerFactory;

/** Configure ECO recipes; GTO requires recipeBuilder/save to be called from its own Data.commonInit. */
public final class ECOGtoMachineRecipes {
    private static final String[] IDS = {
        "compat/gt/bootstrap/b01_aluminum_alloy_dust",
        "compat/gt/bootstrap/b02_black_tungsten_alloy_dust",
        "compat/gt/bootstrap/b03_aluminum_alloy_ingot",
        "compat/gt/bootstrap/b04_black_tungsten_alloy_ingot",
        "compat/gt/bootstrap/b05_energized_crystal",
        "compat/gt/bootstrap/b06_energized_fluix_crystal",
        "compat/gt/bootstrap/b07_crystal_ingot",
        "compat/gt/bootstrap/b08_energized_superconductive_ingot",
        "compat/gt/bootstrap/b09_superconducting_processor",
        "compat/gt/bootstrap/b10_integrated_working_station",
        "compat/gt/bootstrap/b11_aluminum_alloy_casing",
        "compat/gt/bootstrap/b12_black_tungsten_alloy_casing",
        "compat/gt/bootstrap/b13_crafting_casing",
        "compat/gt/bootstrap/b14_storage_casing",
        "compat/gt/bootstrap/b15_computation_casing",
        "compat/gt/bootstrap/b16_integrated_working_station_module_empty",
        "compat/gt/material_processing/c01_energized_crystal_dust",
        "compat/gt/material_processing/c02_energized_fluix_crystal_dust",
        "compat/gt/material_processing/c03_superconducting_processor",
        "compat/gt/ore_processing/o01_raw_aluminum_ore",
        "compat/gt/ore_processing/o02_aluminium_dust",
        "compat/gt/ore_processing/o03_raw_tungsten_ore",
        "compat/gt/ore_processing/o04_tungsten_dust",
    };

    private ECOGtoMachineRecipes() {}

    public static int count() { return IDS.length; }

    public static ResourceLocation id(int index) { return NeoECOAE.id(IDS[index]); }

    public static RecipeType type(int index) {
        return (RecipeType) switch (index) {
            case 0, 1, 6, 7 -> GTRecipeTypes.MIXER_RECIPES;
            case 2 -> GTRecipeTypes.ALLOY_SMELTER_RECIPES;
            case 3 -> GTRecipeTypes.BLAST_RECIPES;
            case 4, 5 -> GTRecipeTypes.AUTOCLAVE_RECIPES;
            case 8 -> GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES;
            case 9, 10, 11, 12, 13, 14, 15 -> GTRecipeTypes.ASSEMBLER_RECIPES;
            case 16, 17, 19, 20, 21, 22 -> GTRecipeTypes.MACERATOR_RECIPES;
            case 18 -> GTRecipeTypes.FORMING_PRESS_RECIPES;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    public static void configure(RecipeBuilder builder, int index) {
        switch (index) {
            case 0 -> builder // b01_aluminum_alloy_dust
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/iron")), 1)
                .inputItems(ItemTags.create(new ResourceLocation("neoecoae:compat/gt/aluminium_dusts")), 1)
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/certus_quartz")), 2)
                .outputItems(RegistriesUtils.getItem("neoecoae:aluminum_alloy_dust"), 1)
                .duration(200)
                .EUt(120);
            case 1 -> builder // b02_black_tungsten_alloy_dust
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/tungsten")), 1)
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/aluminum_alloy")), 1)
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/fluix")), 2)
                .outputItems(RegistriesUtils.getItem("neoecoae:black_tungsten_alloy_dust"), 1)
                .duration(200)
                .EUt(480);
            case 2 -> builder // b03_aluminum_alloy_ingot
                .inputItems(RegistriesUtils.getItem("neoecoae:aluminum_alloy_dust"), 1)
                .outputItems(RegistriesUtils.getItem("neoecoae:aluminum_alloy_ingot"), 1)
                .duration(200)
                .EUt(120);
            case 3 -> builder // b04_black_tungsten_alloy_ingot
                .inputItems(RegistriesUtils.getItem("neoecoae:black_tungsten_alloy_dust"), 1)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:argon"), 100))
                .outputItems(RegistriesUtils.getItem("neoecoae:black_tungsten_alloy_ingot"), 1)
                .blastFurnaceTemp(3600)
                .duration(400)
                .EUt(1920);
            case 4 -> builder // b05_energized_crystal
                .inputItems(RegistriesUtils.getItem("ae2:charged_certus_quartz_crystal"), 4)
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/energized_crystal")), 4)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:distilled_water"), 250))
                .outputItems(RegistriesUtils.getItem("neoecoae:energized_crystal"), 8)
                .duration(100)
                .EUt(480);
            case 5 -> builder // b06_energized_fluix_crystal
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/energized_crystal")), 8)
                .inputItems(ItemTags.create(new ResourceLocation("forge:gems/fluix")), 8)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:distilled_water"), 250))
                .outputItems(RegistriesUtils.getItem("neoecoae:energized_fluix_crystal"), 8)
                .duration(100)
                .EUt(480);
            case 6 -> builder // b07_crystal_ingot
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/certus_quartz")), 4)
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/fluix")), 4)
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/energized_crystal")), 4)
                .inputItems(ItemTags.create(new ResourceLocation("neoecoae:compat/gt/crystal_bases")), 4)
                .inputItems(RegistriesUtils.getItem("gtceu:quartzite_dust"), 4)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:argon"), 2000))
                .outputItems(RegistriesUtils.getItem("neoecoae:crystal_ingot"), 4)
                .duration(400)
                .EUt(480);
            case 7 -> builder // b08_energized_superconductive_ingot
                .inputItems(ItemTags.create(new ResourceLocation("forge:dusts/energized_fluix_crystal")), 4)
                .inputItems(ItemTags.create(new ResourceLocation("neoecoae:compat/gt/aluminium_dusts")), 4)
                .inputItems(ItemTags.create(new ResourceLocation("neoecoae:compat/gt/silicon")), 4)
                .inputItems(ItemTags.create(new ResourceLocation("neoecoae:compat/gt/superconductor_ingots")), 4)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:argon"), 2000))
                .outputItems(RegistriesUtils.getItem("neoecoae:energized_superconductive_ingot"), 4)
                .duration(400)
                .EUt(1920);
            case 8 -> builder // b09_superconducting_processor
                .inputItems(RegistriesUtils.getItem("neoecoae:superconducting_processor_print"), 4)
                .inputItems(RegistriesUtils.getItem("neoecoae:crystal_matrix"), 4)
                .inputItems(ItemTags.create(new ResourceLocation("neoecoae:compat/gt/silicon_plates")), 4)
                .inputItems(ItemTags.create(new ResourceLocation("gtceu:circuits/hv")), 1)
                .inputItems(RegistriesUtils.getItem("gtceu:fine_gold_wire"), 8)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:soldering_alloy"), 144))
                .outputItems(RegistriesUtils.getItem("neoecoae:superconducting_processor"), 4)
                .cleanroom(CleanroomType.CLEANROOM)
                .duration(200)
                .EUt(1920);
            case 9 -> builder // b10_integrated_working_station
                .inputItems(RegistriesUtils.getItem("gtceu:ev_machine_hull"), 1)
                .inputItems(RegistriesUtils.getItem("neoecoae:aluminum_alloy_casing"), 4)
                .inputItems(ItemTags.create(new ResourceLocation("gtceu:circuits/ev")), 4)
                .inputItems(RegistriesUtils.getItem("ae2:molecular_assembler"), 1)
                .inputItems(RegistriesUtils.getItem("ae2:256k_crafting_storage"), 1)
                .inputItems(RegistriesUtils.getItem("ae2:cell_workbench"), 1)
                .inputItems(RegistriesUtils.getItem("neoecoae:superconducting_processor"), 2)
                .inputItems(RegistriesUtils.getItem("ae2:interface"), 1)
                .inputItems(RegistriesUtils.getItem("gtceu:ev_electric_motor"), 2)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:soldering_alloy"), 576))
                .outputItems(RegistriesUtils.getItem("neoecoae:integrated_working_station"), 1)
                .duration(600)
                .EUt(1920);
            case 10 -> builder // b11_aluminum_alloy_casing
                .inputItems(RegistriesUtils.getItem("neoecoae:aluminum_alloy_ingot"), 4)
                .inputItems(RegistriesUtils.getItem("gtceu:aluminium_plate"), 4)
                .inputItems(RegistriesUtils.getItem("ae2:quartz_vibrant_glass"), 4)
                .inputItems(RegistriesUtils.getItem("neoecoae:crystal_ingot"), 1)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:soldering_alloy"), 144))
                .outputItems(RegistriesUtils.getItem("neoecoae:aluminum_alloy_casing"), 2)
                .duration(100)
                .EUt(480);
            case 11 -> builder // b12_black_tungsten_alloy_casing
                .inputItems(RegistriesUtils.getItem("neoecoae:black_tungsten_alloy_ingot"), 4)
                .inputItems(RegistriesUtils.getItem("gtceu:titanium_plate"), 4)
                .inputItems(RegistriesUtils.getItem("ae2:quartz_vibrant_glass"), 4)
                .inputItems(RegistriesUtils.getItem("neoecoae:crystal_ingot"), 1)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:soldering_alloy"), 144))
                .outputItems(RegistriesUtils.getItem("neoecoae:black_tungsten_alloy_casing"), 2)
                .duration(200)
                .EUt(1920);
            case 12 -> builder // b13_crafting_casing
                .inputItems(RegistriesUtils.getItem("neoecoae:black_tungsten_alloy_casing"), 4)
                .inputItems(RegistriesUtils.getItem("ae2:engineering_processor"), 2)
                .inputItems(ItemTags.create(new ResourceLocation("forge:storage_blocks/energized_fluix_crystal")), 1)
                .inputItems(ItemTags.create(new ResourceLocation("gtceu:circuits/ev")), 2)
                .inputItems(RegistriesUtils.getItem("gtceu:stainless_steel_plate"), 4)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:soldering_alloy"), 288))
                .outputItems(RegistriesUtils.getItem("neoecoae:crafting_casing"), 1)
                .duration(200)
                .EUt(1920);
            case 13 -> builder // b14_storage_casing
                .inputItems(RegistriesUtils.getItem("neoecoae:aluminum_alloy_casing"), 4)
                .inputItems(RegistriesUtils.getItem("ae2:logic_processor"), 2)
                .inputItems(ItemTags.create(new ResourceLocation("forge:storage_blocks/energized_fluix_crystal")), 1)
                .inputItems(ItemTags.create(new ResourceLocation("gtceu:circuits/ev")), 2)
                .inputItems(RegistriesUtils.getItem("gtceu:titanium_plate"), 4)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:soldering_alloy"), 288))
                .outputItems(RegistriesUtils.getItem("neoecoae:storage_casing"), 1)
                .duration(200)
                .EUt(1920);
            case 14 -> builder // b15_computation_casing
                .inputItems(RegistriesUtils.getItem("neoecoae:aluminum_alloy_casing"), 4)
                .inputItems(RegistriesUtils.getItem("ae2:calculation_processor"), 2)
                .inputItems(ItemTags.create(new ResourceLocation("forge:storage_blocks/energized_fluix_crystal")), 1)
                .inputItems(ItemTags.create(new ResourceLocation("gtceu:circuits/ev")), 2)
                .inputItems(RegistriesUtils.getItem("gtceu:titanium_plate"), 4)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:soldering_alloy"), 288))
                .outputItems(RegistriesUtils.getItem("neoecoae:computation_casing"), 1)
                .duration(200)
                .EUt(1920);
            case 15 -> builder // b16_integrated_working_station_module_empty
                .inputItems(RegistriesUtils.getItem("ae2:advanced_card"), 1)
                .inputItems(RegistriesUtils.getItem("gtceu:polyethylene_plate"), 4)
                .inputItems(RegistriesUtils.getItem("neoecoae:superconducting_processor"), 2)
                .inputItems(ItemTags.create(new ResourceLocation("gtceu:circuits/hv")), 2)
                .inputFluids(new FluidStack(RecipeJson.fluid("gtceu:soldering_alloy"), 144))
                .outputItems(RegistriesUtils.getItem("neoecoae:integrated_working_station_module_empty"), 4)
                .duration(100)
                .EUt(480);
            case 16 -> builder // c01_energized_crystal_dust
                .inputItems(ItemTags.create(new ResourceLocation("forge:gems/energized_crystal")), 1)
                .outputItems(RegistriesUtils.getItem("neoecoae:energized_crystal_dust"), 1)
                .duration(100)
                .EUt(30);
            case 17 -> builder // c02_energized_fluix_crystal_dust
                .inputItems(ItemTags.create(new ResourceLocation("forge:gems/energized_fluix_crystal")), 1)
                .outputItems(RegistriesUtils.getItem("neoecoae:energized_fluix_crystal_dust"), 1)
                .duration(100)
                .EUt(30);
            case 18 -> builder // c03_superconducting_processor
                .notConsumable(RegistriesUtils.getItem("neoecoae:superconducting_processor_press"))
                .notConsumable(RegistriesUtils.getItem("ae2:silicon_press"))
                .inputItems(RegistriesUtils.getItem("neoecoae:energized_superconductive_ingot"), 1)
                .inputItems(ItemTags.create(new ResourceLocation("neoecoae:compat/gt/silicon_plates")), 1)
                .inputItems(RegistriesUtils.getItem("neoecoae:crystal_matrix"), 1)
                .outputItems(RegistriesUtils.getItem("neoecoae:superconducting_processor"), 1)
                .duration(40)
                .EUt(1920);
            case 19 -> builder // o01_raw_aluminum_ore
                .inputItems(RegistriesUtils.getItem("neoecoae:aluminum_ore"), 1)
                .outputItems(RegistriesUtils.getItem("neoecoae:raw_aluminum_ore"), 2)
                .duration(200)
                .EUt(120);
            case 20 -> builder // o02_aluminium_dust
                .inputItems(RegistriesUtils.getItem("neoecoae:raw_aluminum_ore"), 1)
                .outputItems(RegistriesUtils.getItem("gtceu:aluminium_dust"), 1)
                .duration(200)
                .EUt(120);
            case 21 -> builder // o03_raw_tungsten_ore
                .inputItems(RegistriesUtils.getItem("neoecoae:tungsten_ore"), 1)
                .outputItems(RegistriesUtils.getItem("neoecoae:raw_tungsten_ore"), 2)
                .duration(200)
                .EUt(480);
            case 22 -> builder // o04_tungsten_dust
                .inputItems(RegistriesUtils.getItem("neoecoae:raw_tungsten_ore"), 1)
                .outputItems(RegistriesUtils.getItem("gtceu:tungsten_dust"), 1)
                .duration(400)
                .EUt(1920);
            default -> throw new IndexOutOfBoundsException(index);
        }
    }

    private static ResourceLocation registeredId(int index) {
        return id(index).withPrefix(type(index).registryName.getPath() + "/");
    }

    /** A filtered native save returns a dummy definition instead of throwing. Never report it as registered. */
    public static void verifySaved(GTRecipeDefinition recipe, int index) {
        if (recipe == null || !recipe.registered || recipe.recipeType != type(index)
                || !recipe.id.equals(registeredId(index))) {
            throw new IllegalStateException("GTO rejected ECO machine recipe " + id(index));
        }
    }

    /** Run after GTO finishes populating the maps used by machine searches and EMI. */
    public static void verifyFinalized() {
        for (int index = 0; index < count(); index++) {
            var recipe = RecipeBuilder.get(registeredId(index));
            verifySaved(recipe, index);
            if (type(index).recipes.get(recipe.id) != recipe) {
                throw new IllegalStateException("GTO lost ECO machine recipe " + recipe.id);
            }
        }
        LoggerFactory.getLogger("neoecoae").info("Verified {} GTO ECO machine recipes in finalized recipe maps", count());
    }
}
