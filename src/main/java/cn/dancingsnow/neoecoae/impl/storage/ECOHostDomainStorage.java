package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class ECOHostDomainStorage implements MEStorage {
    private final ECOStorageSystemBlockEntity controller;

    public ECOHostDomainStorage(ECOStorageSystemBlockEntity controller) {
        this.controller = controller;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        UUID domainId = controller.getHostDomainId();
        ServerLevel level = controller.getStorageServerLevel();
        if (domainId == null || level == null || !controller.canDomainStore(what)) {
            return false;
        }
        return ECOStorageSavedData.get(level)
                        .getDomainOrEmpty(domainId)
                        .get(what)
                        .signum()
                > 0;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !controller.canUseHostDomainStorage() || !controller.canDomainStore(what)) {
            return 0L;
        }
        UUID domainId = controller.getHostDomainId();
        ServerLevel level = controller.getStorageServerLevel();
        if (domainId == null || level == null) {
            return 0L;
        }
        if (mode == Actionable.MODULATE) {
            ECOStorageSavedData data = ECOStorageSavedData.get(level);
            data.getOrCreateDomain(domainId).add(what, BigInteger.valueOf(amount));
            data.setDirty();
            controller.onHostDomainContentChanged();
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !controller.canUseHostDomainStorage()) {
            return 0L;
        }
        UUID domainId = controller.getHostDomainId();
        ServerLevel level = controller.getStorageServerLevel();
        if (domainId == null || level == null) {
            return 0L;
        }
        ECOStorageSavedData data = ECOStorageSavedData.get(level);
        BigInteger stored = data.getDomainOrEmpty(domainId).get(what);
        if (stored.signum() <= 0) {
            return 0L;
        }
        long extracted =
                stored.compareTo(BigInteger.valueOf(amount)) > 0 ? amount : ECOStorageSavedData.saturate(stored);
        if (mode == Actionable.MODULATE && extracted > 0) {
            data.getOrCreateDomain(domainId).subtract(what, BigInteger.valueOf(extracted));
            data.setDirty();
            controller.onHostDomainContentChanged();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        UUID domainId = controller.getHostDomainId();
        ServerLevel level = controller.getStorageServerLevel();
        if (domainId == null || level == null || !controller.canUseHostDomainStorage()) {
            return;
        }
        for (Map.Entry<AEKey, BigInteger> entry : ECOStorageSavedData.get(level)
                .getDomainOrEmpty(domainId)
                .amounts()
                .entrySet()) {
            long amount = ECOStorageSavedData.saturate(entry.getValue());
            if (amount > 0) {
                out.add(entry.getKey(), amount);
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.literal("ECO Infinite Storage Domain");
    }
}
