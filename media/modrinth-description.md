**See what your redstone is doing.** Gauge labels redstone in the world: power levels on dust, output arrows on repeaters and comparators, which blocks are strongly or weakly powered, how fast a clock ticks, how full a container is and what a comparator reads from it. It also covers note blocks, jukeboxes, pistons, doors, rails, tripwires, minecarts and lit TNT.

Everything happens on your own computer: Gauge needs nothing on the server and sends no packets of its own.

## Two modes

- **Radius** (default): labels everything within 10 blocks in front of you (2–32, your choice).
- **Focus**: only the block or cart under your crosshair, with labels drawn through walls.

Press **Z** to switch. In both modes a small panel next to the crosshair shows the details of what you look at.

## Redstone

- **Dust**: power 0–15, coloured from grey to bright red.
- **Repeaters**: delay, locked state and an arrow showing where they output.
- **Comparators**: compare or subtract, the output value and an arrow. Servers never send a comparator's output, so Gauge works it out from its inputs (`~6`).
- **Observers**, **torches**, **levers**, **buttons**, **plates**, **lamps**, **crafters**: on or off and their output.
- **Powered blocks**: `S15` for a strongly powered block (powers dust next to it), `W8` for a weakly powered one.
- **Pulses**: a block that just changed gets a cyan outline with the time since its last change. Clocks show their period (`clock 4gt`); outlines turn magenta for very fast pulses (1–2 game ticks).

## Containers

- Fill and comparator output (`37% | 6`) for chests, barrels, hoppers, dispensers, droppers, furnaces and shulker boxes. Double chests count both halves.
- Servers don't send what is inside a container until you open it, so Gauge remembers each container you open. Containers you have not opened show `?`.
- **Hoppers** show where they output and whether redstone locks them.
- **Furnaces, smokers, blast furnaces**: `lit` while burning, `lit!` when lit without any fuel burning (for example set by a command).
- **Composters, cauldrons, cakes, beehives** and other blocks a comparator can read.
- **Lecterns**: page and comparator output (`p3/5 | 8`) once you have read the book.

## Mechanisms

- **Pistons and sticky pistons**: extended or retracted. Gauge uses the game's own power check, including quasi-connectivity, and marks a piston orange (`ret!`, `ext!`) when it is powered but retracted, or unpowered but extended: a BUD, or a blocked push.
- **Doors, trapdoors, fence gates**: open or closed, `P` when powered.
- **Rails**: powered and activator rails on or off; detector rails with their output and the comparator value of the cart on them.
- **Bells**: ringing or powered. **Tripwires**: a tripped string gets a red outline, hooks show whether they are armed.
- **TNT**: unstable TNT is marked; lit TNT and TNT minecarts count down their fuse.
- **Beehives and nests**: honey level 0–5.

## Sound

- **Note blocks**: the note (`C4`, `F#6`), the instrument, and in the panel the click count, pitch, frequency and next note.
- **Jukeboxes**: the song and how far along it is (`♪ 0:53/3:05`), plus the comparator output.

## Minecarts and boats

- Chest and hopper minecarts and chest boats show their fill once you have opened them.
- Hopper minecarts show whether an activator rail has locked them. Furnace minecarts show whether they have fuel.

## Singleplayer: exact and live

In singleplayer Gauge reads the built-in server directly, so every value is exact and updates live. You don't need to open anything first: container and cart contents, comparator outputs, jukebox songs, lectern pages, bees in a hive, furnace fuel and cook progress, furnace cart fuel and hopper cart lock.

## Controls

| Key | What it does |
|---|---|
| **Z** | Switch Radius / Focus mode |
| **N** | Hide or show the overlay |
| (not bound) | Open the settings screen |

All keys can be changed under Options → Controls → Gauge. The settings screen also opens with `/gauge` or from [Mod Menu](https://modrinth.com/mod/modmenu). There you can turn each kind of label on or off and set the radius. `/gauge forget` clears the containers Gauge remembers.

Settings are saved in `config/gauge/config.json`.

## Compatibility

- Minecraft **26.3**, Fabric Loader 0.20.1+, [Fabric API](https://modrinth.com/mod/fabric-api), Java 25.
- Labels are drawn with the game's own debug drawing and no render hooks of its own, so they should work with Sodium and Iris (not tested yet).
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu) for a Configure button.
- Other Minecraft versions and NeoForge may follow.

## Credits

Made by Skaffa, written together with Claude (Anthropic). Source code on [GitHub](https://github.com/skaffa/gauge) under the [MIT License](https://github.com/skaffa/gauge/blob/main/LICENSE).
