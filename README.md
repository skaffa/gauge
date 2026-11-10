# Gauge

Client-side redstone and container diagnostics for Minecraft 26.3 (Fabric). Power levels on dust,
output arrows on repeaters, comparators, observers and hoppers, strong/weak powered blocks, pulse and
clock highlighting, container fill and comparator output, note block pitch and jukebox song progress —
drawn in the world. No server mod, no
packets of its own.

![Radius mode](media/radius.png)

## Use

- **Z** — switch Radius mode (everything nearby) / Focus mode (crosshair only)
- **N** — hide/show the overlay
- `/gauge` — settings (also via Mod Menu), saved in `config/gauge/config.json`; `/gauge forget` — clear remembered containers
- Open a container once to read it: Gauge remembers its fill and comparator output (`37% | 6`).
  Servers never send contents of unopened containers, so those show `?`.
- Comparator outputs are estimated (`~6`): the server does not send them.
- Note blocks show their note (`C4`); the panel adds step, pitch, Hz and the next click.
- Jukeboxes show the song and its progress, if it started while you were nearby.
- Lecterns show page and comparator output (`p3/5 | 8`) once you have read the book.
- In singleplayer every value is exact and live (read from the built-in server): containers without
  opening them, comparator outputs, jukebox songs and lectern pages.

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
./gradlew runClient "-Pdevshots=3,-60,3;6,-60,3;8,-60,3" --args="--username GaugeTester --quickPlayMultiplayer localhost:25565"
python tools/rcon.py gaugetest --script tools/testrig.txt   # once the player has joined
python tools/rcon.py gaugetest --script tools/disc.txt      # a few seconds later: starts a jukebox song
```

The client takes screenshots (`run/screenshots/gauge-*.png`), opens the test chest, aims at each
further target in focus mode, and quits.

## License

MIT
