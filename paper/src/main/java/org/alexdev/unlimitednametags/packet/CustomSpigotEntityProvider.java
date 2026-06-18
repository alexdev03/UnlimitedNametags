package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import me.tofaa.entitylib.EntityIdProvider;
import org.bukkit.Bukkit;
import org.bukkit.UnsafeValues;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class CustomSpigotEntityProvider implements EntityIdProvider {

    /** The supplier for entity IDs, dynamically chosen by {@link #detectIdSupplier()}. */
    private final Supplier<Integer> entityIdSupplier;

    /**
     * Constructs a new {@code SpigotEntityIdProvider}.
     */
    public CustomSpigotEntityProvider() {
        this.entityIdSupplier = detectIdSupplier();
    }

    @Override
    public int provide(@NotNull UUID entityUUID, @NotNull EntityType entityType) {
        // UUID and EntityType are unused in Minecraft id generation.
        return entityIdSupplier.get();
    }

    /**
     * Detects and configures the appropriate {@link Supplier} for entity IDs based on server version
     * and platform capabilities.
     * Logic:
     * <ul>
     *   <li>Paper (1.16+): Use `{@link UnsafeValues#nextEntityId(org.bukkit.World)}`. (Paper API)</li>
     *   <li>1.14+: Access `AtomicInteger` field ("entityCount", "d", "c").</li>
     *   <li>1.8+: Access legacy `int` field "entityCount".</li>
     * </ul>
     *
     * @return A {@link Supplier} instance that provides the next entity ID.
     * @throws IllegalStateException if the entity counter field cannot be located or accessed.
     */
    private Supplier<Integer> detectIdSupplier() {
        final ServerVersion serverVersion = getServerVersion();

        if (isPaper() && serverVersion.isNewerThanOrEquals(ServerVersion.V_1_16)) {
            return () -> Bukkit.getUnsafe().nextEntityId(Bukkit.getWorlds().get(0));
        }

        final Class<?> entityClass = getEntityClass();
        if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_14)) {
            final Supplier<Integer> modernSupplier = resolveAtomicSupplier(entityClass);
            if (modernSupplier != null) {
                return modernSupplier;
            }
        }

        return resolveLegacySupplier(entityClass);
    }

    private Supplier<Integer> resolveAtomicSupplier(final Class<?> entityClass) {
        final Field entityAtomicField = getStaticFieldOfType(entityClass, AtomicInteger.class,
                "entityCount", "d", "c", "counter", "nextEntityId");
        if (entityAtomicField == null) {
            return null;
        }
        try {
            entityAtomicField.setAccessible(true);
            final Object fieldValue = entityAtomicField.get(null);
            if (!(fieldValue instanceof AtomicInteger)) {
                return null; // incompatible type, fall back to legacy strategy
            }
            final AtomicInteger counter = (AtomicInteger) fieldValue;
            return counter::incrementAndGet;
        } catch (final IllegalAccessException exception) {
            throw new IllegalStateException("Failed to access entity counter", exception);
        }
    }

    private Supplier<Integer> resolveLegacySupplier(final Class<?> entityClass) {
        final Field entityLegacyField = getStaticFieldOfType(entityClass, Integer.TYPE, "entityCount", "b");
        if (entityLegacyField == null) {
            throw new IllegalStateException("Could not find legacy entity counter field");
        }
        entityLegacyField.setAccessible(true);
        return () -> {
            try {
                final int entityId = entityLegacyField.getInt(null);
                entityLegacyField.setInt(null, entityId + 1);
                return entityId;
            } catch (final IllegalAccessException exception) {
                throw new IllegalStateException("Failed to modify entity counter", exception);
            }
        };
    }

    /**
     * Resolves server's internal `Entity` class, handling version-specific packages.
     * In Minecraft versions 1.17 and later, the `Entity` class resides in `net.minecraft.world.entity.Entity`.
     * Prior to 1.17, it is located in `net.minecraft.server.[version].Entity`.
     *
     * @return Resolved `Entity` class.
     * @throws IllegalStateException if the `Entity` class cannot be found.
     */
    private Class<?> getEntityClass() {
        final ServerVersion serverVersion = getServerVersion();
        final boolean isFlattened = serverVersion.isNewerThanOrEquals(ServerVersion.V_1_17);

        final String packagePath = isFlattened
                ? "net.minecraft.world.entity"
                : "net.minecraft.server." + getCraftBukkitVersion();

        try {
            return Class.forName(packagePath + ".Entity");
        } catch (final ClassNotFoundException exception) {
            throw new IllegalStateException("Could not find Entity class", exception);
        }
    }

    private static ServerVersion getServerVersion() {
        return PacketEvents.getAPI().getServerManager().getVersion();
    }

    private static String getCraftBukkitVersion() {
        final String packageName = Bukkit.getServer().getClass().getPackage().getName();
        final String[] packageParts = packageName.split("\\.");
        if (packageParts.length < 4) {
            throw new IllegalStateException("Could not determine CraftBukkit version from " + packageName);
        }
        return packageParts[3];
    }

    /**
     * Utility method to retrieve a declared field from a class by trying multiple names.
     * @param clazz The class to examine for the field.
     * @param possibleNames An array of potential field names to attempt retrieval with.
     * @return The {@link Field} if a matching field is found, otherwise {@code null}.
     */
    private static Field getField(final Class<?> clazz, final String... possibleNames) {
        for (final String name : possibleNames) {
            try {
                return clazz.getDeclaredField(name);
            } catch (final NoSuchFieldException ignored) {
                // Field name not found, proceed to the next
            }
        }
        return null;
    }

    private static Field getStaticFieldOfType(final Class<?> clazz, final Class<?> desiredType,
                                              final String... possibleNames) {
        for (final String name : possibleNames) {
            final Field field = getField(clazz, name);
            if (field != null && desiredType.isAssignableFrom(field.getType())
                    && Modifier.isStatic(field.getModifiers())) {
                return field;
            }
        }

        for (final Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && desiredType.isAssignableFrom(field.getType())) {
                return field;
            }
        }
        return null;
    }

    /**
     * Determines if the server environment is Paper by checking for Paper-specific classes.
     * @return true if Paper is detected.
     */
    private static boolean isPaper() {
        return Stream.of(
                "com.destroystokyo.paper.PaperConfig",
                "io.papermc.paper.configuration.Configuration"
        ).anyMatch(CustomSpigotEntityProvider::hasClass);
    }

    /**
     * Utility to check if a class exists on the classpath.
     * @param className Class name to check.
     * @return true if class exists.
     */
    private static boolean hasClass(final String className) {
        try {
            Class.forName(className);
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }
}
