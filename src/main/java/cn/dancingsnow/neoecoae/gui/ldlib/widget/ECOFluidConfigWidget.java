package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static com.lowdragmc.lowdraglib.gui.util.DrawerHelper.drawStringFixedCorner;
import static com.lowdragmc.lowdraglib.gui.util.DrawerHelper.drawStringSized;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.me.ECOAEFluidSlot;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.me.ECOAEFluidSlotList;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.misc.IGhostFluidTarget;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.list.AEListGridWidget;
import com.gregtechceu.gtceu.integration.ae2.slot.IConfigurableSlot;
import com.gregtechceu.gtceu.integration.ae2.utils.AEUtil;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTMath;
import com.gtocore.common.machine.multiblock.part.ae.slots.ExportOnlyAEFluidSlot;
import com.gtocore.common.machine.multiblock.part.ae.slots.ExportOnlyAESlot;
import com.gtocore.utils.AdvMathExpParser;
import com.lowdragmc.lowdraglib.gui.ingredient.IIngredientSlot;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidHelperImpl;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.NotNull;

public class ECOFluidConfigWidget extends WidgetGroup {
    private static final int UPDATE_ID = 1000;
    private static final int SLOT_SIZE = 18;
    private static final int CONFIG_COLUMNS = 8;
    private static final int CONFIG_ROWS = 2;
    private static final int CONFIG_SLOT_H = SLOT_SIZE * 2;
    private static final int CONFIG_ROW_STEP = CONFIG_SLOT_H + 2;
    private static final int CONFIG_GRID_W = CONFIG_COLUMNS * SLOT_SIZE;
    private static final int CONFIG_GRID_H = CONFIG_ROWS * CONFIG_ROW_STEP - 2;
    private static final int AMOUNT_POPUP_W = 80;
    private static final int AMOUNT_POPUP_H = 30;
    private static final int AMOUNT_POPUP_GAP = 2;
    private static final int AMOUNT_POPUP_X = (CONFIG_GRID_W - AMOUNT_POPUP_W) / 2;
    private static final int AMOUNT_POPUP_Y = CONFIG_GRID_H + AMOUNT_POPUP_GAP;
    private static final int WIDGET_W = CONFIG_GRID_W;
    private static final int WIDGET_H = AMOUNT_POPUP_Y + AMOUNT_POPUP_H;

    final IConfigurableSlot[] config;
    IConfigurableSlot[] cached;
    IConfigurableSlot[] displayList;

    private final ECOAEFluidSlotList fluidList;
    private final Int2ObjectMap<IConfigurableSlot> changeMap = new Int2ObjectOpenHashMap<>();
    private final AmountSetWidget amountSetWidget;
    private final int gridOffsetY;
    private final int amountPopupX;
    private final int amountPopupY;
    private final boolean fixedAmountPopup;

    @Getter
    private final boolean isStocking;

    public static int gridWidth() {
        return CONFIG_GRID_W;
    }

    public static int gridHeight() {
        return CONFIG_GRID_H;
    }

    public static int widgetHeight() {
        return WIDGET_H;
    }

    public static int amountPopupWidth() {
        return AMOUNT_POPUP_W;
    }

    public static int amountPopupHeight() {
        return AMOUNT_POPUP_H;
    }

    public ECOFluidConfigWidget(int x, int y, ECOAEFluidSlotList list) {
        this(x, y, list, 0, AMOUNT_POPUP_X, AMOUNT_POPUP_Y, false);
    }

    public ECOFluidConfigWidget(
            int x, int y, ECOAEFluidSlotList list, int gridOffsetY, int amountPopupX, int amountPopupY, boolean fixed) {
        super(new Position(x, y), new Size(WIDGET_W, widgetHeight(gridOffsetY, amountPopupY)));
        this.fluidList = list;
        this.config = list.getInventory();
        this.isStocking = list.isStocking();
        this.gridOffsetY = Math.max(0, gridOffsetY);
        this.amountPopupX = Math.max(0, Math.min(amountPopupX, WIDGET_W - AMOUNT_POPUP_W));
        this.amountPopupY = Math.max(0, amountPopupY);
        this.fixedAmountPopup = fixed;
        this.init();
        this.amountSetWidget = new AmountSetWidget(this.amountPopupX, this.amountPopupY, this);
        this.addWidget(this.amountSetWidget);
        this.addWidget(this.amountSetWidget.getAmountText());
        this.amountSetWidget.setVisible(false);
        this.amountSetWidget.getAmountText().setVisible(false);
    }

