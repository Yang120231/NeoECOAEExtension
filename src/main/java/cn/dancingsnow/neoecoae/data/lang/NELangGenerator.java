package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class NELangGenerator {
    public static void accept(RegistrateLangProvider provider) {
        provider.add("gui.neoecoae.big_order.reason.output_storage", "Waiting for output storage");
        provider.add("gui.neoecoae.big_order.reason.child_incomplete", "Incomplete segment; further planning stopped");
        provider.add("gui.neoecoae.big_order.planning", "Planning");
        provider.add("gui.neoecoae.big_order.running_child", "Running segment");
        provider.add("gui.neoecoae.big_order.waiting_materials", "Waiting for materials");
        provider.add("gui.neoecoae.big_order.waiting_capacity", "Waiting for capacity");
        provider.add("gui.neoecoae.big_order.completed", "Completed");
        provider.add("gui.neoecoae.big_order.cancelled", "Cancelled");
        provider.add("gui.neoecoae.big_order.failed", "Failed");
        provider.add("gui.neoecoae.big_order.reason.capacity", "CPU capacity unavailable");
        provider.add("gui.neoecoae.big_order.reason.materials", "Missing materials");
        provider.add("gui.neoecoae.big_order.reason.source_unavailable", "Waiting for network or requesting player");
        provider.add("gui.neoecoae.big_order.reason.planner_busy", "Planner busy");
        provider.add("gui.neoecoae.big_order.reason.planning_failed", "Planning failed");
        provider.add("gui.neoecoae.big_order.total", "Total %s");
        provider.add("gui.neoecoae.big_order.requested", "Requested: %s");
        provider.add("gui.neoecoae.big_order.done", "Completed %s");
        provider.add("gui.neoecoae.big_order.remaining", "Remaining %s");
        provider.add("gui.neoecoae.big_order.child", "Segment %s · remaining %s");
        provider.add("gui.neoecoae.big_order.requires_cpu", "BigInt orders require an ECO CPU");
        provider.add("gui.neoecoae.big_order.single_task", "ECO CPU: execute the complete order as one task");
        provider.add("gui.neoecoae.craft_error.shortfall", "Not extracted this attempt: %s");
        provider.add("gui.neoecoae.craft_error.meaning", "The actual extraction fell short. This is the unextracted remainder, not total network stock.");
        provider.add("gui.neoecoae.craft_error.unknown_cause", "AE2 did not report the underlying cause. Possible causes to check:");
        provider.add("gui.neoecoae.craft_error.stock_changed", "- Stock changed: another task, machine, or player took the material after planning.");
        provider.add("gui.neoecoae.craft_error.storage_offline", "- Storage became unavailable: disconnected device, missing channel, power loss, or removed cell.");
        provider.add("gui.neoecoae.craft_error.extraction_blocked", "- Extraction denied: permissions, output filters, locked storage, or a third-party storage rule.");
        provider.add("gui.neoecoae.craft_error.transfer_limit", "- Storage returned only part of the request: per-call transfer limits or large-amount compatibility issues.");
        provider.add("gui.neoecoae.craft_error.retry", "Retry: submit the same plan again after restoring material access.");
        provider.add("gui.neoecoae.craft_error.replan", "Back: recalculate using current stock. This page reports only the first failed material.");
        provider.add("gui.neoecoae.crafting_report.exact_incomplete", "Execution plan incomplete; see CPU details");
        provider.add("gui.neoecoae.crafting_report.supply_insufficient", "Available supply cannot cover this order");
        provider.add("gui.neoecoae.crafting_report.optimizing", "Background optimization");
        provider.add("gui.neoecoae.crafting_report.optimization_paused", "Current plan retained; further optimization is incomplete");
        provider.add("gui.neoecoae.crafting_report.optimization_finished", "Route optimization finished");
        provider.add("gui.neoecoae.storage_interface.ignore_creative.on", "Ignore creative cell input: On");
        provider.add("gui.neoecoae.storage_interface.ignore_creative.off", "Ignore creative cell input: Off");
        provider.add("gui.neoecoae.storage_interface.ignore_creative.description", "Input mode only: skip resources whose stored amount does not change after extraction");
        provider.add("tooltip.neoecoae.big_order.requires_cpu", "This order uses an extended quantity; an idle, online ECO crafting CPU is required");
        provider.add("gui.neoecoae.storage_interface.exclude_storage_bus.on", "Exclude storage bus containers: On");
        provider.add("gui.neoecoae.storage_interface.exclude_storage_bus.off", "Exclude storage bus containers: Off");
        provider.add("gui.neoecoae.storage_interface.exclude_storage_bus.description", "Input and output modes: skip storage exposed through ME storage buses");
        GuiLangs.accept(provider);
        ConfigLangs.accept(provider);
        provider.add("gui.neoecoae.crafting.ui.batch_parallel", "Batch");
        provider.add("gui.neoecoae.crafting.smart_doubling", "ECO Smart Doubling");
        provider.add("gui.neoecoae.processing.scope", "Associated machine cycles; not whole-order progress. Machines may be shared.");
        provider.add("gui.neoecoae.processing.aggregate", "Bar: time-weighted progress of all associated machine cycles.");
        provider.add("gui.neoecoae.processing.progress_unavailable", "Machine cycle progress is not available yet; the progress track remains unfilled.");
        provider.add("gui.neoecoae.processing.awaiting_dispatch", "Waiting for this step to be dispatched; the progress track fills when its machine starts.");
        provider.add("gui.neoecoae.processing.page", "Details %s / %s · Shift + wheel to change page");
        provider.add("gui.neoecoae.processing.recipe", "Recipe: %s");
        provider.add("gui.neoecoae.processing.reason", "Reason: %s");
        provider.add("gui.neoecoae.processing.unloaded", "Chunk or dimension unloaded");
        provider.add("gui.neoecoae.processing.unformed", "Multiblock structure is not formed");
        provider.add("gui.neoecoae.processing.machine_open_unavailable", "Cannot open the machine UI. Check that the machine is loaded and in your dimension.");
        provider.add("gui.neoecoae.processing.action.stop", "Stop this processing step");
        provider.add("gui.neoecoae.processing.action.resume", "Resume this processing step");
        provider.add("gui.neoecoae.processing.stop_hint", "Stops future dispatch; accepted batches continue and dependent steps wait. Co-products of the same pattern stop together.");
        provider.add("gui.neoecoae.processing.step_stopped", "Further dispatch for this step is stopped (right-click to resume)");
        provider.add("gui.neoecoae.processing.no_permission", "You do not have permission to modify this pattern or crafting task.");
        provider.add("gui.neoecoae.processing.already_dispatched", "This step is fully dispatched; accepted machine batches continue.");
        provider.add("gui.neoecoae.crafting.mode_switch_blocked", "Cannot switch while a crafting task is running");
        provider.add("gui.neoecoae.crafting.ui.recipe_slots", "Slots");
        provider.add("tooltip.neoecoae.infinite_component.unlock", "Insert 64 components and install 12 L9 storage matrices to enable infinite storage");
        provider.add("gui.ae2.ConfirmCraftCpuStatus", "Storage: %s; Co-Processing Units: %s");
        provider.add("gui.ae2.ConfirmCraftNoCpu", "Storage: N/A; Co-Processing Units: N/A");
        provider.add("gui.neoecoae.crafting.cycle_planning.off", "Cycle Planning: Disabled");
        provider.add("gui.neoecoae.crafting.cycle_planning.on", "Cycle Planning: Enabled");
        provider.add("gui.neoecoae.force_start", "Force Start");
        provider.add("tooltip.neoecoae.storage.bulk_unresolved", "%s bulk entries are preserved but unavailable. Restore missing items or the original recipes before recovery.");
        provider.add("tooltip.neoecoae.force_start", "Ignore missing materials and submit the crafting job");



        provider.add("neoecoae.tooltip.upload_pattern", "Upload Pattern into available ECO Crafting System");

        provider.add("category.neoecoae.cooling", "Cooling");
        provider.add("category.neoecoae.cooling.coolant", "Coolant: %d");
        provider.add("category.neoecoae.cooling.max_overclock", "Max Overclock: %d");
        provider.add("category.neoecoae.multiblock", "ECO Multiblock Info");
        provider.add("category.neoecoae.integrated_working_station", "Integrated Working Station");

        provider.add("block.neoecoae.large_integrated_working_station", "Large Integrated Working Station");

        provider.add("tag.item.ae2.inscriber_presses", "Inscriber Presses");
        provider.add("tag.item.ae2.metal_ingots", "Metal Ingots");
        provider.add("tag.item.c.budding_blocks", "Budding Blocks");
        provider.add("tag.item.c.clusters", "Crystal Clusters");
        provider.add("tag.item.c.dusts.aluminum", "Aluminum Dusts");
        provider.add("tag.item.c.dusts.aluminum_alloy", "Aluminum Alloy Dusts");
        provider.add("tag.item.c.dusts.black_tungsten_alloy", "Black Tungsten Alloy Dusts");
        provider.add("tag.item.c.dusts.energized_crystal", "Energized Crystal Dusts");
        provider.add("tag.item.c.dusts.energized_fluix_crystal", "Energized Fluix Crystal Dusts");
        provider.add("tag.item.c.dusts.tungsten", "Tungsten Dusts");
        provider.add("tag.item.c.gems.energized_crystal", "Energized Crystals");
        provider.add("tag.item.c.gems.energized_fluix_crystal", "Energized Fluix Crystals");
        provider.add("tag.item.c.ingots.aluminum", "Aluminum Ingots");
        provider.add("tag.item.c.ingots.aluminum_alloy", "Aluminum Alloy Ingots");
        provider.add("tag.item.c.ingots.black_tungsten_alloy", "Black Tungsten Alloy Ingots");
        provider.add("tag.item.c.ingots.tungsten", "Tungsten Ingots");
        provider.add("tag.item.c.ores.aluminum", "Aluminum Ores");
        provider.add("tag.item.c.ores.tungsten", "Tungsten Ores");
        provider.add("tag.item.c.raw_materials.aluminum", "Raw Aluminum");
        provider.add("tag.item.c.raw_materials.tungsten", "Raw Tungsten");
        provider.add("tag.item.c.storage_blocks.aluminum", "Aluminum Storage Blocks");
        provider.add("tag.item.c.storage_blocks.aluminum_alloy", "Aluminum Alloy Storage Blocks");
        provider.add("tag.item.c.storage_blocks.black_tungsten_alloy", "Black Tungsten Alloy Storage Blocks");
        provider.add("tag.item.c.storage_blocks.energized_crystal", "Energized Crystal Storage Blocks");
        provider.add("tag.item.c.storage_blocks.energized_fluix_crystal", "Energized Fluix Crystal Storage Blocks");
        provider.add("tag.item.c.storage_blocks.raw_aluminum", "Raw Aluminum Storage Blocks");
        provider.add("tag.item.c.storage_blocks.raw_tungsten", "Raw Tungsten Storage Blocks");
        provider.add("tag.item.c.storage_blocks.tungsten", "Tungsten Storage Blocks");
        provider.add("tag.item.c.tools.mining_tool", "Mining Tools");
        provider.add("tag.item.neoecoae.crystal_ingot_base", "Crystal Ingot Base");
        provider.add("tag.item.neoecoae.superconductive_ingot_base", "Superconductive Ingot Base");

        provider.add("tooltip.neoecoae.holdshift", "Hold [Shift] to show more info");
        provider.add("tooltip.neoecoae.max_lenth", "Maximum length of structure: %d");
        provider.add("tooltip.neoecoae.pattern.verified_normal", "Verified: normal pattern");
        provider.add("tooltip.neoecoae.pattern.verified_smithing_stonecutting", "Verified: smithing/stonecutting recipe");
        provider.add("tooltip.neoecoae.pattern.verified_durability", "Verified: durability pattern");
        provider.add("tooltip.neoecoae.pattern.verified_self_growing", "Verified: self-growing pattern");
        provider.add("tooltip.neoecoae.pattern.verified_special_nbt", "Verified: special NBT pattern");

        provider.add("tooltip.neoecoae.storage_system", "The core of the storage subsystem");
        addLangs(provider, "tooltip.neoecoae.storage_dirve",
            "Can drive storage matrix",
            "The drivable storage matrix tier depends on the storage subsystem host controller"
        );
        provider.add("tooltip.neoecoae.storage.infinite_component_locked", "Cannot remove infinite components: stored contents cannot safely fit back into normal matrices");
        provider.add("tooltip.neoecoae.storage.infinite_member_locked", "Infinite storage matrices cannot be removed while the storage host is in infinite mode");
        provider.add("gui.neoecoae.storage.members_missing", "Missing %s member matrices");
        provider.add("gui.neoecoae.storage.members_return", "Please insert them again");
        provider.add("tooltip.neoecoae.storage.persistence_failed", "Storage data could not be saved; cell extraction is temporarily locked.");
        provider.add("tooltip.neoecoae.storage.infinite_migration_locked", "Cannot remove this matrix while infinite storage migration is in progress");
        provider.add("tooltip.neoecoae.infinite_component.header", "Infinite Storage: Activation Requirements");
        provider.add("tooltip.neoecoae.infinite_component.components",
            "Component slots: 64 infinite storage components in the storage host");
        provider.add("tooltip.neoecoae.infinite_component.matrices",
            "Drives: any 12 L9 storage matrices in the same storage host");
        provider.add("tooltip.neoecoae.storage.infinite_member", "Managed by the storage host");
        provider.add("tooltip.neoecoae.infinite_resource.contents", "Infinite supply: Water / Cobblestone / Lava");
        provider.add("tooltip.neoecoae.infinite_resource.unbounded", "Can be extracted directly by the ME network and never runs out");
        provider.add("tooltip.neoecoae.infinite_resource.sink", "Accepts only the three listed resources; inserted resources are destroyed");
        provider.add("gui.neoecoae.storage.status.degraded", "Infinite storage data needs repair");
        provider.add("gui.neoecoae.storage.status.recovery", "Infinite storage is temporarily read-only");
        provider.add("gui.neoecoae.storage.status.unavailable", "Infinite storage is unavailable");

        provider.add("tooltip.neoecoae.crafting_system", "The core of the crafting subsystem");
        provider.add("tooltip.neoecoae.large_integrated_working_station",
            "256 base parallel, x1 recipe energy. Temperature mode can be disabled in server config");
        provider.add("tooltip.neoecoae.large_integrated_working_station.water", "Water: 16,384 parallel in coolant mode; temperature mode requires 20%");
        provider.add("tooltip.neoecoae.large_integrated_working_station.sodium", "Sodium: 65,536 parallel in coolant mode; temperature mode requires 50%");
        provider.add("tooltip.neoecoae.large_integrated_working_station.cryotheum", "Cryotheum: 262,144 parallel in coolant mode; temperature mode requires 80%");
        provider.add("tooltip.neoecoae.large_integrated_working_station.coolant_tick", "100 mB/t: temperature mode cools while paused; coolant mode consumes only for advancing overclocked work");
        provider.add("tooltip.neoecoae.crafting_parallels", "Parallel core provides parallel count to the crafting subsystem");
        provider.add("tooltip.neoecoae.max_parallel_count", "Max parallel count +%d");
        provider.add("tooltip.neoecoae.overclocked", "When enabling overclocking:");
        provider.add("tooltip.neoecoae.active_cooling", "When enabling active cooling:");
        provider.add("tooltip.neoecoae.clear_negative_effect", "Clear the negative effects of overclocking");

        addLangs(provider, "tooltip.neoecoae.crafting_worker",
            "ECO - FX Worker is the main part of the crafting subsystem",
            "ECO - FX Worker can store 32 crafting jobs, processing 1 crafting job per crafting"
        );
        addLangs(provider, "tooltip.neoecoae.fx_monitor_core",
            "ECO - FX Monitor Core includes a ME-style crafting monitor",
            "Displays the crafting jobs handled by the FX crafting subsystem"
        );
        provider.add("tooltip.neoecoae.crafting_jobs_l4", "Store Crafting Jobs: x%d [L4]");
        provider.add("tooltip.neoecoae.crafting_jobs_l6", "Store Crafting Jobs: x%d [L6]");
        provider.add("tooltip.neoecoae.crafting_jobs_l9", "Store Crafting Jobs: x%d [L9]");
        provider.add("tooltip.neoecoae.power_multiply_l4", "Power Multiply: x%d [L4]");
        provider.add("tooltip.neoecoae.power_multiply_l6", "Power Multiply: x%d [L6]");
        provider.add("tooltip.neoecoae.power_multiply_l9", "Power Multiply: x%d [L9]");

        addLangs(provider, "tooltip.neoecoae.crafting_pattern_bus",
            "ECO - FD Smart Pattern Bus is the main part of the crafting subsystem",
            "Each bus can store 63 patten",
            "When encoding patten on the ME Encoding Terminal, you can use the adjacent button to quickly upload them"
        );

        provider.add("tooltip.neoecoae.computation_system", "The core of the computation subsystem");
        addLangs(provider, "tooltip.neoecoae.computation_system_desc",
            "The computation subsystem introduces virtual Crafting Processors (vCPUs):",
            "The host provides only one vCPU to the ME network at a time, with capacity equal to all currently available bytes in the subsystem",
            "When a user assigns a crafting task to a vCPU, the host automatically adjusts the vCPU's byte allocation to the task's requirements before assigning it to a Threading Core",
            "New vCPUs can be allocated continuously until the total allocated vCPUs reach the max thread count",
            "vCPUs are immediately destroyed when the crafting task completes and all items are returned"
        );

        addLangs(provider, "tooltip.neoecoae.computation_drive",
            "Can drive flash crystal matrix",
                "The drivable flash crystal matrix tier depends on the computation subsystem host controller"
        );
        addLangs(provider, "tooltip.neoecoae.computation_threading_core",
            "Threading Core is the main part of the computation subsystem, providing thread count to the host controller",
            "Threads determine the maximum virtual CPUs for the computation subsystem",
            "When destroyed, compressed CPU data will be directly saved to the dropped item"
        );
        provider.add("tooltip.neoecoae.max_thread_count", "Max thread count +%d");
        addLangs(provider, "tooltip.neoecoae.computation_parallel_core",
            "Parallel Core provides parallel count to the computation subsystem",
            "Parallel count increases the processing numbers per crafting task for all threading cores"
        );
        provider.add("tooltip.neoecoae.computation_cell", "Provides %s bytes to the computation subsystem");
        provider.add("tooltip.neoecoae.computation_cell.install",
            "Right-click a computation subsystem host to install directly");

        provider.add("neoecoae.classic_pack", "Neo ECO AE Extension Classic Textures");
        provider.add("neoecoae.unknow_cell_type", "Unknown cell type");

        provider.add("tooltip.neoecoae.budding_energized_crystal_block", "Obtained by striking Budding Certus Quartz with lightning");
    }

    private static void addLangs(RegistrateLangProvider provider, String key, String... langs) {
        for (int i = 0; i < langs.length; i++) {
            provider.add(key + "." + i, langs[i]);
        }
    }
}
