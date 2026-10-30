package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

class DrivePersistenceNotificationTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test void hotTransfersQueueOneTaskAndResendOnlyChangedState() throws ReflectiveOperationException {
        var drive = StorageDriveTestFixtures.drive();
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(call -> tasks.add(call.getArgument(0))).when(server).executeIfPossible(any());
        var field = BlockEntity.class.getDeclaredField("level");
        field.setAccessible(true);
        field.set(drive, level);
        doNothing().when(drive).markForUpdate();
        doNothing().when(drive).setChanged();

        drive.notifyPersistence();
        drive.notifyPersistence();
        drive.notifyPersistence();
        assertEquals(1, tasks.size(), "pending notifications are coalesced per drive");
        tasks.remove(0).run();
        verify(drive, times(1)).setChanged();
        verify(drive, times(1)).markForUpdate();

        drive.notifyPersistence();
        assertEquals(1, tasks.size(), "a new notification is accepted after the task ran");
        tasks.remove(0).run();
        verify(drive, times(2)).setChanged();
        verify(drive, times(1)).markForUpdate();
    }
}
