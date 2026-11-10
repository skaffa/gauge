# Gauge — spec (revised)

Client-side Fabric mod for Minecraft 26.3. Shows redstone power, output directions, pulse timing,
container fill and comparator output in the world. Sends no packets of its own and needs nothing on the
server.

## Features

| Feature | Source of the data |
|---|---|
| Dust power 0–15 | `RedstoneWireBlock.POWER` block state |
| Repeater delay, lock, output arrow | `DELAY`, `LOCKED`, `POWERED`, `FACING` (output = opposite of `FACING`) |
| Comparator mode + estimated output | `MODE`, `POWERED`; output recomputed from its inputs (see below) |
| Observer gaze and pulse direction | `FACING` (watches `FACING`, outputs to the opposite side) |
| Torches, levers, buttons, plates, daylight sensors, target blocks... | `BlockState.getSignal` in all directions |
| Powered solid blocks: `S15` strong / `W14` weak | neighbouring components' `getSignal` / `getDirectSignal` (dust counts as weak only) |
| Hopper direction and lock, dispenser/dropper facing | `FACING`, `ENABLED`, `TRIGGERED` |
| Container fill % and comparator output | remembered from the last time the player opened it |
| Composter, cauldron, cake, respawn anchor, beehive... comparator output | `getAnalogOutputSignal` (pure block state) |
| Pulse highlight, clock period | every client block change via a `ClientLevel.sendBlockUpdated` mixin |
| Target panel beside the crosshair | all of the above, in words |

Modes: **Radius** (everything within 2–32 blocks, nearest first, capped at `maxLabels`) and **Focus**
(only the block under the crosshair, drawn through walls). Key **Z** switches, **N** hides the overlay,
`/gauge` opens the settings, `/gauge forget` clears remembered containers. Filters: dust, unpowered dust,
logic blocks, powered blocks, containers, pulses, target panel.

## Corrections to the first draft

1. **Container contents are not on the client.** Vanilla servers send a chest's, hopper's or barrel's
   items only while a player has it open; client block entities are empty otherwise. There are no
   "client-synced container tags". Gauge therefore remembers contents when *you* open a container
   (matched to the block you right-clicked, double chests included) and shows the age of that reading.
   Never-opened containers show `?`. Hopper chains change constantly, so readings go stale fast.
2. **Comparator output is not on the client either.** It lives in `ComparatorBlockEntity`, which is never
   synced; only `POWERED` is. Gauge recomputes it from the rear and side inputs (`~6`) and says "estimated".
3. **"Powered solid blocks" needs care.** `Level.getBestNeighborSignal` treats a strongly powered
   neighbour block as a source, which is wrong for a solid block, and dust's direct signal is non-zero in
   code even though dust only weakly powers. Gauge computes strong/weak itself.
4. **Pulse catcher limits.** The client sees the state the server sends at the end of each tick: 0-tick
   pulses are invisible, and timings are measured in client game ticks, which drift when the server lags
   (TPS < 20). Fast (≤ 2 game ticks) toggling shows magenta, slower changes cyan; a repeating on/off
   pattern is reported as a clock period.
5. **No render-event hooks.** `WorldRenderEvents.AFTER_TRANSLUCENT` is gone from current Fabric API.
   Gauge submits vanilla *gizmos* (billboard text, arrows, cuboids) once per tick; the vanilla renderer
   draws them every frame, which also keeps it working with Sodium/Iris.
6. **No radial menu.** A settings screen (Mod Menu supported) plus two keys covers the filters with far
   less code. The `Z` default is kept for the mode switch.
7. **Performance** comes from skipping whole 16³ chunk sections whose block palette holds nothing
   relevant (`LevelChunkSection.maybeHas`), a behind-the-camera cull, and the label cap — not from a
   per-frame frustum pass.
8. **Fabric only** for now; NeoForge would need a second loader module.
9. Units: repeater delay is shown in redstone ticks and game ticks; pulse timings in game ticks (`gt`).
