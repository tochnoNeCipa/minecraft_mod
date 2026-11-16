package com.robohelper.release;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.robohelper.debug.RobotTimings;
import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.entity.RobotMode;
import com.robohelper.module.ModuleProgress;
import com.robohelper.registry.ModEntities;
import com.robohelper.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/** Проверки на настоящем сервере; код включается только с -PreleaseChecks. */
public final class ReleaseServer implements ModInitializer {
    private static final BlockPos HOME = new BlockPos(0, 65, 0);
    private final Path output = Path.of(System.getProperty("robohelper.release.output", "../release-results")).toAbsolutePath();
    private final String phase = System.getProperty("robohelper.release.phase", "prepare");
    private final Set<String> reports = new HashSet<>();
    private final List<RobotEntity> robots = new ArrayList<>();
    private final List<Map<String, Object>> measurements = new ArrayList<>();
    private final List<Double> samples = new ArrayList<>();
    private final List<Double> serverSamples = new ArrayList<>();
    private int stage;
    private int elapsed;
    private int scenario;
    private int warmup;
    private long tickStarted;
    private RobotEntity chunkRobot;
    private UUID chunkUuid;
    private NbtCompound chunkData;
    private JsonObject checkpoint;

    @Override
    public void onInitialize() {
        if (!System.getProperty("fabric.dli.env", "server").equals("server")) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                literal("robohelper_release").then(literal("report").then(argument("event", StringArgumentType.word())
                        .executes(context -> {
                            String name = context.getSource().getPlayerOrThrow().getGameProfile().getName();
                            String event = StringArgumentType.getString(context, "event");
                            reports.add(name + ":" + event);
                            System.out.println("RELEASE client " + name + " " + event);
                            return 1;
                        })))));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                Files.createDirectories(output);
                ServerWorld world = server.getOverworld();
                world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
                world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, server);
                world.setTimeOfDay(1000);
                world.setSpawnPos(HOME, 0);
                if (phase.equals("prepare")) {
                    platform(world, HOME, 12);
                } else {
                    checkpoint = JsonParser.parseString(Files.readString(output.resolve("checkpoint.json"))).getAsJsonObject();
                    stage = 10;
                }
                System.out.println("RELEASE ready " + phase);
            } catch (Exception error) { fail(server, error); }
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            RobotTimings.drain();
            tickStarted = System.nanoTime();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                tick(server, RobotTimings.drain(), (System.nanoTime() - tickStarted) / 1_000_000.0);
            } catch (Exception error) { fail(server, error); }
        });
    }

    private void tick(MinecraftServer server, RobotTimings.Sample timings, double serverMillis) throws Exception {
        ServerWorld world = server.getOverworld();
        elapsed++;
        if (stage == 0) {
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer("ReleaseOwner");
            ServerPlayerEntity other = server.getPlayerManager().getPlayer("ReleaseOther");
            if (owner == null || other == null) {
                require(elapsed < 3600, "Two clients did not join within 180 seconds");
                return;
            }
            robots.add(fixture(world, owner, HOME, true));
            robots.add(fixture(world, other, HOME.east(3), false));
            world.setBlockState(HOME.north(), Blocks.CHEST.getDefaultState());
            owner.teleport(world, 0.5, 65, 4.5, 180, 15);
            other.teleport(world, 3.5, 65, 4.5, 180, 15);
            stage = 1;
            elapsed = 0;
            System.out.println("RELEASE two real clients online; fixtures ready");
        } else if (stage == 1) {
            if (!reports.contains("ReleaseOwner:done") || !reports.contains("ReleaseOther:done")) {
                require(elapsed < 3600, "Client GUI acceptance did not finish");
                return;
            }
            for (String name : new String[]{"ReleaseOwner", "ReleaseOther"}) {
                for (String event : new String[]{"foreign-denied", "gui-open", "fuel-rejected", "buttons", "done"}) {
                    require(reports.contains(name + ":" + event), "Missing network check: " + name + ":" + event);
                }
            }
            require(reports.contains("ReleaseOwner:miner-warning"), "Miner warning not received by actual owner client");
            RobotEntity miner = robots.get(0);
            miner.setPosition(0.5, 65, 0.5);
            miner.setZoneCenter(HOME);
            miner.setEnergy(4801);
            miner.setMinerDirection(Direction.NORTH);
            for (int i = 1; i <= 6; i++) for (int y = 0; y <= 2; y++) world.setBlockState(HOME.north(i).up(y), Blocks.STONE.getDefaultState());
            world.setBlockState(HOME.north(7), Blocks.BEDROCK.getDefaultState());
            miner.setMode(RobotMode.WORK);
            stage = 2;
            elapsed = 0;
        } else if (stage == 2) {
            RobotEntity miner = robots.get(0);
            if (count(miner) < 501) { require(elapsed < 200, "Miner did not start"); return; }
            require(count(miner) == 501, "Checkpoint must interrupt between head and feet");
            miner.setMode(RobotMode.IDLE);
            for (RobotEntity robot : robots) robot.setMode(RobotMode.IDLE);
            JsonObject data = new JsonObject();
            for (int i = 0; i < robots.size(); i++) {
                NbtCompound nbt = snapshot(robots.get(i));
                data.addProperty("robot" + i, robots.get(i).getUuidAsString());
                data.addProperty("nbt" + i, nbt.toString());
            }
            data.addProperty("networkClients", 2);
            data.addProperty("checks", String.join(",", new TreeSet<>(reports)));
            write("checkpoint.json", data);
            write("network.json", Map.of("passed", true, "clients", 2, "checks", reports));
            System.out.println("RELEASE prepare passed; saving actual world and stopping");
            stage = -1;
            server.stop(false);
        } else if (stage == 10) {
            if (elapsed < 40) return;
            robots.clear();
            for (int i = 0; i < 2; i++) {
                Entity entity = world.getEntity(UUID.fromString(checkpoint.get("robot" + i).getAsString()));
                require(entity instanceof RobotEntity, "Robot missing after full JVM restart");
                RobotEntity robot = (RobotEntity) entity;
                assertSaved(snapshot(robot), StringNbtReader.parse(checkpoint.get("nbt" + i).getAsString()));
                robots.add(robot);
            }
            require(world.getBlockState(HOME.north().up()).isAir()
                    && world.getBlockState(HOME.north()).isOf(Blocks.STONE), "Partial section not saved");
            robots.get(0).setMode(RobotMode.WORK);
            stage = 11;
            elapsed = 0;
            System.out.println("RELEASE disk reload passed; resuming saved tunnel");
        } else if (stage == 11) {
            RobotEntity miner = robots.get(0);
            if (count(miner) < 512) { require(elapsed < 800, "Saved tunnel did not resume"); return; }
            require(count(miner) == 512 && miner.getInventory().count(Items.COBBLESTONE) == 24,
                    "Lost/duplicated mined items after restart");
            write("restart.json", Map.of("passed", true, "robots", 2, "blocksMined", 12,
                    "itemCount", 24, "savedFields", "owner,health,energy,mode,zone,direction,command,all-slots,NBT,partial-tunnel"));
            robots.forEach(Entity::discard);
            robots.clear();
            BlockPos far = new BlockPos(2048, 65, 2048);
            world.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
            platform(world, far, 2);
            chunkRobot = ModEntities.ROBOT.create(world);
            chunkRobot.refreshPositionAndAngles(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0, 0);
            chunkRobot.setOwnerUuid(UUID.randomUUID());
            chunkRobot.setEnergy(1234); chunkRobot.setHealth(29);
            chunkRobot.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 7));
            chunkRobot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.GUARD_MODULE));
            world.spawnEntity(chunkRobot);
            chunkUuid = chunkRobot.getUuid();
            chunkData = snapshot(chunkRobot);
            world.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
            stage = 12;
            elapsed = 0;
            System.out.println("RELEASE waiting for genuine chunk unload");
        } else if (stage == 12) {
            if (world.getEntity(chunkUuid) != null) { require(elapsed < 1600, "Chunk did not unload"); return; }
            world.setChunkForced(128, 128, true);
            world.getChunk(128, 128);
            stage = 13;
            elapsed = 0;
        } else if (stage == 13) {
            Entity entity = world.getEntity(chunkUuid);
            if (entity == null) { require(elapsed < 200, "Chunk robot did not reload"); return; }
            require(entity != chunkRobot && entity instanceof RobotEntity, "Expected newly deserialized entity");
            assertSaved(snapshot((RobotEntity) entity), chunkData);
            entity.discard();
            world.setChunkForced(128, 128, false);
            write("chunks.json", Map.of("passed", true, "genuineUnloadReload", true, "sameUuidNewInstance", true));
            scenario = 3;
            beginBenchmark(world);
            stage = 18;
            elapsed = 0;
            System.out.println("RELEASE chunk unload/reload passed; prewarming ten searching robots");
        } else if (stage == 18 || stage == 19) {
            if (elapsed < 400) return;
            robots.forEach(Entity::discard); robots.clear();
            if (stage == 18) {
                scenario = 7;
                beginBenchmark(world);
                stage = 19;
                elapsed = 0;
                System.out.println("RELEASE prewarming ten working robots");
            } else {
                scenario = 0;
                stage = 20;
                elapsed = 0;
            }
        } else if (stage == 20) {
            if (robots.isEmpty()) beginBenchmark(world);
            if (warmup++ < 60 || timings.robots() != robots.size()) return;
            samples.add(timings.totalNanos() / 1_000_000.0);
            serverSamples.add(serverMillis);
            if (samples.size() < 240) return;
            boolean active = scenario >= 4;
            if (active) for (RobotEntity robot : robots) require(count(robot) > 0, "Working benchmark did not mine");
            List<Double> sorted = samples.stream().sorted().toList();
            double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scenario", active ? "mining+guard+logistics" : "empty-search-lumberjack+farmer+logistics");
            row.put("robots", robots.size()); row.put("samples", samples.size()); row.put("warmupTicks", 60);
            row.put("meanTotalMs", mean); row.put("meanPerRobotMs", mean / robots.size());
            row.put("p95TotalMs", sorted.get((int) (sorted.size() * 0.95))); row.put("maxTotalMs", sorted.get(sorted.size() - 1));
            row.put("meanServerTickMs", serverSamples.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
            row.put("requirementMet", robots.size() == 1 ? mean <= 1 : robots.size() != 5 || mean <= 5);
            measurements.add(row);
            System.out.println("RELEASE benchmark " + row);
            robots.forEach(Entity::discard); robots.clear();
            samples.clear(); serverSamples.clear(); warmup = 0;
            if (++scenario == 8) {
                write("performance.json", Map.of("profile", "complete RobotEntity.tick including vanilla mob/navigation",
                        "java", System.getProperty("java.version"), "os", System.getProperty("os.name"),
                        "processors", Runtime.getRuntime().availableProcessors(), "prewarmTicks", 800, "results", measurements));
                write("summary.json", Map.of("passed", true, "networkClients", 2, "restart", true, "chunks", true,
                        "benchmarkScenarios", 8, "performanceThresholdsMet", measurements.stream().allMatch(r -> (boolean) r.get("requirementMet"))));
                stage = -1;
                System.out.println("RELEASE all checks complete");
                server.stop(false);
            }
        }
    }

    private void beginBenchmark(ServerWorld world) {
        require(RobotTimings.ENABLED, "Profiling must be enabled");
        int count = new int[]{1, 3, 5, 10}[scenario % 4];
        boolean active = scenario >= 4;
        for (int i = 0; i < count; i++) {
            BlockPos center = new BlockPos(i * 48, 65, 128);
            for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) world.setChunkForced((center.getX() >> 4) + cx, (center.getZ() >> 4) + cz, true);
            platform(world, center, 16);
            BlockPos start = active ? center.south(14) : center;
            RobotEntity robot = ModEntities.ROBOT.create(world);
            robot.refreshPositionAndAngles(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 180, 0);
            robot.setZoneCenter(center); robot.setEnergy(6400);
            robot.getInventory().setStack(19, new ItemStack(active ? ModItems.MINER_MODULE : ModItems.LUMBERJACK_MODULE));
            robot.getInventory().setStack(20, new ItemStack(active ? ModItems.GUARD_MODULE : ModItems.FARMER_MODULE));
            robot.getInventory().setStack(21, new ItemStack(ModItems.LOGISTICS_MODULE));
            if (active) for (int step = 1; step <= 29; step++) for (int y = 0; y <= 2; y++) world.setBlockState(start.north(step).up(y), Blocks.STONE.getDefaultState());
            world.spawnEntity(robot); robot.setMode(RobotMode.WORK); robots.add(robot);
        }
        warmup = 0;
        System.out.println("RELEASE profiling scenario " + scenario + " robots=" + count);
    }

    private static RobotEntity fixture(ServerWorld world, ServerPlayerEntity player, BlockPos pos, boolean miner) {
        player.changeGameMode(GameMode.CREATIVE);
        player.getInventory().clear(); player.getInventory().setStack(1, new ItemStack(Items.STONE, 3));
        RobotEntity robot = ModEntities.ROBOT.create(world);
        robot.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180, 0);
        robot.setOwnerUuid(player.getUuid()); robot.setCustomName(Text.literal(player.getGameProfile().getName() + "Bot"));
        robot.setZoneCenter(pos); robot.setHealth(31); robot.setEnergy(4801);
        robot.getInventory().setStack(0, new ItemStack(Items.COBBLESTONE, 12));
        robot.getInventory().setStack(1, new ItemStack(Items.WHEAT_SEEDS, 64));
        robot.getInventory().getStack(0).getOrCreateNbt().putString("AcceptanceMarker", "survives-real-restart");
        robot.getInventory().setStack(18, new ItemStack(Items.COAL, 3));
        robot.getInventory().setStack(19, new ItemStack(miner ? ModItems.MINER_MODULE : ModItems.FARMER_MODULE));
        robot.getInventory().getStack(19).getOrCreateNbt().putInt(ModuleProgress.COUNTER_KEY, 500);
        robot.getInventory().setStack(20, new ItemStack(miner ? ModItems.GUARD_MODULE : ModItems.LUMBERJACK_MODULE));
        robot.getInventory().getStack(20).getOrCreateNbt().putInt(ModuleProgress.COUNTER_KEY, 100);
        robot.getInventory().setStack(21, new ItemStack(ModItems.LOGISTICS_MODULE));
        world.spawnEntity(robot);
        return robot;
    }

    private static NbtCompound snapshot(RobotEntity robot) { NbtCompound nbt = new NbtCompound(); robot.writeCustomDataToNbt(nbt); return nbt; }
    private static void assertSaved(NbtCompound actual, NbtCompound expected) {
        for (String key : new String[]{"Owner", "Inventory", "Energy", "Mode", "ZoneCenter", "ZoneRadius", "MinerDir", "MinerCommand", "Health", "CustomName"}) {
            require(Objects.equals(actual.get(key), expected.get(key)), "Saved field mismatch: " + key + " expected=" + expected.get(key) + " actual=" + actual.get(key));
        }
    }
    private static int count(RobotEntity robot) { return ModuleProgress.counter(robot.getInventory().getStack(19)); }
    private static void platform(ServerWorld world, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) world.setBlockState(center.add(x, -1, z), Blocks.STONE.getDefaultState());
    }
    private void write(String name, Object value) throws Exception { Files.writeString(output.resolve(name), new GsonBuilder().setPrettyPrinting().create().toJson(value)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private void fail(MinecraftServer server, Exception error) {
        error.printStackTrace();
        try { write("failure.json", Map.of("phase", phase, "stage", stage, "error", error.toString())); } catch (Exception ignored) { }
        stage = -1;
        server.stop(false);
    }
}
