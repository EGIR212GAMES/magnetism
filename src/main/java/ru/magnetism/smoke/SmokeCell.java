package ru.magnetism.smoke;

/**
 * Runtime coarse-grid cell. One cell represents a cubic group of blocks rather
 * than a single block. This keeps both memory and simulation work bounded.
 */
public final class SmokeCell {
    private final CellPos pos;
    private float density;
    private float flowX;
    private float flowY;
    private float flowZ;
    private float permeability;
    private boolean exterior;
    private boolean active;
    private long lastUpdateTick;

    public SmokeCell(CellPos pos) {
        this.pos = pos;
        this.permeability = 1.0F;
    }

    public CellPos pos() {
        return pos;
    }

    public float density() {
        return density;
    }

    public void setDensity(float density) {
        this.density = Math.max(0.0F, Math.min(1.0F, density));
        this.active = this.density > 0.001F;
    }

    public float flowX() {
        return flowX;
    }

    public float flowY() {
        return flowY;
    }

    public float flowZ() {
        return flowZ;
    }

    public void setFlow(float x, float y, float z) {
        this.flowX = x;
        this.flowY = y;
        this.flowZ = z;
    }

    public float permeability() {
        return permeability;
    }

    public void setPermeability(float permeability) {
        this.permeability = Math.max(0.0F, Math.min(1.0F, permeability));
    }

    public boolean exterior() {
        return exterior;
    }

    public void setExterior(boolean exterior) {
        this.exterior = exterior;
    }

    public boolean active() {
        return active;
    }

    public long lastUpdateTick() {
        return lastUpdateTick;
    }

    public void markUpdated(long tick) {
        this.lastUpdateTick = tick;
    }

    public void clear() {
        this.density = 0.0F;
        this.flowX = 0.0F;
        this.flowY = 0.0F;
        this.flowZ = 0.0F;
        this.active = false;
    }

    public record CellPos(int x, int y, int z) {
        public CellPos offset(int dx, int dy, int dz) {
            return new CellPos(x + dx, y + dy, z + dz);
        }

        public int blockX(int cellSize) {
            return x * cellSize;
        }

        public int blockY(int cellSize) {
            return y * cellSize;
        }

        public int blockZ(int cellSize) {
            return z * cellSize;
        }
    }
}
