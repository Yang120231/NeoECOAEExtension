package cn.dancingsnow.neoecoae.compat.gtmthings;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.energy.ECOCraftingEnergyAdapter;
import cn.dancingsnow.neoecoae.api.me.energy.ECOCraftingEnergyRequest;
import cn.dancingsnow.neoecoae.api.me.energy.ECOCraftingEnergyResult;
import cn.dancingsnow.neoecoae.api.me.energy.ECOCraftingEnergySnapshot;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingEnergyMode;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCraftingEnergyStatus;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GTMWirelessEnergyAdapter implements ECOCraftingEnergyAdapter {
    public static final GTMWirelessEnergyAdapter INSTANCE = new GTMWirelessEnergyAdapter();

    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final String SOURCE = "gtmthings_wireless";
    private static final String CONTAINER_CLASS = "com.hepdd.gtmthings.api.misc.WirelessEnergyContainer";

    private static volatile boolean accessResolved;

    @Nullable private static Access access;

    private GTMWirelessEnergyAdapter() {}

    public static boolean isAvailable() {
        return ModList.get().isLoaded("gtmthings")
                && (ModList.get().isLoaded("gtceu")
                        || ModList.get().isLoaded("gtm")
                        || ModList.get().isLoaded("gregtech"));
    }

    @Override
    public ECOCraftingEnergySnapshot snapshot(ECOCraftingEnergyRequest request) {
        UUID owner = request.owner();
        if (owner == null || !isAvailable()) {
            return ECOCraftingEnergySnapshot.unavailable(
                    ECOCraftingEnergyMode.EXTERNAL, request.requiredEnergy(), SOURCE);
        }
        try {
            Access api = resolveAccess();
            if (api == null) {
                return ECOCraftingEnergySnapshot.unavailable(
                        ECOCraftingEnergyMode.EXTERNAL, request.requiredEnergy(), SOURCE);
            }
            Object container = api.getOrCreate().invoke(null, owner);
            long rate = Math.max(0L, (long) api.getRate().invoke(container));
            BigInteger storage = (BigInteger) api.getStorage().invoke(container);
            long available = saturate(storage);
            boolean enough = rate >= request.requiredEnergy()
                    && storage.compareTo(BigInteger.valueOf(request.requiredEnergy())) >= 0;
            return new ECOCraftingEnergySnapshot(
                    ECOCraftingEnergyMode.EXTERNAL,
                    enough ? ECOCraftingEnergyStatus.READY : ECOCraftingEnergyStatus.INSUFFICIENT,
                    enough,
                    request.requiredEnergy(),
                    available,
                    rate,
                    SOURCE);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError e) {
            LOGGER.debug("Unable to query GTMThings wireless energy for {}", owner, e);
            return ECOCraftingEnergySnapshot.unavailable(
                    ECOCraftingEnergyMode.EXTERNAL, request.requiredEnergy(), SOURCE);
        }
    }

    @Override
    public ECOCraftingEnergyResult drain(ECOCraftingEnergyRequest request) {
        ECOCraftingEnergySnapshot snapshot = snapshot(request);
        if (!snapshot.available() || request.owner() == null || request.requiredEnergy() <= 0L) {
            return snapshot.asResult(false);
        }
        try {
            Access api = resolveAccess();
            if (api == null) {
                return snapshot.asResult(false);
            }
            Object container = api.getOrCreate().invoke(null, request.owner());
            long extracted = (long) api.removeEnergy().invoke(container, request.requiredEnergy(), null);
            if (extracted == request.requiredEnergy()) {
                return snapshot.asResult(true);
            }
            if (extracted > 0L) {
                long restored = (long) api.addEnergy().invoke(container, extracted, null);
                if (restored != extracted) {
                    LOGGER.error(
                            "Failed to restore partial GTMThings wireless energy extraction: owner={} extracted={} restored={}",
                            request.owner(),
                            extracted,
                            restored);
                }
            }
            return snapshot.asResult(false);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError e) {
            LOGGER.debug("Unable to drain GTMThings wireless energy for {}", request.owner(), e);
            return snapshot.asResult(false);
        }
    }

    @Nullable private static Access resolveAccess() {
        if (accessResolved) {
            return access;
        }
        synchronized (GTMWirelessEnergyAdapter.class) {
            if (accessResolved) {
                return access;
            }
            try {
                Class<?> containerClass = Class.forName(CONTAINER_CLASS);
                access = new Access(
                        containerClass.getMethod("getOrCreateContainer", UUID.class),
                        containerClass.getMethod("getRate"),
                        containerClass.getMethod("getStorage"),
                        findEnergyMethod(containerClass, "removeEnergy"),
                        findEnergyMethod(containerClass, "addEnergy"));
            } catch (ReflectiveOperationException | LinkageError e) {
                LOGGER.debug("Unable to resolve GTMThings wireless energy API", e);
            } finally {
                accessResolved = true;
            }
            return access;
        }
    }

    private static Method findEnergyMethod(Class<?> containerClass, String name) throws NoSuchMethodException {
        for (Method method : containerClass.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == long.class) {
                return method;
            }
        }
        throw new NoSuchMethodException(containerClass.getName() + "#" + name);
    }

    private static long saturate(BigInteger value) {
        if (value == null || value.signum() <= 0) {
            return 0L;
        }
        BigInteger max = BigInteger.valueOf(Long.MAX_VALUE);
        return value.compareTo(max) >= 0 ? Long.MAX_VALUE : value.longValue();
    }

    private record Access(
            Method getOrCreate, Method getRate, Method getStorage, Method removeEnergy, Method addEnergy) {}
}