    private static int widgetHeight(int gridOffsetY, int amountPopupY) {
        return Math.max(Math.max(0, gridOffsetY) + CONFIG_GRID_H, Math.max(0, amountPopupY) + AMOUNT_POPUP_H);
    }

    private void init() {
        int line;
        this.displayList = new IConfigurableSlot[this.config.length];
        this.cached = new IConfigurableSlot[this.config.length];
        for (int index = 0; index < this.config.length; index++) {
            this.displayList[index] = new ECOAEFluidSlot();
            this.cached[index] = new ECOAEFluidSlot();
            line = index / CONFIG_COLUMNS;
            this.addWidget(new ECOFluidConfigSlotWidget(
                    (index - line * CONFIG_COLUMNS) * SLOT_SIZE, gridOffsetY + line * CONFIG_ROW_STEP, this, index));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void enableAmountClient(int slotIndex) {
        this.amountSetWidget.setSlotIndexClient(slotIndex);
        this.amountSetWidget.moveToSlot(slotIndex);
        this.amountSetWidget.setVisible(true);
        this.amountSetWidget.getAmountText().setVisible(true);
    }

    @OnlyIn(Dist.CLIENT)
    public void disableAmountClient() {
        this.amountSetWidget.setSlotIndexClient(-1);
        this.amountSetWidget.setVisible(false);
        this.amountSetWidget.getAmountText().setVisible(false);
    }

    public void enableAmount(int slotIndex) {
        this.amountSetWidget.setSlotIndex(slotIndex);
        this.amountSetWidget.moveToSlot(slotIndex);
        this.amountSetWidget.setVisible(true);
        this.amountSetWidget.getAmountText().setVisible(true);
    }

    public void disableAmount() {
        this.amountSetWidget.setSlotIndex(-1);
        this.amountSetWidget.setVisible(false);
        this.amountSetWidget.getAmountText().setVisible(false);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.amountSetWidget.isVisible()) {
            if (this.amountSetWidget.getAmountText().mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (!this.amountSetWidget.isMouseOverElement(mouseX, mouseY)) {
                this.disableAmountClient();
            }
        }
        for (Widget w : this.widgets) {
            if (w instanceof ECOConfigSlotWidget slot) {
                slot.setSelect(false);
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean hasStackInConfig(GenericStack stack) {
        return fluidList.hasStackInConfig(stack, true);
    }

    public boolean isAutoPull() {
        return fluidList.isAutoPull();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.changeMap.clear();
        for (int index = 0; index < this.config.length; index++) {
            IConfigurableSlot newSlot = this.config[index];
            IConfigurableSlot oldSlot = this.cached[index];
            GenericStack nConfig = newSlot.getConfig();
            GenericStack nStock = newSlot.getStock();
            GenericStack oConfig = oldSlot.getConfig();
            GenericStack oStock = oldSlot.getStock();
            if (!areAEStackCountsEqual(nConfig, oConfig) || !areAEStackCountsEqual(nStock, oStock)) {
                this.changeMap.put(index, newSlot.copy());
                this.cached[index] = this.config[index].copy();
                this.gui.holder.markAsDirty();
            }
        }
        if (!this.changeMap.isEmpty()) {
            this.writeUpdateInfo(UPDATE_ID, buf -> {
                buf.writeVarInt(this.changeMap.size());
                for (int index : this.changeMap.keySet()) {
                    GenericStack sConfig = this.changeMap.get(index).getConfig();
                    GenericStack sStock = this.changeMap.get(index).getStock();
                    buf.writeVarInt(index);
                    if (sConfig != null) {
                        buf.writeBoolean(true);
                        GenericStack.writeBuffer(sConfig, buf);
                    } else {
                        buf.writeBoolean(false);
                    }
                    if (sStock != null) {
                        buf.writeBoolean(true);
                        GenericStack.writeBuffer(sStock, buf);
                    } else {
                        buf.writeBoolean(false);
                    }
                }
            });
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        super.readUpdateInfo(id, buffer);
        if (id == UPDATE_ID) {
            int size = buffer.readVarInt();
            for (int i = 0; i < size; i++) {
                int index = buffer.readVarInt();
                IConfigurableSlot slot = this.displayList[index];
                if (buffer.readBoolean()) {
                    slot.setConfig(GenericStack.readBuffer(buffer));
                } else {
                    slot.setConfig(null);
                }
                if (buffer.readBoolean()) {
                    slot.setStock(GenericStack.readBuffer(buffer));
                } else {
                    slot.setStock(null);
                }
            }
        }
    }

    public final IConfigurableSlot getConfig(int index) {
        return this.config[index];
    }

    public final IConfigurableSlot getDisplay(int index) {
        return this.displayList[index];
    }

    private static boolean areAEStackCountsEqual(GenericStack s1, GenericStack s2) {
        if (s2 == s1) {
            return true;
        }
        if (s1 != null && s2 != null) {
            return s1.amount() == s2.amount() && s1.what().matches(s2);
        }
        return false;
    }

    private static class ECOConfigSlotWidget extends Widget implements IIngredientSlot {
        static final int REMOVE_ID = 1000;
        static final int UPDATE_ID = 1001;
        static final int AMOUNT_CHANGE_ID = 1002;
        static final int SLOT_CLICK_ID = 1003;

        final ECOFluidConfigWidget parentWidget;
        final int index;

        @Setter
        boolean select = false;

        ECOConfigSlotWidget(Position pos, Size size, ECOFluidConfigWidget widget, int index) {
            super(pos, size);
            this.parentWidget = widget;
            this.index = index;
        }

        @OnlyIn(Dist.CLIENT)
        @Override
        public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            IConfigurableSlot slot = this.parentWidget.getDisplay(this.index);
            if (slot.getConfig() == null) {
                if (mouseOverConfig(mouseX, mouseY)) {
                    List<Component> hoverStringList = new ArrayList<>();
                    hoverStringList.add(Component.translatable("gtceu.gui.config_slot"));
                    if (parentWidget.isAutoPull()) {
                        hoverStringList.add(Component.translatable("gtceu.gui.config_slot.auto_pull_managed"));
                    } else {
                        if (!parentWidget.isStocking()) {
                            hoverStringList.add(Component.translatable("gtceu.gui.config_slot.set"));
                            hoverStringList.add(Component.translatable("gtceu.gui.config_slot.scroll"));
                        } else {
                            hoverStringList.add(Component.translatable("gtceu.gui.config_slot.set_only"));
                        }
                        hoverStringList.add(Component.translatable("gtceu.gui.config_slot.remove"));
                    }
                    setHoverTooltips(hoverStringList);
                }
            } else {
                GenericStack item = null;
                if (mouseOverConfig(mouseX, mouseY)) {
                    item = slot.getConfig();
                } else if (mouseOverStock(mouseX, mouseY)) {
                    item = slot.getStock();
                }
                if (item != null) {
                    setHoverTooltips(
                            Screen.getTooltipFromItem(Minecraft.getInstance(), GenericStack.wrapInItemStack(item)));
                }
            }
        }

        boolean mouseOverConfig(double mouseX, double mouseY) {
            Position position = getPosition();
            return isMouseOver(position.x, position.y, 18, 18, mouseX, mouseY);
        }

        boolean mouseOverStock(double mouseX, double mouseY) {
            Position position = getPosition();
            return isMouseOver(position.x, position.y + 18, 18, 18, mouseX, mouseY);
        }

        boolean isStackValidForSlot(GenericStack stack) {
            if (stack == null || stack.amount() < 0) {
                return true;
            }
            if (!parentWidget.isStocking()) {
                return true;
            }
            return !parentWidget.hasStackInConfig(stack);
        }

        public Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
            IConfigurableSlot slot = this.parentWidget.getDisplay(this.index);
            if (slot == null) {
                return null;
            }
            GenericStack stack = null;
            if (this.mouseOverConfig(mouseX, mouseY)) {
                stack = slot.getConfig();
            } else if (this.mouseOverStock(mouseX, mouseY)) {
                stack = slot.getStock();
            }

            if (stack == null || stack.what() == null) {
                return null;
            }

            EmiStack emiStack = EmiStackHelper.toEmiStack(stack);
            if (emiStack != null) {
                if (emiStack.getAmount() == 0L) {
                    emiStack.setAmount(1L);
                }

                return new EmiStackInteraction(emiStack, null, false);
            }
            return null;
        }
    }

    private static class ECOFluidConfigSlotWidget extends ECOConfigSlotWidget implements IGhostFluidTarget {
        ECOFluidConfigSlotWidget(int x, int y, ECOFluidConfigWidget widget, int index) {
            super(new Position(x, y), new Size(18, 36), widget, index);
        }

        @OnlyIn(Dist.CLIENT)
        @Override
        public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            Position position = getPosition();
            IConfigurableSlot slot = this.parentWidget.getDisplay(this.index);
            GenericStack config = slot.getConfig();
            GenericStack stock = slot.getStock();
            drawSlots(graphics, mouseX, mouseY, position.x, position.y, parentWidget.isAutoPull());
            if (this.select) {
                GuiTextures.SELECT_BOX.draw(graphics, mouseX, mouseY, position.x, position.y, 18, 18);
            }

            int stackX = position.x + 1;
            int stackY = position.y + 1;
            if (config != null) {
                var stack = AEUtil.toFluidStack(config);
                if (!stack.isEmpty()) {
                    DrawerHelper.drawFluidForGui(
                            graphics, FluidHelperImpl.toFluidStack(stack), config.amount(), stackX, stackY, 16, 16);
                    if (!parentWidget.isStocking()) {
                        String amountStr = FormattingUtil.formatNumberReadable(
                                config.amount(), true, FormattingUtil.DECIMAL_FORMAT_0F, "B");
                        drawStringFixedCorner(graphics, amountStr, stackX + 17, stackY + 17, 16777215, true, 0.5f);
                    }
                }
            }
            if (stock != null) {
                var stack = AEUtil.toFluidStack(stock);
                if (!stack.isEmpty()) {
                    DrawerHelper.drawFluidForGui(
                            graphics, FluidHelperImpl.toFluidStack(stack), stock.amount(), stackX, stackY + 18, 16, 16);
                    String amountStr = FormattingUtil.formatNumberReadable(
                            stock.amount(), true, FormattingUtil.DECIMAL_FORMAT_0F, "B");
                    drawStringFixedCorner(graphics, amountStr, stackX + 17, stackY + 18 + 17, 16777215, true, 0.5f);
                }
            }

            if (mouseOverConfig(mouseX, mouseY)) {
                AEListGridWidget.drawSelectionOverlay(graphics, stackX, stackY, 16, 16);
            } else if (mouseOverStock(mouseX, mouseY)) {
                AEListGridWidget.drawSelectionOverlay(graphics, stackX, stackY + 18, 16, 16);
            }
        }

        @OnlyIn(Dist.CLIENT)
        private static void drawSlots(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, boolean autoPull) {
            if (autoPull) {
                GuiTextures.SLOT_DARK.draw(graphics, mouseX, mouseY, x, y, 18, 18);
                GuiTextures.CONFIG_ARROW_DARK.draw(graphics, mouseX, mouseY, x, y, 18, 18);
            } else {
                GuiTextures.FLUID_SLOT.draw(graphics, mouseX, mouseY, x, y, 18, 18);
                GuiTextures.CONFIG_ARROW.draw(graphics, mouseX, mouseY, x, y, 18, 18);
            }
            GuiTextures.SLOT_DARK.draw(graphics, mouseX, mouseY, x, y + 18, 18, 18);
        }

        @OnlyIn(Dist.CLIENT)
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseOverConfig(mouseX, mouseY)) {
                if (parentWidget.isAutoPull()) {
                    return false;
                }

                if (button == 1) {
                    writeClientAction(REMOVE_ID, buf -> {});

                    if (!parentWidget.isStocking()) {
                        this.parentWidget.disableAmountClient();
                    }
                } else if (button == 0) {
                    ItemStack hold = this.gui.getModularUIContainer().getCarried();
                    FluidUtil.getFluidContained(hold).ifPresent(f -> writeClientAction(UPDATE_ID, f::writeToPacket));

                    if (!parentWidget.isStocking()) {
                        this.parentWidget.enableAmountClient(this.index);
                        this.select = true;
                    }
                }
                return true;
            } else if (mouseOverStock(mouseX, mouseY)) {
                if (button != 0) {
                    return false;
                }
                if (parentWidget.isStocking()) {
                    return false;
                }
                if (this.parentWidget.getDisplay(this.index).getStock() != null) {
                    writeClientAction(SLOT_CLICK_ID, buf -> buf.writeBoolean(isShiftDown()));
                }
                return true;
            }
            return false;
        }

        @Override
        public void handleClientAction(int id, FriendlyByteBuf buffer) {
            super.handleClientAction(id, buffer);
            IConfigurableSlot slot = this.parentWidget.getConfig(this.index);
            switch (id) {
                case REMOVE_ID -> {
                    slot.setConfig(null);
                    this.parentWidget.disableAmount();
                    writeUpdateInfo(REMOVE_ID, buf -> {});
                }
                case UPDATE_ID -> {
                    FluidStack fluid = FluidStack.readFromPacket(buffer);
                    var stack = AEUtil.fromFluidStack(fluid);
                    if (!isStackValidForSlot(stack)) {
                        return;
                    }
                    slot.setConfig(stack);
                    this.parentWidget.enableAmount(this.index);
                    if (fluid != FluidStack.EMPTY) {
                        writeUpdateInfo(UPDATE_ID, fluid::writeToPacket);
                    }
                }
                case AMOUNT_CHANGE_ID -> {
                    if (slot.getConfig() != null) {
                        int amt = buffer.readInt();
                        slot.setConfig(ExportOnlyAESlot.copy(slot.getConfig(), amt));
                        writeUpdateInfo(AMOUNT_CHANGE_ID, buf -> buf.writeInt(amt));
                    }
                }
                case SLOT_CLICK_ID -> {
                    if (slot.getStock() != null) {
                        boolean isShiftDown = buffer.readBoolean();
                        int clickResult = tryClickContainer(isShiftDown);
                        if (clickResult >= 0) {
                            writeUpdateInfo(SLOT_CLICK_ID, buf -> buf.writeVarInt(clickResult));
                        }
                    }
                }
            }
        }

        @OnlyIn(Dist.CLIENT)
        @Override
        public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
            super.readUpdateInfo(id, buffer);
            IConfigurableSlot slot = this.parentWidget.getDisplay(this.index);
            switch (id) {
                case REMOVE_ID -> slot.setConfig(null);
                case UPDATE_ID -> {
                    FluidStack fluid = FluidStack.readFromPacket(buffer);
                    slot.setConfig(AEUtil.fromFluidStack(fluid));
                }
                case AMOUNT_CHANGE_ID -> {
                    if (slot.getConfig() != null) {
                        int amt = buffer.readInt();
                        slot.setConfig(ExportOnlyAESlot.copy(slot.getConfig(), amt));
                    }
                }
                case SLOT_CLICK_ID -> {
                    if (slot.getStock() != null && slot.getStock().what() instanceof AEFluidKey key) {
                        ItemStack currentStack = gui.getModularUIContainer().getCarried();
                        int newStackSize = buffer.readVarInt();
                        currentStack.setCount(newStackSize);
                        gui.getModularUIContainer().setCarried(currentStack);

                        FluidStack stack = new FluidStack(
                                key.getFluid(),
                                GTMath.saturatedCast(slot.getStock().amount()),
                                key.getTag());
                        GenericStack stack1 = ExportOnlyAESlot.copy(
                                slot.getStock(), Math.max(0, (slot.getStock().amount() - stack.getAmount())));
                        slot.setStock(stack1.amount() == 0 ? null : stack1);
                    }
                }
            }
        }

        @OnlyIn(Dist.CLIENT)
        @Override
        public Rect2i getRectangleBox() {
            Rect2i rectangle = toRectangleBox();
            rectangle.setHeight(rectangle.getHeight() / 2);
            return rectangle;
        }

        @OnlyIn(Dist.CLIENT)
        @Override
        public void acceptFluid(FluidStack fluidStack) {
            if (fluidStack.getRawFluid() != Fluids.EMPTY && fluidStack.getAmount() <= 0L) {
                fluidStack.setAmount(1000);
            }

            if (!fluidStack.isEmpty()) {
                writeClientAction(UPDATE_ID, fluidStack::writeToPacket);
            }
        }

        @OnlyIn(Dist.CLIENT)
        @Override
        public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
            if (parentWidget.isStocking()) {
                return false;
            }
            IConfigurableSlot slot = this.parentWidget.getDisplay(this.index);
            Rect2i rectangle = toRectangleBox();
            rectangle.setHeight(rectangle.getHeight() / 2);
            if (slot.getConfig() == null || wheelDelta == 0 || !rectangle.contains((int) mouseX, (int) mouseY)) {
                return false;
            }
            FluidStack fluid = slot.getConfig().what() instanceof AEFluidKey fluidKey
                    ? new FluidStack(
                            fluidKey.getFluid(),
                            GTMath.saturatedCast(slot.getConfig().amount()),
                            fluidKey.getTag())
                    : FluidStack.EMPTY;
            long amt;
            if (isCtrlDown()) {
                amt = wheelDelta > 0 ? fluid.getAmount() : fluid.getAmount() / 2L;
            } else {
                amt = wheelDelta > 0 ? fluid.getAmount() + 1L : fluid.getAmount() - 1L;
            }

            if (amt > 0 && amt < Integer.MAX_VALUE + 1L) {
                int finalAmt = (int) amt;
                writeClientAction(AMOUNT_CHANGE_ID, buf -> buf.writeInt(finalAmt));
                return true;
            }
            return false;
        }

