package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyBigMap;
import appeng.api.storage.MEStorage.AvailableStacksCache;
import java.nio.file.Path;
import java.util.UUID;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

class GtoBigCellStorageApiTest {
    @Test void installedReleaseExposesTheBoundPublicApi() throws Exception {
        String path = System.getenv("NEOECOAE_GTO_CORE_JAR");
        Assumptions.assumeTrue(path != null && !path.isBlank(), "Installed GTOCore artifact not configured");
        String name = GtoBigCellStorage.findApiClass(Path.of(path));
        try (JarFile jar = new JarFile(path);
             var input = jar.getInputStream(jar.getJarEntry(name.replace('.', '/') + ".class"))) {
            ClassNode api = new ClassNode();
            new ClassReader(input).accept(api, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            for (String signature : new String[]{"()Lappeng/api/stacks/AEKeyBigMap;",
                "(Lappeng/api/stacks/AEKeyBigMap;)V", "(Lappeng/api/stacks/AEKey;J)V", "(D)V"}) {
                assertEquals(1, api.methods.stream().filter(method -> method.desc.equals(signature)
                    && (method.access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0).count(), signature);
            }
            assertTrue(api.methods.stream().anyMatch(method -> method.name.equals("<init>") && method.desc.equals("()V")));
        }
    }

    @Test void renamedPublicMethodsRetainTheRealMapAndReceiveMutations() {
        var map = new AEKeyBigMap<AEKey>();
        var storage = new GtoBigCellStorage(GtoBigCellStorage.bind(PublishedApiFixture.class), map);
        var nativeObject = (PublishedApiFixture) storage.identity();
        assertSame(map, nativeObject.contents());
        var key = new ECOGtoStorageIdentityTest.Key();
        assertEquals(35, map.addTo(key, 35));
        storage.changed(key, 35);
        assertEquals(35, nativeObject.bytes);
        assertEquals(35, storage.listing().get(key));
        assertEquals(12, map.extractLong(key, 12));
        storage.changed(key, -12);
        assertEquals(23, nativeObject.bytes);
        assertEquals(23, storage.listing().get(key), "A second read in the same tick must see the extraction");
        assertEquals(23, nativeObject.contents().getLongAmount(key));
        assertSame(nativeObject.cache, storage.cache());
        map.clear();
        storage.refresh();
        assertEquals(0, nativeObject.bytes);
        assertTrue(storage.listing().isEmpty());
    }

    /** Boundary fixture for symbol renaming; quantities and cache are the actual GTO AE2 classes. */
    public static final class PublishedApiFixture {
        private AEKeyBigMap<AEKey> map;
        private double bytes;
        public final AvailableStacksCache cache = new AvailableStacksCache(out -> out.addAll(map));
        public PublishedApiFixture() { cache.setTickUpdate(false); }
        public static PublishedApiFixture lookup(UUID id) { return new PublishedApiFixture(); }
        public AEKeyBigMap<AEKey> contents() { return map; }
        public void contents(AEKeyBigMap<AEKey> map) { this.map = map; }
        public void changeBytes(AEKey key, long delta) { bytes += delta; }
        public void byteTotal(double value) { bytes = value; }
    }
}
