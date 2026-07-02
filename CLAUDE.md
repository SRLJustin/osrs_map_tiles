# osrs_map_tiles — map update runbook

Short guide for regenerating the map tiles when a new OSRS cache drops.

## What this repo is

The OSRS world map split into 256px tiles for leaflet-style map viewers.

- `0/ 1/ 2/ 3/` — one directory per map plane (z-level). Inside each: `<zoom>/<x>/<y>.png`, zoom levels 3–11.
- `generated_images/current-map-image-{0..3}.png` — the full rendered map image per plane. These are the source the tiles are cut from **and** the baseline the next update diffs against, so they must stay in the repo.

## Before updating: sync `internal` with `main`

**Always start from the latest published maps.** The diff step only regenerates tiles that changed since the committed `current-map-image-*.png`, so `internal` must hold the newest images before you generate. Pull `main` in first:

```sh
git checkout internal
git fetch origin
git merge origin/main        # brings in the most recently merged rev-N images
```

Do map generation on `internal` (it has the build fixes). Then commit only the images to a fresh `rev-N` branch for the PR to `main` (see "What to commit").

## How to update the images

Needs Docker (Desktop configured with ≥ 8 GB memory). The generator downloads the latest cache from openrs2, renders the full map images, diffs them against the committed `current-map-image-*.png`, and regenerates **only the changed tiles**. Full run is ~25–30 min.

From the repo root:

```sh
# PowerShell: $Env:DOCKER_BUILDKIT=0
export DOCKER_BUILDKIT=0
docker build ./tile_generator -t map-tile-generator
docker run --rm -v "<repo-root>:/repo" map-tile-generator   # PowerShell: -v "${pwd}:/repo"
```

> Build fixes required to compile the generator live on the **`internal`** branch (see below). Build/run from `internal`, or cherry-pick its commits — a plain `master` checkout will not build.

## Naming

Name the update "rev N", where N is the openrs2 cache **build** number the run prints:

```
INFO Cache build: ['239']      ->  branch/PR "rev 239"
```

## What to commit

Only:

- changed tiles under `0/ 1/ 2/ 3/`
- `generated_images/current-map-image-{0..3}.png`

`cache/`, `generated_images/diff-map-image-*.png`, and `previous-map-image-*.png` are gitignored — leave them out.

## Gotchas / known issues

- **Build fixes are on `internal`, not `master`.** `master` is intentionally kept to published images + this doc. The two fixes:
  - `tile_generator/java/build.gradle.kts` — adds explicit `commons-cli`, `slf4j-api`, `antlr4-runtime` deps. They are runtime-only transitives of `net.runelite:cache`, so `Main.java` fails to compile without them (`package ... does not exist`).
  - `.gitattributes` — pins `tile_generator/java/gradlew` to LF. On Windows with `core.autocrlf=true` it otherwise checks out as CRLF and the Docker build dies with `./java/gradlew: required file not found`.
- **XTEA keys are no longer needed** — OSRS map data is unencrypted now. `XteaKeyManager - Loaded 0 keys` and the many `Archive - revision mismatch ...` warnings during the render are **benign**; the map still renders correctly.
- The diff step needs the previous `current-map-image-*.png` present (they're committed, so a fresh checkout is fine).

## Rendering historical (pre-modern) caches

The modern RuneLite cache lib can't parse early OSRS caches out of the box. Four shadow classes under `tile_generator/java/src/main/java/net/runelite/cache/` fix this — they share fully-qualified names with the library and win when our compiled classes are placed **ahead of** the `net.runelite:cache` jar on the classpath. All are no-ops on modern caches, so normal updates are unaffected.

- `definitions/loaders/MapLoader.java` — legacy 2007-engine terrain encodes the tile attribute + overlay as **bytes**, not shorts. Selected via system property `-Dosrs.map.format=byte` (default `short`). Without it: `BufferUnderflowException` in `loadTerrain`.
- `AreaManager.java` — null-guards the missing AREA config archive (world-map areas postdate 2013).
- `WorldMapManager.java` — null-guards the missing WORLDMAP index + intermap-link clientscript.
- `MapImageDumper.java` — upstream copy with null-guards on tiles referencing overlay/underlay definitions absent from the old cache (treated as no overlay / skipped).

`pilot_render.py` (repo root) is a render-only harness for a specific cache. Inside the container:

```sh
CACHE_ID=<openrs2 cache id> MAP_FORMAT=byte \
  java -Xmx8g -Dosrs.map.format=byte \
  -cp /java/build/classes/java/main:/java/build/libs/mapimage-wrapper-1.0-all.jar ...
```

Proven on build 1 (openrs2 id 241, 2013-02-22) — renders the correct 2007-era mainland. **Not yet wired into full tile generation**; the byte↔short crossover build and mid-era validation are still open.

## Automating (future)

The pipeline auto-detects the latest openrs2 cache and the latest RuneLite version, so it is cron-friendly. If moving to GitHub Actions: public-repo runners have enough RAM (16 GB) for the 8 GB Java heap, but the millions-of-files checkout is the real bottleneck — a self-hosted runner that keeps the working tree warm avoids re-cloning every run. Have it open a PR (not push to `master`) and only when tiles actually changed.
