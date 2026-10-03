package ru.magnetism.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import ru.magnetism.util.ModLogger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "magnetism.json";

    private boolean enabled = true;
    private boolean debugLogging = false;
    private MagnetConfig magnet = new MagnetConfig();
    private CopierConfig copier = new CopierConfig();
    private SmokeConfig smoke = new SmokeConfig();
    private SmokeExposureConfig smokeExposure = new SmokeExposureConfig();

    private static ModConfig current;

    private ModConfig() {
    }

    public static ModConfig defaults() {
        return new ModConfig();
    }

    public static void load() {
        Path file = getConfigFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                current = defaults();
                save();
                ModLogger.info("Created default config at {}", file);
                return;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                current = loaded == null ? defaults() : loaded;
            }
            current.normalize();
            ModLogger.info("Loaded config from {}", file);
        } catch (IOException | JsonIOException | JsonSyntaxException exception) {
            current = defaults();
            ModLogger.error("Failed to load config; using defaults.", exception);
        }
    }

    public static void save() {
        ModConfig config = current == null ? defaults() : current;
        Path file = getConfigFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException | JsonIOException exception) {
            ModLogger.error("Failed to save config " + file, exception);
        }
    }

    public static ModConfig get() {
        if (current == null) {
            current = defaults();
        }
        current.normalize();
        return current;
    }

    public static void reset() {
        current = null;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean isDebugLoggingEnabled() {
        return debugLogging;
    }

    public MagnetConfig magnet() {
        return magnet;
    }

    public CopierConfig copier() {
        return copier;
    }

    public SmokeConfig smoke() {
        return smoke;
    }

    public SmokeExposureConfig smokeExposure() {
        return smokeExposure;
    }

    private void normalize() {
        if (magnet == null) magnet = new MagnetConfig();
        if (copier == null) copier = new CopierConfig();
        if (smoke == null) smoke = new SmokeConfig();
        if (smokeExposure == null) smokeExposure = new SmokeExposureConfig();
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static final class MagnetConfig {
        private boolean enabled = true;
        private boolean debugLogging = false;
        private double radius = 12.0D;
        private double baseStrength = 0.02D;
        private int updateIntervalTicks = 2;
        private double minimumDistance = 0.15D;
        private double falloffExponent = 2.0D;
        private double verticalForceMultiplier = 1.0D;
        private double maxAccelerationPerUpdate = 0.18D;
        private double maxResultingVelocity = 1.50D;
        private double chainmailArmorWeight = 1.0D;
        private double ironArmorWeight = 2.0D;
        private double diamondArmorWeight = 3.0D;
        private double netheriteArmorWeight = 4.0D;
        private double itemBaseWeight = 1.0D;

        public boolean enabled() { return enabled; }
        public boolean debugLogging() { return debugLogging; }
        public double radius() { return Math.max(1.0D, radius); }
        public double baseStrength() { return Math.max(0.0D, baseStrength); }
        public int updateIntervalTicks() { return Math.max(1, updateIntervalTicks); }
        public double minimumDistance() { return Math.max(0.01D, minimumDistance); }
        public double falloffExponent() { return Math.max(0.1D, falloffExponent); }
        public double verticalForceMultiplier() { return Math.max(0.0D, verticalForceMultiplier); }
        public double maxAccelerationPerUpdate() { return Math.max(0.0D, maxAccelerationPerUpdate); }
        public double maxResultingVelocity() { return Math.max(0.05D, maxResultingVelocity); }
        public double chainmailArmorWeight() { return Math.max(0.0D, chainmailArmorWeight); }
        public double ironArmorWeight() { return Math.max(0.0D, ironArmorWeight); }
        public double diamondArmorWeight() { return Math.max(0.0D, diamondArmorWeight); }
        public double netheriteArmorWeight() { return Math.max(0.0D, netheriteArmorWeight); }
        public double itemBaseWeight() { return Math.max(0.0D, itemBaseWeight); }
    }


    public static final class SmokeConfig {
        private boolean enabled = true;
        private int minCampfires = 5;
        private int cellSize = 3;
        private int maxRegionSize = 64;
        private int maxCells = 8192;
        private int updateInterval = 4;
        private int maxAirSearch = 4096;
        private int maxActiveRegions = 64;
        private double particleDensity = 0.55D;
        private int particleUpdateInterval = 8;
        private int maxParticlesPerRegionPerUpdate = 64;
        private double particleThreshold = 0.08D;
        private int topologyRefreshInterval = 40;
        private int sourceRefreshInterval = 40;
        private int sourceRefreshBudget = 4096;
        private int regionIdleTimeout = 200;
        private double sourceDensityPerStep = 0.35D;
        private double diffusionRate = 0.42D;
        private double decayRate = 0.045D;
        private double upwardWeight = 1.8D;
        private double lateralWeight = 0.65D;
        private double downwardWeight = 0.12D;
        private double exitRate = 0.18D;
        private double cellRemovalThreshold = 0.008D;

        public boolean enabled() { return enabled; }
        public int minCampfires() { return clamp(minCampfires, 1, 128); }
        public int cellSize() { return clamp(cellSize, 2, 4); }
        public int maxRegionSize() { return clamp(maxRegionSize, 16, 256); }
        public int maxCells() { return clamp(maxCells, 256, 65536); }
        public int updateInterval() { return clamp(updateInterval, 1, 20); }
        public int maxAirSearch() { return clamp(maxAirSearch, 64, 65536); }
        public int maxActiveRegions() { return clamp(maxActiveRegions, 1, 1024); }
        public double particleDensity() { return clamp(particleDensity, 0.0D, 1.0D); }
        public int particleUpdateInterval() { return clamp(particleUpdateInterval, 1, 40); }
        public int maxParticlesPerRegionPerUpdate() { return clamp(maxParticlesPerRegionPerUpdate, 1, 256); }
        public double particleThreshold() { return clamp(particleThreshold, 0.0D, 1.0D); }
        public int topologyRefreshInterval() { return clamp(topologyRefreshInterval, 5, 200); }
        public int sourceRefreshInterval() { return clamp(sourceRefreshInterval, 5, 200); }
        public int sourceRefreshBudget() { return clamp(sourceRefreshBudget, 64, 65536); }
        public int regionIdleTimeout() { return clamp(regionIdleTimeout, 20, 1200); }
        public double sourceDensityPerStep() { return clamp(sourceDensityPerStep, 0.01D, 1.0D); }
        public double diffusionRate() { return clamp(diffusionRate, 0.0D, 1.0D); }
        public double decayRate() { return clamp(decayRate, 0.0D, 1.0D); }
        public double upwardWeight() { return Math.max(0.0D, upwardWeight); }
        public double lateralWeight() { return Math.max(0.0D, lateralWeight); }
        public double downwardWeight() { return Math.max(0.0D, downwardWeight); }
        public double exitRate() { return clamp(exitRate, 0.0D, 1.0D); }
        public double cellRemovalThreshold() { return clamp(cellRemovalThreshold, 0.0001D, 0.25D); }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public static final class SmokeExposureConfig {
        private boolean enabled = true;
        private double dangerousDensityThreshold = 0.18D;
        private int baseExposureTicks = 12000;
        private int cleanAirRecoveryTicks = 6000;
        private int secondWindLevel1Ticks = 18000;
        private int secondWindLevel2Ticks = 30000;
        private int secondWindLevel3Ticks = 42000;
        private double minMaxHealth = 2.0D;
        private int maximumExposureCounter = 42000;
        private int maximumCleanAirCounter = 6000;

        public boolean enabled() { return enabled; }
        public double dangerousDensityThreshold() { return clamp(dangerousDensityThreshold, 0.01D, 1.0D); }
        public int baseExposureTicks() { return clamp(baseExposureTicks, 1, 1_000_000); }
        public int cleanAirRecoveryTicks() { return clamp(cleanAirRecoveryTicks, 1, 1_000_000); }
        public int secondWindLevel1Ticks() { return clamp(secondWindLevel1Ticks, baseExposureTicks(), 1_000_000); }
        public int secondWindLevel2Ticks() { return clamp(secondWindLevel2Ticks, secondWindLevel1Ticks(), 1_000_000); }
        public int secondWindLevel3Ticks() { return clamp(secondWindLevel3Ticks, secondWindLevel2Ticks(), 1_000_000); }
        public double minMaxHealth() { return Math.max(2.0D, minMaxHealth); }
        public int maximumExposureCounter() { return Math.max(baseExposureTicks(), maximumExposureCounter); }
        public int maximumCleanAirCounter() { return Math.max(cleanAirRecoveryTicks(), maximumCleanAirCounter); }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public static final class CopierConfig {
        private int writtenBookBaseCost = 1;
        private int writtenBookPageCost = 1;
        private int enchantedBookBaseCost = 2;
        private int enchantedBookLevelCost = 1;
        private int maxCopyCost = 40;

        public int writtenBookBaseCost() { return Math.max(0, writtenBookBaseCost); }
        public int writtenBookPageCost() { return Math.max(0, writtenBookPageCost); }
        public int enchantedBookBaseCost() { return Math.max(0, enchantedBookBaseCost); }
        public int enchantedBookLevelCost() { return Math.max(0, enchantedBookLevelCost); }
        public int maxCopyCost() { return Math.max(0, maxCopyCost); }
    }
}
