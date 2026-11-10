# Gauge

Client-side redstone and container diagnostics for Minecraft 26.3 (Fabric). Power levels on dust,
output arrows on repeaters, comparators, observers and hoppers, strong/weak powered blocks, pulse and
clock highlighting, container fill and comparator output — drawn in the world. No server mod, no
packets of its own.

![Radius mode](media/radius.png)

## Use

- **Z** — switch Radius mode (everything nearby) / Focus mode (crosshair only)
- **N** — hide/show the overlay
- `/gauge` — settings (also via Mod Menu); `/gauge forget` — clear remembered containers
- Open a container once to read it: Gauge remembers its fill and comparator output (`37% | 6`).
  Servers never send contents of unopened containers, so those show `?`.
- Comparator outputs are estimated (`~6`): the server does not send them.

See [SPEC.md](SPEC.md) for details and known limits.

## Build

Needs JDK 25.

```
./gradlew build          # jar in build/libs, runs the JUnit tests
./gradlew runClient
```

### Scripted in-game check

```
./gradlew runServer --args=nogui         # run-server/: flat, offline, RCON password gaugetest
./gradlew runClient -Pdevshots=3,-60,3 --args="--username GaugeTester --quickPlayMultiplayer localhost:25565"
python tools/rcon.py gaugetest --script tools/testrig.txt   # once the player has joined
```

The client takes screenshots (`run/screenshots/gauge-*.png`), opens the test chest, and quits.

## License

MIT
