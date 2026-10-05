package org.alexdev.unlimitednametags;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.potion.PotionEffectType;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

/** Constructor-free fixtures avoid starting Bukkit schedulers or plugin persistence. */
public final class TestInstances {
    private TestInstances() {}
    public static <T> T allocate(Class<T> type) throws Exception {
        return mock(type, CALLS_REAL_METHODS);
    }
    public static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    public static MockedStatic<RegistryAccess> potionRegistry() {
        // A lazy proxy avoids initializing Bukkit Registry before the provider is installed.
        MockedStatic<RegistryAccess> provider = mockStatic(RegistryAccess.class);
        RegistryAccess access = mock(RegistryAccess.class, invocation -> {
            if (!invocation.getMethod().getName().equals("getRegistry")) return null;
            return Proxy.newProxyInstance(org.bukkit.Registry.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.Registry.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getOrThrow") || method.getName().equals("get")) {
                            return mock(PotionEffectType.class);
                        }
                        if (method.getName().equals("toString")) return "test potion registry";
                        throw new AssertionError("Unexpected registry call: " + method.getName());
                    });
        });
        provider.when(RegistryAccess::registryAccess).thenReturn(access);
        return provider;
    }
}
