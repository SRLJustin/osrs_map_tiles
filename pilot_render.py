"""
Render-only pilot: fetch a SPECIFIC openrs2 cache by id and run the Java
MapImageDumper, using our shadow MapLoader so legacy (2007-engine) caches parse.

  CACHE_ID    openrs2 cache id (default 241 = build 1, 2013)
  MAP_FORMAT  "byte" (legacy, default here) or "short" (modern)

Outputs full images to /repo/cache/pilot_out/ (gitignored). Does NOT tile.
"""
import glob
import os
import subprocess
import sys

sys.path.insert(0, "/src")
import tile_generator as tg  # noqa: E402

CACHE_ID = os.environ.get("CACHE_ID", "241")
MAP_FORMAT = os.environ.get("MAP_FORMAT", "byte")
BASE = tg.CACHES_BASE_URL

out_dir = "/repo/cache/pilot_out/"
os.makedirs(out_dir, exist_ok=True)

cache_dir = os.path.join(tg.ROOT_CACHE_DIR, f"build-{CACHE_ID}/")
xtea_file = os.path.join(cache_dir, "xteas.json")

if not os.path.isdir(cache_dir):
    os.makedirs(cache_dir, exist_ok=True)
    print(f"Downloading keys for cache {CACHE_ID}")
    tg.download_xteas(f"{BASE}/caches/runescape/{CACHE_ID}/keys.json", xtea_file)
    print(f"Downloading cache {CACHE_ID}")
    tg.download_and_extract_cache(f"{BASE}/caches/runescape/{CACHE_ID}/disk.zip", cache_dir)
else:
    print("Cache dir already present, skipping download")

os.chdir("/java/build/libs")
jar = glob.glob("mapimage-wrapper-*-all.jar")[0]
# Our compiled classes must precede the jar so our MapLoader shadows the lib's.
classpath = f"/java/build/classes/java/main:/java/build/libs/{jar}"

print(f"Rendering cache {CACHE_ID} with osrs.map.format={MAP_FORMAT} ...")
subprocess.run(
    [
        "java", "-Xmx8g",
        f"-Dosrs.map.format={MAP_FORMAT}",
        "-cp", classpath,
        "org.explv.mapimage.Main",
        "--cachedir", cache_dir,
        "--xteapath", xtea_file,
        "--outputdir", out_dir,
        "--renderLabels", "false",
    ],
    check=True,
)

print("=== RENDER RESULTS ===")
for p in range(tg.MIN_Z, tg.MAX_Z + 1):
    f = os.path.join(out_dir, f"img-{p}.png")
    if os.path.exists(f):
        print(f"PLANE {p}: OK  {os.path.getsize(f)} bytes  {f}")
    else:
        print(f"PLANE {p}: MISSING")
