package com.robohelper.module;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Реестр id → фабрика; каждый робот получает собственный экземпляр поведения. */
public final class ModuleRegistry {
    private static final Map<Identifier, Supplier<? extends RobotModule>> FACTORIES = new HashMap<>();

    private ModuleRegistry() {
    }

    public static void register(Identifier id, Supplier<? extends RobotModule> factory) {
        if (FACTORIES.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("Duplicate robot module: " + id);
        }
    }

    @Nullable
    public static RobotModule create(Identifier id) {
        Supplier<? extends RobotModule> factory = FACTORIES.get(id);
        if (factory == null) {
            return null;
        }
        RobotModule module = factory.get();
        if (!id.equals(module.getId()) || module.getPriority() < 2 || module.getPriority() > 4) {
            throw new IllegalStateException("Invalid robot module factory: " + id);
        }
        return module;
    }

    public static void initialize() {
    }
}
