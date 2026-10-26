package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOGtoStorageListingTest {
    @Test void nestedNetworksKeepTheirContributionsIndependent() {
        var key = mock(AEKey.class);
        var storage = mock(MEStorage.class);
        var output = new KeyCounter();
        ECOGtoStorageListing.append(storage, output, outer -> {
            KeyCounter parent = (KeyCounter) outer[1];
            parent.add(key, 3);
            ECOGtoStorageListing.append(storage, parent, inner -> {
                KeyCounter child = (KeyCounter) inner[1];
                assertNotSame(parent, child);
                assertTrue(child.isEmpty());
                child.add(key, 9);
                return null;
            });
            parent.add(key, 4);
            return null;
        });
        assertEquals(16, output.get(key));
    }

    @Test void countersAreReusedWithoutRetainingFailedContributions() {
        var key = mock(AEKey.class);
        var storage = mock(MEStorage.class);
        var counter = new AtomicReference<KeyCounter>();
        assertThrows(IllegalStateException.class, () -> ECOGtoStorageListing.append(storage, new KeyCounter(), args -> {
            KeyCounter target = (KeyCounter) args[1];
            counter.set(target);
            target.add(key, 999);
            throw new IllegalStateException("source failed");
        }));
        var output = new KeyCounter();
        for (int i = 0; i < 100; i++) {
            ECOGtoStorageListing.append(storage, output, args -> {
                KeyCounter target = (KeyCounter) args[1];
                assertSame(counter.get(), target);
                assertTrue(target.isEmpty());
                target.add(key, 5);
                return null;
            });
        }
        assertEquals(500, output.get(key));
    }

    @Test void unboundedStorageDoesNotEraseOrdinaryStockInEitherMountOrder() {
        var key = mock(AEKey.class);
        var storage = mock(MEStorage.class);
        for (boolean reverse : new boolean[]{false, true}) {
            var output = new KeyCounter();
            ECOGtoStorageListing.append(storage, output, args -> {
                ((KeyCounter) args[1]).add(key, reverse ? 7 : Long.MAX_VALUE);
                return null;
            });
            ECOGtoStorageListing.append(storage, output, args -> {
                ((KeyCounter) args[1]).add(key, reverse ? Long.MAX_VALUE : 7);
                return null;
            });
            assertEquals(Long.MAX_VALUE, output.get(key));
        }
    }
}
