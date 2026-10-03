# Magnetism Smoke Engine — Stage 4 test plan

The engine is server-side. Smoke state is runtime-derived and is rebuilt from loaded campfire block entities after restart.

| # | Scenario | Expected result |
|---|---|---|
| 1 | 4 lit campfires in one connected air volume | No `SmokeRegion`; only vanilla campfire smoke exists. |
| 2 | 5 lit campfires in one connected air volume | One `SmokeRegion` is created; density is injected at all active source cells. |
| 3 | 5 campfires in sealed room | Smoke rises, reaches the top layer, diffuses laterally; no exterior cells are reached. |
| 4 | Same room with an open door to outside | BFS reaches the outside boundary through the door; exterior cells drain density and particles bias toward the exit. |
| 5 | Same room with one open trapdoor/hatch | Open collision shape creates a traversable route and an exterior path. |
| 6 | Wall window/hole | Open cells connect to the bounded exterior flood-fill. |
| 7 | Roofless building | Smoke can rise to open boundary cells and then diffuses laterally / drains toward the outside. |
| 8 | Very large building | Region and BFS are capped by `maxRegionSize`, `maxCells`, and `maxAirSearch`; no unbounded scan occurs. |
| 9 | Two independent rooms, 5 fires each | Two regions; topology in one room cannot move smoke through the separating solid wall. |
| 10 | Two rooms connected by a door | One connectivity group when the door is open; smoke can cross and exit if the combined area reaches the exterior. |
| 11 | Remove source | Block-entity unload / periodic source refresh marks the area dirty; region falls below threshold and is removed. |
| 12 | Chunk unload | Simulation skips cells whose center chunk is not loaded; source index entry is removed until reload. |
| 13 | Chunk reload | Campfires in the chunk are re-indexed; a qualifying region is rebuilt. |
| 14 | Server restart | No smoke field is serialized. Loaded campfires are rediscovered and runtime regions are reconstructed. |
| 15 | 20+ players distributed across smoke zones | Simulation remains region/cell-budgeted and does not multiply work per player. Player exposure is only a later consumer of sampled density. |
