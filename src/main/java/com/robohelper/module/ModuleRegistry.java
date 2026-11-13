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

    /** Регистрирует фабрику отдельного поведения; дубликат id считается ошибкой. */
    public static void register(Identifier id, Supplier<? extends RobotModule> factory) {
        if (FACTORIES.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("Duplicate robot module: " + id);
        }
    }

    @Nullable
    /** Создаёт поведение по id либо возвращает null для неизвестной профессии. */
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

    /** Регистрирует встроенные профессии при запуске мода. */
    public static void initialize() {
        register(LumberjackModule.ID, LumberjackModule::new);
        register(FarmerModule.ID, FarmerModule::new);
    }
}
