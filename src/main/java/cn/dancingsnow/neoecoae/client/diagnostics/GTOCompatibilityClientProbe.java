package cn.dancingsnow.neoecoae.client.diagnostics;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageProvider;
import appeng.core.definitions.AEBlocks;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.compat.gto.ECOGtoPatternPush;
import cn.dancingsnow.neoecoae.integration.emi.NeoECOAEEmiPlugin;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import org.slf4j.LoggerFactory;

/** Explicit GTO acceptance only: real recipe lookup, provider dispatch, server ticks and EMI screens. */
public final class GTOCompatibilityClientProbe {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("neoecoae.gtoAcceptance");
    private static volatile int stage, ticks;
    private static volatile boolean done;
    private static volatile Throwable failure;
    private static BlockPos smallPos, largePos;
    private static IntegratedWorkingStationRecipe recipe;
    private static KeyCounter received = new KeyCounter();
    private static IStorageProvider sinkProvider;
    private static java.util.List<String> originalPacks;
    private static final net.minecraft.resources.ResourceLocation RELOAD_RECIPE = NeoECOAE.id("gto_acceptance/reload");
    private static int originalSmallCount, originalLargeCount;
    private static java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> nativePlan, ecoPlan;
    private static appeng.api.networking.crafting.ICraftingLink nativeLink;
    private static final int NATIVE_CRAFTS = 64;
    private static long largeManualOutput;

    private GTOCompatibilityClientProbe() {}

    public static void tick(Minecraft client) {
        if (stage == -1 || client.level == null || client.player == null) return;
        if (failure != null) {
            LOG.error("GTO_ACCEPTANCE FAILED at stage {}", stage, failure);
            stage = -1;
            return;
        }
        ticks++;
        try {
            switch (stage) {
                case 0 -> {
                    client.options.pauseOnLostFocus = false;
                    client.setScreen(null);
                    var manager = EmiApi.getRecipeManager();
                    int small = manager.getRecipes(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION).size();
                    int large = manager.getRecipes(NeoECOAEEmiPlugin.LARGE_WORKING_STATION).size();
                    if (small == 0 || large == 0) {
                        if (ticks < 1200) return;
                        throw new IllegalStateException("EMI workstation categories empty: " + small + "/" + large);
                    }
                    LOG.info("GTO_ACCEPTANCE EMI catalogue: integrated={}, large={}", small, large);
                    originalSmallCount = small;
                    originalLargeCount = large;
                    EmiApi.displayRecipeCategory(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION);
                    advance();
                }
                case 1 -> {
                    if (ticks < 30) return;
                    require(client.screen != null && client.screen.getClass().getName().contains("RecipeScreen"), "EMI recipe screen missing");
                    screenshot(client, "neoecoae-gto-emi-integrated.png");
                    EmiApi.displayRecipeCategory(NeoECOAEEmiPlugin.LARGE_WORKING_STATION);
                    advance();
                }
                case 2 -> {
                    if (ticks < 30) return;
                    screenshot(client, "neoecoae-gto-emi-large.png");
                    client.setScreen(null);
                    done = false;
                    var origin = client.player.blockPosition();
                    server(client, () -> prepare(client.getSingleplayerServer().overworld(), origin));
                    advance();
                }
                case 3 -> {
                    if (!done || ticks < 100) return;
                    done = false;
                    server(client, () -> dispatch(client.getSingleplayerServer().overworld()));
                    advance();
                }
                case 4 -> {
                    if (!done || ticks < 400 || ticks % 20 != 0) return;
                    done = false;
                    client.getSingleplayerServer().execute(() -> {
                        try {
                            if (received.get(AEItemKey.of(recipe.itemOutput())) < largeManualOutput && ticks < 4000) {
                                done = true;
                                return;
                            }
                            verify(client.getSingleplayerServer().overworld());
                            advance();
                            done = true;
                        } catch (Throwable error) { failure = error; }
                    });
                }
                case 5 -> {
                    if (!done || ticks < 500 || ticks % 20 != 0) return;
                    done = false;
                    client.getSingleplayerServer().execute(() -> {
                        try {
                            if (nativeLink != null && !nativeLink.isDone() && !nativeLink.isCanceled() && ticks < 4000) {
                                done = true;
                                return;
                            }
                            require(nativeLink != null && nativeLink.isDone() && !nativeLink.isCanceled(), "GTO native CPU job did not complete");
                            require(received.get(AEItemKey.of(recipe.itemOutput())) == recipe.itemOutput().getCount() * NATIVE_CRAFTS + largeManualOutput,
                                "GTO native CPU output ownership/count mismatch: " + received);
                            LOG.info("GTO_ACCEPTANCE native CPU job PASS: crafts={}, delivered={}, link={}", NATIVE_CRAFTS,
                                received.get(AEItemKey.of(recipe.itemOutput())), nativeLink.getCraftingID());
                            advance();
                            reload(client, true);
                        } catch (Throwable error) { failure = error; }
                    });
                }
                case 6 -> {
                    if (!done || ticks < 100) return;
                    if (client.level.getRecipeManager().byKey(RELOAD_RECIPE).isEmpty()
                            || EmiApi.getRecipeManager().getRecipes(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION).size() != originalSmallCount + 1
                            || EmiApi.getRecipeManager().getRecipes(NeoECOAEEmiPlugin.LARGE_WORKING_STATION).size() != originalLargeCount + 1) {
                        if (ticks < 1600) return;
                        throw new IllegalStateException("Warm reload did not update client and EMI ECO catalogues");
                    }
                    LOG.info("GTO_ACCEPTANCE warm reload added recipe to server, client and both EMI categories");
                    done = false;
                    beginReload(client, false);
                    advance();
                }
                case 7 -> {
                    if (!done || ticks < 100) return;
                    if (client.level.getRecipeManager().byKey(RELOAD_RECIPE).isPresent()
                            || EmiApi.getRecipeManager().getRecipes(NeoECOAEEmiPlugin.INTEGRATED_WORKING_STATION).size() != originalSmallCount
                            || EmiApi.getRecipeManager().getRecipes(NeoECOAEEmiPlugin.LARGE_WORKING_STATION).size() != originalLargeCount) {
                        if (ticks < 1600) return;
                        throw new IllegalStateException("Disabled datapack left stale ECO client/EMI recipes");
                    }
                    LOG.info("GTO_ACCEPTANCE PASS: EMI screens, real processing, warm reload and stale recipe removal");
                    screenshot(client, "neoecoae-gto-processing.png");
                    stage = -1;
                }
                default -> throw new IllegalStateException("Unknown GTO acceptance stage " + stage);
            }
        } catch (Throwable error) { failure = error; }
    }

