package cn.dancingsnow.neoecoae.compat.gto;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GtoBlockUiInitializationTest {
    private static final String UI_CLASS =
        "cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType";
    private static final String PLAYER_UI_CLASS =
        "cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.PlayerUIMenuType";

    @ParameterizedTest(name = "register before joining a world, dedicated server = {0}")
    @ValueSource(booleans = {false, true})
    void commonSetupRegistersFactoryWithoutOpeningSingleplayer(boolean dedicatedServer) throws Exception {
        var id = new ResourceLocation("neoecoae", "block_ui");
        var playerId = new ResourceLocation("neoecoae", "player_ui");
        var previous = UIFactory.FACTORIES.remove(id);
        var previousPlayer = UIFactory.FACTORIES.remove(playerId);
        try {
            // Isolate static state to represent a freshly launched process, without a player,
            // level or a previous integrated-server UI open initializing the factory for us.
            var loader = new ClassLoader(getClass().getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (dedicatedServer && name.startsWith("net.minecraft.client.")) {
                        throw new ClassNotFoundException("Client class on dedicated server: " + name);
                    }
                    if (!name.equals(UI_CLASS) && !name.startsWith(UI_CLASS + "$")
                        && !name.equals(PLAYER_UI_CLASS) && !name.startsWith(PLAYER_UI_CLASS + "$")) {
                        return super.loadClass(name, resolve);
                    }
                    synchronized (getClassLoadingLock(name)) {
                        var type = findLoadedClass(name);
                        if (type == null) {
                            try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                                if (input == null) throw new ClassNotFoundException(name);
                                byte[] bytes = input.readAllBytes();
                                type = defineClass(name, bytes, 0, bytes.length);
                            } catch (IOException failure) {
                                throw new ClassNotFoundException(name, failure);
                            }
                        }
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
            };
            var type = assertDoesNotThrow(() -> Class.forName(UI_CLASS, true, loader));
            var queuedWork = new AtomicReference<Runnable>();
            var event = mock(FMLCommonSetupEvent.class);
            when(event.enqueueWork(any(Runnable.class))).thenAnswer(invocation -> {
                queuedWork.set(invocation.getArgument(0));
                return CompletableFuture.completedFuture(null);
            });

            var setup = type.getMethod("onCommonSetup", FMLCommonSetupEvent.class);
            assertDoesNotThrow(() -> setup.invoke(null, event));
            assertNull(UIFactory.FACTORIES.get(id), "Registration must wait for serial setup work");
            assertNull(UIFactory.FACTORIES.get(playerId));
            assertNotNull(queuedWork.get());
            assertDoesNotThrow(() -> queuedWork.get().run());

            var factory = UIFactory.FACTORIES.get(id);
            assertNotNull(factory, "The first multiplayer UI packet must find the factory");
            assertSame(loader, factory.getClass().getClassLoader());
            assertEquals(id, factory.uiFactoryId);
            var playerFactory = UIFactory.FACTORIES.get(playerId);
            assertNotNull(playerFactory, "Terminal status packets also need their factory before joining a world");
            assertSame(loader, playerFactory.getClass().getClassLoader());
            assertDoesNotThrow(() -> queuedWork.get().run());
            assertSame(factory, UIFactory.FACTORIES.get(id));
            assertSame(playerFactory, UIFactory.FACTORIES.get(playerId));
        } finally {
            if (previous == null) UIFactory.FACTORIES.remove(id);
            else UIFactory.FACTORIES.put(id, previous);
            if (previousPlayer == null) UIFactory.FACTORIES.remove(playerId);
            else UIFactory.FACTORIES.put(playerId, previousPlayer);
        }
    }
}
