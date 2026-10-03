# Stage 5 test plan

## Smoke exposure

- Vanilla max health 20 HP. After 1-9 successful smoke penalties, effective max health must be 18, 16, 14, 12, 10, 8, 6, 4, 2 HP. A tenth penalty must not be possible while no-smoke max health is 20 HP.
- With smoke density below the configured dangerous threshold, exposure must not increase even when the player stands beside a campfire.
- With density at or above threshold, exposure increases one tick per server tick.
- After 12000 dangerous ticks without Second Wind, one heart is lost.
- After 6000 clean-air ticks, one lost heart is restored.
- Recovery never increases the current health value by itself; it only restores maximum-health capacity.
- State survives relog, server restart, death, and dimension change.

## Second Wind

- Level I threshold: 18000 ticks.
- Level II threshold: 30000 ticks.
- Level III threshold: 42000 ticks.
- Removing/replacing the helmet changes the active threshold on the next exposure tick.
- No client-only code is required.

## Sneeze

- Sound plays once per successful heart loss.
- Green slime-like particles originate approximately at mouth height, offset in the player's look direction.
- No sneeze particles are emitted merely because the player reaches the minimum-health floor.

## Speed Flyer

- I: flying speed multiplier 1.25x.
- II: 1.40x.
- III: 1.65x.
- IV: 1.80x.
- V: 2.00x.
- Attribute modifier is data-driven and tied to the harness body slot, so equipping/removing the harness changes it without a custom movement tick loop.
- Replacing a harness or changing enchantment level must not duplicate the modifier because the enchantment effect has a stable modifier id.
- Harness removal, relog, death and server restart must not leave stale speed modifiers.