    private static void prepare(ServerLevel level, BlockPos origin) {
        recipe = level.getRecipeManager().getRecipes().stream()
            .filter(IntegratedWorkingStationRecipe.class::isInstance).map(IntegratedWorkingStationRecipe.class::cast)
            .filter(r -> r.inputFluid().ingredient().isEmpty() && r.fluidOutput().isEmpty() && !r.itemOutput().isEmpty())
            .filter(r -> r.inputItems().stream().allMatch(i -> i.ingredient().getItems().length > 0 && i.count() <= 64))
            .min(Comparator.comparingInt(IntegratedWorkingStationRecipe::energy)).orElseThrow();
        smallPos = origin.offset(3, 1, 0);
        largePos = origin.offset(8, 1, 0);
        level.setBlockAndUpdate(smallPos, NEBlocks.INTEGRATED_WORKING_STATION.getDefaultState());
        level.setBlockAndUpdate(smallPos.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        WorkstationModelClientProbe.build(level, largePos, Direction.NORTH);
        level.setBlockAndUpdate(largePos.relative(Direction.SOUTH, 2).below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(largePos.relative(Direction.SOUTH, 3).below(), AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        level.setBlockAndUpdate(largePos.relative(Direction.SOUTH, 4).below(), AEBlocks.CRAFTING_ACCELERATOR.block().defaultBlockState());
        var large = (ECOLargeIntegratedWorkingStationBlockEntity) level.getBlockEntity(largePos);
        large.rebuildMultiblock();
        require(large.isFormed(), "Large workstation did not form");
        LOG.info("GTO_ACCEPTANCE processing fixture: recipe={}, energy={}, duration={}", recipe.getId(), recipe.energy(), recipe.processingTicks());
    }

    private static void dispatch(ServerLevel level) {
        var small = (ECOIntegratedWorkingStationBlockEntity) level.getBlockEntity(smallPos);
        var large = (ECOLargeIntegratedWorkingStationBlockEntity) level.getBlockEntity(largePos);
        require(small.getMainNode().isActive(), "Standalone workstation grid inactive");
        require(large.getMainNode().isActive(), "Large workstation grid inactive");
        var inputs = new ArrayList<GenericStack>();
        int slot = 0;
        for (var ingredient : recipe.inputItems()) {
            ItemStack stack = ingredient.ingredient().getItems()[0].copy();
            stack.setCount(ingredient.count());
            small.getInternalInventory().setItemDirect(slot++, stack.copy());
            inputs.add(GenericStack.fromItemStack(stack));
        }
        require(small.getTask() != null, "Standalone workstation recipe lookup failed");
        var output = GenericStack.fromItemStack(recipe.itemOutput());
        var encoded = PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new), new GenericStack[]{output});
        var communication = (ECOLargeIntegratedWorkingStationInterfaceBlockEntity) level.getBlockEntity(largePos.relative(Direction.SOUTH).below());
        var provider = communication.getWorkstationProvider();
        provider.getPatternInv().setItemDirect(0, encoded);
        provider.updatePatterns();
        var player = level.getServer().getPlayerList().getPlayers().get(0);
        var originalHand = player.getMainHandItem();
        boolean originalShift = player.isShiftKeyDown();
        try {
            var tool = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("gtocore:pattern_modifier_pro")));
            tool.getOrCreateTag().putLong("Scale", 4);
            tool.getOrCreateTag().putLong("DivScale", 1);
            tool.getOrCreateTag().putLong("Cycles", 1);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
            player.setShiftKeyDown(true);
            var pos = communication.getBlockPos();
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false);
            var event = new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,
                net.minecraft.world.InteractionHand.MAIN_HAND, pos, hit);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
            require(event.isCanceled() && event.getCancellationResult() == net.minecraft.world.InteractionResult.SUCCESS,
                "GTO modifier interaction on ECO interface failed");
        } finally {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, originalHand);
            player.setShiftKeyDown(originalShift);
        }
        var pattern = provider.getAvailablePatterns().iterator().next();
        largeManualOutput = pattern.getPrimaryOutput().amount();
        require(largeManualOutput == recipe.itemOutput().getCount() * 4L, "GTO native modifier did not quadruple the pattern");
        LOG.info("GTO_ACCEPTANCE native modifier PASS: baseOutput={}, scaledOutput={}", recipe.itemOutput().getCount(), largeManualOutput);
        var holders = new KeyCounter[pattern.getInputs().length];
        for (int i = 0; i < holders.length; i++) {
            var input = pattern.getInputs()[i];
            var stack = input.getPossibleInputs()[0];
            holders[i] = new KeyCounter();
            holders[i].add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()));
        }
        var stocked = new KeyCounter();
        for (var holder : holders) for (var entry : holder) stocked.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), NATIVE_CRAFTS * 2L));
        MEStorage sink = new MEStorage() {
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                if (mode == Actionable.MODULATE) received.add(key, amount);
                return amount;
            }
            @Override public void getAvailableStacks(KeyCounter out) { out.addAll(stocked); }
            @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                long extracted = Math.min(amount, stocked.get(key));
                if (mode == Actionable.MODULATE) stocked.remove(key, extracted);
                return extracted;
            }
            @Override public Component getDescription() { return Component.literal("GTO acceptance output counter"); }
        };
        sinkProvider = mounts -> mounts.mount(sink);
        large.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(sinkProvider);
        var grid = large.getMainNode().getGrid();
        grid.getStorageService().invalidateCache();
        var source = IActionSource.ofMachine(large);
        var requester = new appeng.api.networking.crafting.ICraftingSimulationRequester() {
            @Override public IActionSource getActionSource() { return source; }
            @Override public appeng.api.networking.IGridNode getGridNode() { return large.getMainNode().getNode(); }
        };
        var ecoRequester = new cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerRequest() {
            @Override public IActionSource getActionSource() { return source; }
            @Override public appeng.api.networking.IGridNode getGridNode() { return large.getMainNode().getNode(); }
            @Override public cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions neoecoae$getPlannerOptions() {
                return new cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions(false, false, java.util.Set.of());
            }
        };
        nativePlan = grid.getCraftingService().beginCraftingCalculation(level, requester, output.what(), output.amount() * NATIVE_CRAFTS,
            appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
        ecoPlan = grid.getCraftingService().beginCraftingCalculation(level, ecoRequester, output.what(), output.amount() * NATIVE_CRAFTS,
            appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
        require(ECOGtoPatternPush.push(provider, pattern, holders), "GTO native callback rejected the large workstation pattern");
        for (var holder : holders) require(holder.isEmpty(), "Large workstation did not take input ownership");
        LOG.info("GTO_ACCEPTANCE actual native provider callback accepted recipe {}", recipe.getId());
    }

    private static void verify(ServerLevel level) {
        try {
            require(nativePlan.isDone() && ecoPlan.isDone(), "Native/ECO planners did not finish");
            var nativeResult = nativePlan.get();
            var ecoResult = ecoPlan.get();
            require(!cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry.isECOOwnedPlan(nativeResult),
                "Ordinary request was intercepted by ECO");
            require(cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry.isECOOwnedPlan(ecoResult),
                "Explicit ECO request did not reach the ECO planner");
            require(!nativeResult.simulation() && !ecoResult.simulation(), "Native/ECO planning unexpectedly reported missing inputs");
            require(nativeResult.finalOutput().amount() == recipe.itemOutput().getCount() * (long) NATIVE_CRAFTS
                    && ecoResult.finalOutput().amount() == recipe.itemOutput().getCount() * (long) NATIVE_CRAFTS,
                "Native/ECO planning changed requested output");
            LOG.info("GTO_ACCEPTANCE planner coexistence PASS: nativeTasks={}, ecoTasks={}, nativeOutput={}, ecoOutput={}",
                nativeResult.patternTimes().size(), ecoResult.patternTimes().size(), nativeResult.finalOutput(), ecoResult.finalOutput());
        } catch (java.util.concurrent.ExecutionException | InterruptedException error) { throw new IllegalStateException(error); }
        var small = (ECOIntegratedWorkingStationBlockEntity) level.getBlockEntity(smallPos);
        var actual = small.getInternalInventory().getStackInSlot(9);
        require(ItemStack.isSameItemSameTags(actual, recipe.itemOutput()) && actual.getCount() == recipe.itemOutput().getCount(),
            "Standalone processing output mismatch: " + actual);
        for (int i = 0; i < 9; i++) require(small.getInternalInventory().getStackInSlot(i).isEmpty(), "Standalone input not consumed");
        require(received.get(AEItemKey.of(recipe.itemOutput())) == largeManualOutput,
            "Large processing output mismatch: actual=" + received.get(AEItemKey.of(recipe.itemOutput())) + ", expected=" + largeManualOutput
                + ", pending=" + level.getBlockEntity(largePos).saveWithFullMetadata().getList("pendingBatches", 10));
        LOG.info("GTO_ACCEPTANCE processing PASS: integrated={}, large={}, output={}",
            actual.getCount(), received.get(AEItemKey.of(recipe.itemOutput())), recipe.itemOutput());
        try {
            var large = (ECOLargeIntegratedWorkingStationBlockEntity) level.getBlockEntity(largePos);
            var crafting = large.getMainNode().getGrid().getCraftingService();
            var cpu = crafting.getCpus().stream().filter(appeng.me.cluster.implementations.CraftingCPUCluster.class::isInstance).findFirst().orElseThrow();
            var submitted = crafting.submitJob(nativePlan.get(), null, cpu, false, IActionSource.ofMachine(large));
            require(submitted.successful(), "Native GTO plan could not be submitted: " + submitted.errorCode() + "/" + submitted.errorDetail());
            nativeLink = submitted.link();
            if (nativeLink == null) nativeLink = ((appeng.me.cluster.implementations.CraftingCPUCluster) cpu).craftingLogic.getLastLink();
            require(nativeLink != null, "Submitted GTO native CPU did not retain a standalone job link");
            LOG.info("GTO_ACCEPTANCE submitted native CPU job {}", nativeLink.getCraftingID());
        } catch (java.util.concurrent.ExecutionException | InterruptedException error) { throw new IllegalStateException(error); }
    }

    private static void reload(Minecraft client, boolean enable) {
        var server = client.getSingleplayerServer();
        var repository = server.getPackRepository();
        if (originalPacks == null) originalPacks = java.util.List.copyOf(repository.getSelectedIds());
        if (enable) {
            var root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
                .resolve("neoecoae-gto-acceptance");
            try {
                java.nio.file.Files.createDirectories(root.resolve("data/neoecoae/recipes/gto_acceptance"));
                java.nio.file.Files.writeString(root.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":15,\"description\":\"Isolated ECO GTO acceptance fixture\"}}");
                java.nio.file.Files.writeString(root.resolve("data/neoecoae/recipes/gto_acceptance/reload.json"), """
                    {"type":"neoecoae:integrated_working_station","inputItems":[{"item":"minecraft:iron_ingot","count":1}],
                     "itemOutput":{"item":"minecraft:diamond","count":1},"energy":17,"processingTicks":13}
                    """);
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        }
        repository.reload();
        var selected = new ArrayList<>(originalPacks);
        if (enable) selected.add("file/neoecoae-gto-acceptance");
        // Completion follows the asynchronous resource loader; client recipe and EMI reloads finish later.
        done = false;
        server.reloadResources(selected).whenComplete((ignored, error) -> server.execute(() -> {
            if (error != null) { failure = error; return; }
            try {
                require(server.getRecipeManager().byKey(RELOAD_RECIPE).isPresent() == enable, "Server warm recipe reload mismatch");
                done = true;
            } catch (Throwable check) { failure = check; }
        }));
    }

    private static void beginReload(Minecraft client, boolean enable) {
        client.getSingleplayerServer().execute(() -> {
            try { reload(client, enable); } catch (Throwable error) { failure = error; }
        });
    }

    private static void server(Minecraft client, Runnable action) {
        client.getSingleplayerServer().execute(() -> {
            try { action.run(); done = true; } catch (Throwable error) { failure = error; }
        });
    }
    private static void advance() { stage++; ticks = 0; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void screenshot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> LOG.info("GTO_ACCEPTANCE screenshot: {}", message.getString()));
    }
}
