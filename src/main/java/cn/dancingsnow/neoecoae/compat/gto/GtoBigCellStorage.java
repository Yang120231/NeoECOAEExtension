package cn.dancingsnow.neoecoae.compat.gto;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyBigMap;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage.AvailableStacksCache;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarFile;
import net.minecraftforge.fml.ModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Uses GTOLib's BigCellDataStorage API, including protected releases whose public symbols are renamed.
 * API discovery happens once; IO uses the native AEKeyBigMap directly and cached method handles.
 *
 * <p>Each ECO SavedData owns one native instance. Its existing atomic snapshot keeps quantities, migration
 * receipts and ownership together; the same inventory is never registered with a second persistence owner.
 * The public API is documented by OpenGTO's com.gtolib.api.ae2.storage.BigCellDataStorage.
 */
public final class GtoBigCellStorage {
    public static final String API_CLASS = "com.gtolib.api.ae2.storage.BigCellDataStorage";
    private static final String MAP_DESCRIPTOR = "Lappeng/api/stacks/AEKeyBigMap;";
    private static final String CACHE_DESCRIPTOR = "Lappeng/api/storage/MEStorage$AvailableStacksCache;";
    private final Object delegate;
    private final Binding binding;
    private final AEKeyBigMap<AEKey> amounts;
    private final AvailableStacksCache cache;
    private boolean listingDirty = true;

    GtoBigCellStorage(Binding binding, AEKeyBigMap<AEKey> amounts) {
        this.binding = binding;
        this.amounts = amounts;
        try {
            delegate = (Object) binding.constructor.invokeExact();
            binding.setMap.invokeExact(delegate, amounts);
            if ((AEKeyBigMap<AEKey>) binding.getMap.invokeExact(delegate) != amounts) {
                throw new IllegalStateException("GTOLib did not retain its storage map");
            }
            cache = (AvailableStacksCache) binding.cache.get(delegate);
        } catch (Throwable failure) {
            throw linkageFailure(failure);
        }
        refresh();
    }

    public static GtoBigCellStorage create(AEKeyBigMap<AEKey> amounts) {
        return new GtoBigCellStorage(RuntimeBinding.API, amounts);
    }

    public Object identity() { return delegate; }
    public AEKeyBigMap<AEKey> amounts() { return amounts; }
    public AvailableStacksCache cache() { return cache; }
    public KeyCounter listing() {
        if (listingDirty) {
            // ECO permits multiple mutations and reads within one tick; refresh explicitly at this boundary.
            cache.updateCache();
            listingDirty = false;
        }
        return cache.getAvailableStacksCache();
    }

    public void changed(AEKey key, long delta) {
        if (delta != 0) {
            try { binding.addBytes.invokeExact(delegate, key, delta); }
            catch (Throwable failure) { throw linkageFailure(failure); }
        }
        cache.markAsDirty(key);
        listingDirty = true;
    }

    /** Bulk replacement/legacy decoding is a cold path; ordinary IO adjusts bytes incrementally. */
    public void refresh() {
        double[] bytes = {0};
        amounts.forEach((key, amount) -> bytes[0] += amount.doubleValue() / key.getAmountPerByte());
        try { binding.setBytes.invokeExact(delegate, bytes[0]); }
        catch (Throwable failure) { throw linkageFailure(failure); }
        cache.invalidateCache();
        listingDirty = true;
    }

    private static IllegalStateException linkageFailure(Throwable failure) {
        return new IllegalStateException("Cannot use the installed GTOLib BigCellDataStorage API", failure);
    }

    private static final class RuntimeBinding {
        static final Binding API = load();

        private static Binding load() {
            ClassLoader loader = GtoBigCellStorage.class.getClassLoader();
            try {
                return bind(Class.forName(API_CLASS, false, loader));
            } catch (ClassNotFoundException renamed) {
                var core = ModList.get().getModFileById("gtocore");
                if (core == null) throw new IllegalStateException("GTO storage requires GTOLib from GTOCore");
                try {
                    String name = findApiClass(core.getFile().getFilePath());
                    return bind(Class.forName(name, false, loader));
                } catch (IOException | ClassNotFoundException failure) {
                    throw linkageFailure(failure);
                }
            }
        }
    }

