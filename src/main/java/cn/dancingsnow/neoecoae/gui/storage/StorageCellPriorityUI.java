package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.api.storage.ECOStorageCellPriorities;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCellPriorities.Target;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEmitter;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEventBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** A reusable per-cell priority window, including menu-scoped RPC and installation validation. */
public final class StorageCellPriorityUI {
    private final Player player;
    private final ECOStorageCellPriorities priorities;
    private final UIElement window;
    private final RPCEmitter select;
    private final RPCEmitter set;
    private final RPCEmitter change;
    private Target target;

    public StorageCellPriorityUI(Player player, ECOStorageCellPriorities priorities) {
        this.player = player;
        this.priorities = priorities;
        window = StoragePriorityUI.createFloatingPanel(new StoragePriorityUI.Config(
            () -> player.level().isClientSide ? priorities.getPriority(player, target) : priorities.getSelectedPriority(player),
            value -> edit(value, false), delta -> edit(delta, true),
            () -> Component.translatable("gui.neoecoae.storage.cell.priority.title")));
        // These handlers belong to this menu, so stale packets cannot configure a different open menu.
        select = window.addRPCEvent(RPCEventBuilder.simple(BlockPos.class, UUID.class, (position, installation) -> {
            if (canEdit()) priorities.select(player, new Target(position, installation));
        }));
        set = window.addRPCEvent(RPCEventBuilder.simple(BlockPos.class, UUID.class, Integer.class, Boolean.class,
            (position, installation, value) -> {
                if (!canEdit()) return false;
                priorities.setPriority(player, new Target(position, installation), value);
                return true;
            }));
        change = window.addRPCEvent(RPCEventBuilder.simple(BlockPos.class, UUID.class, Integer.class, Boolean.class,
            (position, installation, delta) -> {
                if (!canEdit()) return false;
                priorities.changePriority(player, new Target(position, installation), delta);
                return true;
            }));
    }

    /** Add this floating window directly to the host root on both client and server. */
    public UIElement window() {
        return window;
    }

    /** Pass this method as {@link StorageHostUI.CellActions#openPriority()}. */
    public void openPriority(BlockPos position, UUID installation) {
        target = new Target(position, installation);
        window.setDisplay(true);
        if (player.level().isClientSide) select.send(position, installation);
        else if (canEdit()) priorities.select(player, target);
    }

    private void edit(int value, boolean relative) {
        if (player.level().isClientSide) {
            if (target != null) (relative ? change : set).send(target.position(), target.installation(), value);
        } else if (canEdit()) {
            // Server button clicks use the server's selection; the client never supplies a drive object.
            if (relative) priorities.changePriority(player, priorities.getSelectedTarget(player), value);
            else priorities.setPriority(player, priorities.getSelectedTarget(player), value);
        }
    }

    private boolean canEdit() {
        var ui = window.getModularUI();
        return !player.level().isClientSide && ui != null && ui.getMenu() == player.containerMenu
            && player.containerMenu.stillValid(player);
    }
}
