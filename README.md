# Magnetism — Stage 4: Smoke Simulation Engine

Minecraft Java Edition 26.2 / Fabric / server-side core.

## Toolchain

- Minecraft: 26.2
- Fabric Loader: 0.19.5
- Fabric API: 0.161.0+26.2
- Fabric Loom: 1.18-SNAPSHOT
- Java: 25
- Gradle: 9.7.1

## Stage 4 scope

Implemented only the Smoke Simulation Engine. Magnet and Copier remain intact; enchantments, player health penalties, and Geyser presentation are not implemented here.

## Smoke model

- Smoke activates only when a connected source group has at least 5 lit campfires.
- Both normal and soul campfires are accepted as sources.
- Source discovery uses loaded-chunk and server block-entity lifecycle events; no world-wide per-tick block scan is used.
- The simulation uses a sparse 3x3x3 coarse grid by default.
- `SmokeCell` stores density, flow, permeability, exterior connectivity, active state, and last-update tick.
- Smoke state is runtime-derived and intentionally not persisted to world save data.
- Topology is rebuilt on a bounded schedule and after source/chunk lifecycle changes.
- `AirConnectivityService` uses bounded BFS with `maxAirSearch` and `maxRegionSize` limits.
- Open doors, trapdoors, holes, and roof openings are represented through collision-shape permeability and bounded exterior connectivity.
- Unloaded chunk cells are never advanced by the simulation.
- Active regions and cells are budgeted by `maxActiveRegions` and `maxCells`.
- Particle emission is server-side and uses vanilla campfire smoke particles. Gameplay does not depend on successful particle rendering.

## Configuration

The following smoke options are available in `config/magnetism.json`:

- `smoke.enabled`
- `smoke.minCampfires`
- `smoke.cellSize`
- `smoke.maxRegionSize`
- `smoke.maxCells`
- `smoke.updateInterval`
- `smoke.maxAirSearch`
- `smoke.maxActiveRegions`
- `smoke.particleDensity`
- `smoke.particleUpdateInterval`
- `smoke.maxParticlesPerRegionPerUpdate`
- `smoke.particleThreshold`
- `smoke.topologyRefreshInterval`
- `smoke.sourceRefreshInterval`
- `smoke.sourceRefreshBudget`
- `smoke.regionIdleTimeout`
- `smoke.sourceDensityPerStep`
- `smoke.diffusionRate`
- `smoke.decayRate`
- `smoke.upwardWeight`
- `smoke.lateralWeight`
- `smoke.downwardWeight`
- `smoke.exitRate`
- `smoke.cellRemovalThreshold`

### Default coarse cell choice

The default is **3x3x3 blocks per cell**. This is the selected starting point because it cuts the number of simulation nodes by a factor of 27 versus block-resolution while retaining enough spatial resolution to distinguish rooms, openings, roofs, and vertical smoke movement. A 2x2x2 grid is available only through future configuration changes because it increases CPU/memory cost substantially; 4x4x4 is intentionally avoided as the default because small doors and hatches become too coarse.

## Why no Mixin is used yet

Fabric's server chunk and block-entity lifecycle events are sufficient for source indexing, while topology is refreshed on a bounded interval. A generic `Level#setBlock` mixin would add a global hook to every block mutation and is therefore deliberately avoided in the performance-sensitive first implementation. The current design catches campfire lifecycle immediately and catches doors/walls/roof changes on the bounded topology refresh. If profiling later demonstrates that a sub-second response to arbitrary block changes is required, a single narrow server-side block-state-change hook can be introduced without changing the smoke model.

## Build

After installing JDK 25 and Gradle 9.7.1 (or generating the official wrapper):

```text
gradle wrapper --gradle-version 9.7.1
./gradlew check
./gradlew build
```

Dedicated server dev run:

```text
./gradlew runServer
```

## Smoke test plan

See `docs/smoke-test-plan.md` for the requested 15 manual scenarios covering sealed rooms, exits, hatches, roofless structures, independent/connected rooms, source removal, chunk lifecycle, restarts, and 20+ players.

## Validation limitation

The preparation environment currently has JDK 21 and no Gradle executable, so a real Minecraft 26.2 compilation/server boot cannot honestly be claimed as executed here. The source tree, configuration surface, resource structure, and API usage were checked against Fabric 26.2 documentation/API references before packaging.

## Stage 5: Smoke Exposure and Enchantments

Implemented server-side smoke exposure using the Smoke Engine density field, persistent Fabric Data Attachments for exposure/clean-air counters and lost hearts, a max-health attribute modifier capped at 2 HP, sneeze feedback, the `magnetism:second_wind` data-driven enchantment, and the `magnetism:speed_flyer` data-driven Happy Ghast flying-speed attribute enchantment.

### Stage 5 API decisions

- Smoke Exposure reads `SmokeManager.sampleDensity(ServerPlayer)` and never tests campfire proximity.
- Player exposure, clean-air time, and lost-heart count use persistent Fabric Data Attachments with `copyOnDeath()`.
- Lost hearts are represented by one permanent `MAX_HEALTH` `ADD_VALUE` modifier with a stable identifier. The modifier is reconciled against other max-health modifiers and clamped so Magnetism never lowers its effective max health below 2 HP.
- Second Wind is a data-driven enchantment definition; its timing is read by the server exposure service from the current helmet enchantment level.
- Speed Flyer is a data-driven attribute enchantment. It uses a stable attribute-modifier identifier, the Happy Ghast body slot, and a 26.2 `lookup` value provider for the exact I-V multipliers.