    /** Resolves public API shape only; no method bodies or private implementation are inspected. */
    static String findApiClass(Path artifact) throws IOException {
        List<String> candidates = new ArrayList<>();
        if (Files.isDirectory(artifact)) {
            Path root = artifact.resolve("com/gtolib");
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                    acceptApiClass(Files.readAllBytes(file), candidates);
                }
            }
        } else {
            try (JarFile jar = new JarFile(artifact.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.getName().startsWith("com/gtolib/") || !entry.getName().endsWith(".class")) continue;
                    try (var input = jar.getInputStream(entry)) { acceptApiClass(input.readAllBytes(), candidates); }
                }
            }
        }
        if (candidates.size() != 1) {
            throw new IllegalStateException("Expected one GTOLib BigCellDataStorage API, found " + candidates);
        }
        return candidates.get(0).replace('/', '.');
    }

    private static void acceptApiClass(byte[] bytes, List<String> candidates) {
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if ((type.access & Opcodes.ACC_ABSTRACT) != 0) return;
        boolean map = type.fields.stream().anyMatch(field -> MAP_DESCRIPTOR.equals(field.desc)
            && (field.access & Opcodes.ACC_STATIC) == 0);
        boolean cache = type.fields.stream().anyMatch(field -> CACHE_DESCRIPTOR.equals(field.desc)
            && (field.access & Opcodes.ACC_PUBLIC) != 0 && (field.access & Opcodes.ACC_STATIC) == 0);
        boolean lookup = type.methods.stream().anyMatch(method -> (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
            == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
            && method.desc.equals("(Ljava/util/UUID;)L" + type.name + ";"));
        if (map && cache && lookup) candidates.add(type.name);
    }

    static Binding bind(Class<?> type) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Field cache = null;
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == AvailableStacksCache.class && Modifier.isPublic(field.getModifiers())
                    && !Modifier.isStatic(field.getModifiers())) {
                    if (cache != null) throw new IllegalStateException("Ambiguous GTOLib cache field");
                    cache = field;
                }
            }
            if (cache == null) throw new IllegalStateException("Missing GTOLib storage cache");
            // Resolve the UUID accessor too, so a lookalike inventory cannot satisfy this contract.
            method(type, type, true, UUID.class);
            return new Binding(
                lookup.unreflectConstructor(type.getConstructor()).asType(MethodType.methodType(Object.class)),
                lookup.unreflect(method(type, AEKeyBigMap.class, false)).asType(
                    MethodType.methodType(AEKeyBigMap.class, Object.class)),
                lookup.unreflect(method(type, void.class, false, AEKeyBigMap.class)).asType(
                    MethodType.methodType(void.class, Object.class, AEKeyBigMap.class)),
                lookup.unreflect(method(type, void.class, false, AEKey.class, long.class)).asType(
                    MethodType.methodType(void.class, Object.class, AEKey.class, long.class)),
                lookup.unreflect(method(type, void.class, false, double.class)).asType(
                    MethodType.methodType(void.class, Object.class, double.class)),
                lookup.unreflectVarHandle(cache));
        } catch (ReflectiveOperationException failure) {
            throw linkageFailure(failure);
        }
    }

    private static Method method(Class<?> owner, Class<?> returns, boolean isStatic, Class<?>... parameters) {
        Method found = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) == isStatic
                && method.getReturnType() == returns && java.util.Arrays.equals(parameters, method.getParameterTypes())) {
                if (found != null) throw new IllegalStateException("Ambiguous GTOLib storage API: " + returns);
                found = method;
            }
        }
        if (found == null) throw new IllegalStateException("Missing GTOLib storage API: " + returns);
        return found;
    }

    record Binding(MethodHandle constructor, MethodHandle getMap, MethodHandle setMap,
                   MethodHandle addBytes, MethodHandle setBytes, VarHandle cache) {}
}
