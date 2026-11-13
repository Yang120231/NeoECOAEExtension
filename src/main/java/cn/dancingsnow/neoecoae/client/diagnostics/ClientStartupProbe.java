package cn.dancingsnow.neoecoae.client.diagnostics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in development probe; uses the real client, world loader and framebuffer. */
public final class ClientStartupProbe {
    private static final Logger LOG = LoggerFactory.getLogger("neoecoae.clientSmoke");
    private static boolean started;
    private static int worldTicks;
    private static boolean captured;
    private static String lastScreen;
    private static net.minecraft.core.BlockPos workstationPos;
    private static int gtoTicks;
    private static volatile int gtoPhase;
    private static net.minecraft.core.BlockPos gtoProviderPos;
    private static appeng.api.parts.IPart gtoTerminal;
    private static cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity gtoPatternBus;
    private static net.minecraft.world.inventory.Slot gtoEncodedSlot;
    private static net.minecraft.world.item.ItemStack gtoPendingPattern;
    private static boolean gtoScreenCaptured;

    private ClientStartupProbe() {}

    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        String currentScreen = client.screen == null ? "in_game" : client.screen.getClass().getName();
        if (!currentScreen.equals(lastScreen)) {
            LOG.info("CLIENT_SMOKE screen: {}", currentScreen);
            lastScreen = currentScreen;
        }
        if (java.nio.file.Files.exists(client.gameDirectory.toPath().resolve("client-smoke-stop"))) {
            LOG.info("CLIENT_SMOKE stopping client on local request");
            client.stop();
            return;
        }
        if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            client.options.onboardAccessibility = false;
            client.options.save();
            client.setScreen(new TitleScreen());
            return;
        }
        if (!started && client.screen instanceof TitleScreen && client.getOverlay() == null) {
            started = true;
            if (!System.getProperty("neoecoae.multiblockAlignmentSmoke", "").isBlank()) {
                MultiblockAlignmentClientProbe.verifyModels(client);
            }
            LOG.info("CLIENT_SMOKE title screen reached; creating isolated creative world");
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            var settings = new LevelSettings("NeoECOAE startup validation", GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("neoecoae-startup-" + System.currentTimeMillis(),
                settings, new WorldOptions(20261021L, false, false), WorldPresets::createNormalWorldDimensions);
        }
        if (!captured && client.level != null && client.player != null && ++worldTicks >= 200) {
            captured = true;
            var server = client.getSingleplayerServer();
            LOG.info("CLIENT_SMOKE world entered: dimension={}, player={}, integratedServer={}, gameTime={}",
                client.level.dimension().location(), client.player.getGameProfile().getName(),
                server != null, client.level.getGameTime());
            if (server != null) server.execute(() -> {
                if (Boolean.getBoolean("neoecoae.gtoAcceptance")) {
                    var recipes = server.getRecipeManager();
                    long own = recipes.getRecipeIds().filter(cn.dancingsnow.neoecoae.compat.gto.ECOGtoRecipes::owns).count();
                    long small = recipes.getRecipes().stream().filter(cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe.class::isInstance).count();
                    var large = cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes.getAll(server.overworld());
                    LOG.info("GTO_ACCEPTANCE server recipes: eco={}, integrated={}, large={}", own, small, large.size());
                    if (own == 0 || small == 0 || large.isEmpty()) throw new IllegalStateException("GTO ECO recipe catalogue is empty");
                    cn.dancingsnow.neoecoae.compat.gto.ECOGtoMachineRecipes.verifyFinalized();
                    LOG.info("GTO_ACCEPTANCE all 23 native machine recipes verified");
                    cn.dancingsnow.neoecoae.compat.gto.ECOGtoPatternPush.verifyContract();
                    var cpu = new appeng.me.cluster.implementations.CraftingCPUCluster(
                        net.minecraft.core.BlockPos.ZERO, net.minecraft.core.BlockPos.ZERO);
                    if (!(cpu.craftingLogic instanceof cn.dancingsnow.neoecoae.compat.gto.ECOGtoCraftingCpuLogic)) {
                        throw new IllegalStateException("GTO CPU bridge did not attach");
                    }
                    LOG.info("GTO_ACCEPTANCE provider callback and native CPU ledger contracts verified");
                }
            });
            if (Boolean.getBoolean("neoecoae.gtoAcceptance")) {
                long own = client.level.getRecipeManager().getRecipeIds()
                    .filter(cn.dancingsnow.neoecoae.compat.gto.ECOGtoRecipes::owns).count();
                LOG.info("GTO_ACCEPTANCE client recipes: eco={}", own);
            }
            Screenshot.grab(client.gameDirectory, "neoecoae-startup-world.png", client.getMainRenderTarget(),
                message -> LOG.info("CLIENT_SMOKE screenshot: {}", message.getString()));
        }
        if (captured && Boolean.getBoolean("neoecoae.clientSmokeEnergy")) {
            ManagedEnergyClientProbe.tick(client);
        }
        if (captured && Boolean.getBoolean("neoecoae.upstreamSyncSmoke")) {
            WorkstationSyncClientProbe.tick(client);
        }
        if (captured && client.level != null && client.player != null
                && !System.getProperty("neoecoae.workstationModelSmoke", "").isBlank()) {
            WorkstationModelClientProbe.tick(client);
        }
        if (captured && client.level != null && client.player != null
                && !System.getProperty("neoecoae.multiblockAlignmentSmoke", "").isBlank()) {
            MultiblockAlignmentClientProbe.tick(client);
        }
        if (captured && client.level != null && client.player != null
                && Boolean.getBoolean("neoecoae.clientSmokeInventories")) {
            ManagedInventoryClientProbe.tick(client);
        }
        if (captured && client.level != null && client.player != null && Boolean.getBoolean("neoecoae.clientSmokeMenus")) {
            worldTicks++;
            if (worldTicks == 240) {
                var server = client.getSingleplayerServer();
                var id = client.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    var level = player.serverLevel();
                    var pos = player.blockPosition().offset(2, 0, 0);
                    workstationPos = pos;
                    var block = cn.dancingsnow.neoecoae.all.NEBlocks.INTEGRATED_WORKING_STATION.get();
                    level.setBlockAndUpdate(pos, block.defaultBlockState());
                });
            }
            if (worldTicks == 260) {
                var server = client.getSingleplayerServer();
                var id = client.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    var level = player.serverLevel();
                    var pos = workstationPos;
                    var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.UP, pos, false);
                    var result = level.getBlockState(pos).use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    LOG.info("CLIENT_SMOKE workstation activation={}, menu={}", result, player.containerMenu.getClass().getName());
                });
            }
            if (worldTicks == 300) {
                LOG.info("CLIENT_SMOKE workstation screen={}", currentScreen);
                Screenshot.grab(client.gameDirectory, "neoecoae-startup-workstation.png", client.getMainRenderTarget(),
                    message -> LOG.info("CLIENT_SMOKE screenshot: {}", message.getString()));
            }
            if (worldTicks == 340) client.player.closeContainer();
        }
        if (captured && client.level != null && client.player != null
                && Boolean.getBoolean("neoecoae.gtoAcceptance") && worldTicks >= 360) {
            verifyGtoInteractions(client);
        }
    }

    /** Real grid, native batch, part menu and client action; no mocked world or provider state. */
    private static void verifyGtoInteractions(Minecraft client) {
        if (gtoPhase < 0) return;
        if (gtoPhase == 4) {
            if (!gtoScreenCaptured) {
                gtoScreenCaptured = true;
                Screenshot.grab(client.gameDirectory, "neoecoae-gto-me2in1.png", client.getMainRenderTarget(),
                    message -> LOG.info("GTO_ACCEPTANCE terminal screenshot: {}", message.getString()));
            }
            return;
        }
        if (++gtoTicks > 600) {
            gtoPhase = -1;
            LOG.error("GTO_ACCEPTANCE timed out waiting for native grid or terminal");
            return;
        }
        if ((gtoPhase < 2 || gtoPhase == 3 || gtoPhase == 5) && gtoTicks % 20 == 0) {
            var server = client.getSingleplayerServer();
            var id = client.player.getUUID();
            server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(id);
                    var level = player.serverLevel();
                    if (gtoPhase == 0) {
                        var origin = player.blockPosition().above(16);
                        level.setBlockAndUpdate(origin.north(3).below(), net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState());
                        player.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() - 2.5);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        gtoProviderPos = origin.east(4);
                        level.setBlockAndUpdate(gtoProviderPos, appeng.core.definitions.AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
                        level.setBlockAndUpdate(gtoProviderPos.east(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
                        level.setBlockAndUpdate(gtoProviderPos.west(), appeng.core.definitions.AEBlocks.CABLE_BUS.block().defaultBlockState());
                        var hub = (appeng.api.parts.IPartHost) level.getBlockEntity(gtoProviderPos.west());
                        var cable = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                            new net.minecraft.resources.ResourceLocation("ae2:fluix_glass_cable"));
                        hub.addPart((appeng.api.parts.IPartItem<?>) cable, null, player);
                        level.setBlockAndUpdate(gtoProviderPos.west().above(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        level.setBlockAndUpdate(gtoProviderPos.west().north(), appeng.core.definitions.AEBlocks.CONTROLLER.block().defaultBlockState());
                        var provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) level.getBlockEntity(gtoProviderPos);
                        var pattern = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                            new appeng.api.stacks.GenericStack[]{new appeng.api.stacks.GenericStack(
                                appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 3)},
                            new appeng.api.stacks.GenericStack[]{new appeng.api.stacks.GenericStack(
                                appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 1)});
                        provider.getLogic().getPatternInv().setItemDirect(0, pattern);
                        provider.getLogic().updatePatterns();
                        var terminalPos = gtoProviderPos.west().south();
                        level.setBlockAndUpdate(terminalPos, appeng.core.definitions.AEBlocks.CABLE_BUS.block().defaultBlockState());
                        var host = (appeng.api.parts.IPartHost) level.getBlockEntity(terminalPos);
                        host.addPart((appeng.api.parts.IPartItem<?>) cable, null, player);
                        var terminal = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(item -> {
                            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                            return item instanceof appeng.api.parts.IPartItem<?> && key.getNamespace().equals("gtocore")
                                && key.getPath().matches(".*me.*2.*1.*");
                        }).findFirst().orElseThrow(() -> new IllegalStateException("GTO ME2in1 part item unavailable"));
                        gtoTerminal = host.addPart((appeng.api.parts.IPartItem<?>) terminal, net.minecraft.core.Direction.SOUTH, player);
                        var controllerPos = gtoProviderPos.west(3);
                        var controllerState = cn.dancingsnow.neoecoae.all.NEBlocks.CRAFTING_SYSTEM_L4.getDefaultState()
                            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                                net.minecraft.core.Direction.NORTH);
                        level.setBlockAndUpdate(controllerPos, controllerState);
                        var plan = cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService.preview(
                            level, controllerPos, controllerState, cn.dancingsnow.neoecoae.all.NEMultiBlocks.CRAFTING_SYSTEM_L4,
                            1, false);
                        if (!cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService.buildInstant(level, plan, player))
                            throw new IllegalStateException("GTO acceptance crafting host could not be built");
                        var controller = (cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity)
                            level.getBlockEntity(controllerPos);
                        controller.rebuildMultiblock();
                        if (!controller.isFormed()) throw new IllegalStateException("GTO acceptance crafting host did not form");
                        gtoPatternBus = controller.getCluster().getPatternDomainBuses().get(0);
                        LOG.info("GTO_ACCEPTANCE native fixtures built; terminal={}",
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(terminal));
                        gtoPhase = 1;
                    } else if (gtoPhase == 1) {
                        var provider = (appeng.blockentity.crafting.PatternProviderBlockEntity) level.getBlockEntity(gtoProviderPos);
                        if (!provider.getMainNode().isActive() || gtoTerminal.getGridNode() == null
                                || !gtoTerminal.getGridNode().isActive() || !gtoPatternBus.getMainNode().isActive()) {
                            var node = provider.getMainNode().getNode();
                            var terminalNode = gtoTerminal.getGridNode();
                            LOG.info("GTO_ACCEPTANCE grid waiting: providerNode={}, powered={}, booted={}, channels={}, terminalNode={}, terminalPowered={}, terminalChannels={}",
                                node != null, node != null && node.isPowered(), node != null && node.hasGridBooted(),
                                node != null && node.meetsChannelRequirements(), terminalNode != null,
                                terminalNode != null && terminalNode.isPowered(), terminalNode != null && terminalNode.meetsChannelRequirements());
                            return;
                        }
                        var pattern = provider.getLogic().getAvailablePatterns().get(0);
                        var inputs = new appeng.api.stacks.KeyCounter();
                        inputs.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 3);
                        if (!provider.getLogic().pushPattern(pattern, new appeng.api.stacks.KeyCounter[]{inputs}))
                            throw new IllegalStateException("GTO native single batch was rejected");
                        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(gtoProviderPos.east());
                        int received = 0;
                        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                            var stack = chest.getItem(slot);
                            if (stack.is(net.minecraft.world.item.Items.IRON_INGOT)) received += stack.getCount();
                        }
                        if (received != 3) throw new IllegalStateException("GTO single batch inserted " + received + " instead of 3");
                        LOG.info("GTO_ACCEPTANCE native single batch inserted exactly 3 iron ingots");
                        player.closeContainer();
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                        if (!gtoTerminal.onActivate(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                                new net.minecraft.world.phys.Vec3(0.5, 0.5, 1)))
                            throw new IllegalStateException("GTO terminal did not open");
                        var menu = (appeng.menu.AEBaseMenu) player.containerMenu;
                        if (!menu.isValidMenu() || !gtoTerminal.getGridNode().isActive())
                            throw new IllegalStateException("GTO terminal has no active menu node");
                        var encodedSlots = menu.getSlots(appeng.menu.SlotSemantics.ENCODED_PATTERN);
                        if (encodedSlots.isEmpty()) throw new IllegalStateException("GTO terminal has no encoded-pattern slots");
                        gtoEncodedSlot = encodedSlots.get(0);
                        gtoPendingPattern = gtoCraftingPattern(level, 0);
                        gtoEncodedSlot.set(gtoPendingPattern.copy());
                        menu.broadcastChanges();
                        gtoPhase = 5;
                    } else if (gtoPhase == 5) {
                        var menu = (appeng.menu.AEBaseMenu) player.containerMenu;
                        cn.dancingsnow.neoecoae.compat.gto.ECOGtoPatternTerminal.upload(menu);
                        var catalog = gtoTerminal.getGridNode().getGrid()
                            .getService(cn.dancingsnow.neoecoae.api.IECOPatternStorageService.class);
                        if (gtoEncodedSlot.hasItem() || !catalog.containsPatternInNetwork(gtoPendingPattern))
                            throw new IllegalStateException("GTO terminal server upload did not settle its encoded pattern");
                        LOG.info("GTO_ACCEPTANCE native terminal server upload moved first pattern into ECO bus");
                        gtoPendingPattern = gtoCraftingPattern(level, 1);
                        gtoEncodedSlot.set(gtoPendingPattern.copy());
                        menu.broadcastChanges();
                        gtoPhase = 2;
                    } else if (gtoPhase == 3) {
                        var catalog = gtoTerminal.getGridNode().getGrid()
                            .getService(cn.dancingsnow.neoecoae.api.IECOPatternStorageService.class);
                        if (gtoEncodedSlot.hasItem() || !catalog.containsPatternInNetwork(gtoPendingPattern)) return;
                        if (gtoPatternBus.getAvailablePatterns().size() != 2)
                            throw new IllegalStateException("GTO terminal upload did not preserve exactly two ECO patterns");
                        LOG.info("GTO_ACCEPTANCE native terminal upload button settled second pattern; ECO bus contains exactly 2 patterns");
                        gtoPhase = 4;
                    }
                } catch (Throwable failure) {
                    gtoPhase = -1;
                    LOG.error("GTO_ACCEPTANCE native interaction failed", failure);
                }
            });
        }
        if (gtoPhase == 2 && client.screen instanceof appeng.client.gui.AEBaseScreen<?> screen
                && cn.dancingsnow.neoecoae.compat.gto.ECOGtoPatternTerminal.isMenu(screen.getMenu())) {
            var upload = (cn.dancingsnow.neoecoae.client.gui.widget.GtoPatternUploadButton)
                screen.getWidgets().getCompositeWidgets().get("neoecoaeUploadPattern");
            if (upload == null) {
                gtoPhase = -1;
                LOG.error("GTO_ACCEPTANCE native terminal upload widget is missing; widgets={}",
                    screen.getWidgets().getCompositeWidgets().keySet());
                return;
            }
            if (!upload.visible || !upload.active) return;
            LOG.info("GTO_ACCEPTANCE upload button bounds: x={}, y={}, width={}, height={}",
                upload.getX(), upload.getY(), upload.getWidth(), upload.getHeight());
            upload.onPress();
            LOG.info("GTO_ACCEPTANCE native terminal screen upload button sent client action: {}", client.screen.getClass().getName());
            gtoPhase = 3;
        }
    }

    /** GTO removes vanilla recipes; samples come from its finalized crafting catalogue. */
    private static net.minecraft.world.item.ItemStack gtoCraftingPattern(
            net.minecraft.server.level.ServerLevel level, int index) {
        var recipes = level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
            .stream().filter(recipe -> recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped
                && shaped.getWidth() <= 3 && shaped.getHeight() <= 3 && !recipe.isSpecial()
                && !recipe.getResultItem(level.registryAccess()).isEmpty()
                && recipe.getIngredients().stream().allMatch(ingredient -> ingredient.isEmpty() || ingredient.getItems().length > 0))
            .sorted(java.util.Comparator.comparing(recipe -> recipe.getId().toString())).toList();
        if (recipes.size() <= index) throw new IllegalStateException("GTO has too few sample crafting recipes");
        var recipe = (net.minecraft.world.item.crafting.ShapedRecipe) recipes.get(index);
        var inputs = new net.minecraft.world.item.ItemStack[9];
        java.util.Arrays.fill(inputs, net.minecraft.world.item.ItemStack.EMPTY);
        for (int y = 0; y < recipe.getHeight(); y++) {
            for (int x = 0; x < recipe.getWidth(); x++) {
                var ingredient = recipe.getIngredients().get(y * recipe.getWidth() + x);
                if (!ingredient.isEmpty()) inputs[y * 3 + x] = ingredient.getItems()[0].copyWithCount(1);
            }
        }
        var pattern = appeng.api.crafting.PatternDetailsHelper.encodeCraftingPattern(recipe, inputs,
            recipe.getResultItem(level.registryAccess()), false, false);
        if (appeng.api.crafting.PatternDetailsHelper.decodePattern(pattern, level) == null)
            throw new IllegalStateException("GTO sample crafting recipe did not encode: " + recipe.getId());
        LOG.info("GTO_ACCEPTANCE upload sample recipe={}", recipe.getId());
        return pattern;
    }
}