        private int tryClickContainer(boolean isShiftKeyDown) {
            ExportOnlyAEFluidSlot fluidTank =
                    this.parentWidget.getConfig(this.index) instanceof ExportOnlyAEFluidSlot fluid ? fluid : null;
            if (fluidTank == null) {
                return -1;
            }
            Player player = gui.entityPlayer;
            ItemStack currentStack = gui.getModularUIContainer().getCarried();
            var handler = FluidUtil.getFluidHandler(currentStack).resolve().orElse(null);
            if (handler == null) {
                return -1;
            }
            int maxAttempts = isShiftKeyDown ? currentStack.getCount() : 1;

            if (!fluidTank.getFluidInTank(0).isEmpty()) {
                boolean performedFill = false;
                FluidStack initialFluid = fluidTank.getFluidInTank(0);
                for (int i = 0; i < maxAttempts; i++) {
                    FluidActionResult result =
                            FluidUtil.tryFillContainer(currentStack, fluidTank, Integer.MAX_VALUE, null, false);
                    if (!result.isSuccess()) {
                        break;
                    }
                    ItemStack remainingStack = FluidUtil.tryFillContainer(
                                    currentStack, fluidTank, Integer.MAX_VALUE, null, true)
                            .getResult();
                    currentStack.shrink(1);
                    performedFill = true;
                    if (!remainingStack.isEmpty() && !player.addItem(remainingStack)) {
                        Block.popResource(player.level(), player.getOnPos(), remainingStack);
                        break;
                    }
                }
                if (performedFill) {
                    SoundEvent soundevent =
                            initialFluid.getFluid().getFluidType().getSound(initialFluid, SoundActions.BUCKET_FILL);
                    if (soundevent != null) {
                        player.level()
                                .playSound(
                                        null,
                                        player.position().x,
                                        player.position().y + 0.5,
                                        player.position().z,
                                        soundevent,
                                        SoundSource.BLOCKS,
                                        1.0F,
                                        1.0F);
                    }
                    gui.getModularUIContainer().setCarried(currentStack);
                    return currentStack.getCount();
                }
            }

            return -1;
        }
    }

    private static class AmountSetWidget extends Widget {
        private static final Integer COLOR_DEFAULT = 0xFFFFFFFF;
        private static final Integer COLOR_ERROR = 0xFFDF0000;

        private int index = -1;

        @Getter
        private final TextFieldWidget amountText;

        private final ECOFluidConfigWidget parentWidget;

        AmountSetWidget(int x, int y, ECOFluidConfigWidget widget) {
            super(x, y, 80, 30);
            this.parentWidget = widget;
            this.amountText =
                    (TextFieldWidget) new TextFieldWidget(x + 8, y + 12, 60, 13, this::getAmountStr, this::setNewAmount)
                            .setValidator(this::amountTextValidator)
                            .setMaxStringLength(24)
                            .appendHoverTooltips(Component.translatable("gtocore.gui.widget.amount_set.hover_tooltip"));
        }

        @OnlyIn(Dist.CLIENT)
        public void setSlotIndexClient(int slotIndex) {
            this.index = slotIndex;
            writeClientAction(0, buf -> buf.writeVarInt(this.index));
        }

        public void setSlotIndex(int slotIndex) {
            this.index = slotIndex;
        }

        public void moveToSlot(int slotIndex) {
            if (this.parentWidget.fixedAmountPopup) {
                int popupX = this.parentWidget.amountPopupX;
                int popupY = this.parentWidget.amountPopupY;
                setSelfPosition(popupX, popupY);
                this.amountText.setSelfPosition(popupX + 8, popupY + 12);
                return;
            }
            int slotColumn = slotIndex < 0 ? 0 : slotIndex % CONFIG_COLUMNS;
            int slotX = slotColumn * SLOT_SIZE;
            int popupX = Math.max(0, Math.min(slotX + (SLOT_SIZE - AMOUNT_POPUP_W) / 2, WIDGET_W - AMOUNT_POPUP_W));
            setSelfPosition(popupX, AMOUNT_POPUP_Y);
            this.amountText.setSelfPosition(popupX + 8, AMOUNT_POPUP_Y + 12);
        }

        private String getAmountStr() {
            if (this.index < 0) {
                return "0";
            }
            IConfigurableSlot slot = this.parentWidget.getConfig(this.index);
            if (slot.getConfig() != null) {
                if (this.amountText.getCurrentString().isEmpty()
                        || this.amountText.getCurrentString().equals("0")) {
                    return String.valueOf(slot.getConfig().amount());
                }
                return this.amountText.getCurrentString();
            }
            return "0";
        }

        private void setNewAmount(String amount) {
            try {
                if (this.index < 0) {
                    return;
                }

                long newAmount = 0;
                if (!amount.isEmpty()) {
                    newAmount = AdvMathExpParser.parse(amount).longValue();
                }

                IConfigurableSlot slot = this.parentWidget.getConfig(this.index);
                if (newAmount > 0 && slot.getConfig() != null) {
                    slot.setConfig(new GenericStack(slot.getConfig().what(), newAmount));
                }
            } catch (IllegalArgumentException | ArithmeticException ignore) {
            }
        }

        private String amountTextValidator(String text) {
            try {
                long value = AdvMathExpParser.parse(text).longValue();
                if (value < 0) {
                    throw new IllegalArgumentException("Amount cannot be negative");
                }
                this.amountText.setTextColor(COLOR_DEFAULT);
            } catch (IllegalArgumentException | ArithmeticException e) {
                this.amountText.setTextColor(COLOR_ERROR);
            }
            return text;
        }

        @Override
        public void handleClientAction(int id, FriendlyByteBuf buffer) {
            super.handleClientAction(id, buffer);
            if (id == 0) {
                this.amountText.setCurrentString("");
                this.index = buffer.readVarInt();
            }
        }

        @OnlyIn(Dist.CLIENT)
        @Override
        public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            Position position = getPosition();
            GuiTextures.BACKGROUND.draw(graphics, mouseX, mouseY, position.x, position.y, 80, 30);
            drawStringSized(
                    graphics,
                    I18n.get("ldlib.gui.editor.configurator.amount"),
                    position.x + 25,
                    position.y + 3,
                    4210752,
                    false,
                    1.0F,
                    false);
        }
    }
}
