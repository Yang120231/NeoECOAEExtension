"""Restore the public, hash-pinned compile dependencies for a clean CI checkout."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def restore(root=ROOT):
    lock = json.loads((root / "tools/compat/build-dependencies.json").read_text())
    generated = root / "porting/dependencies.lock.json"
    if generated.exists():
        previous = {row['mod_id']: row for row in json.loads(generated.read_text())['artifacts']}
        for row in lock['artifacts']:
            if row['mod_id'] in previous and previous[row['mod_id']]['sha256'] != row['sha256']:
                raise ValueError(f"Existing dependency hash differs: {row['mod_id']}")
    destination = root / "libs/pinned"
    destination.mkdir(parents=True, exist_ok=True)
    by_id = {row["mod_id"]: row for row in lock["artifacts"]}

    def obtain(row):
        path = destination / row["filename"]
        if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == row["sha256"]:
            return
        if "embedded" in row:
            parent = destination / by_id[row["embedded"]["parent"]]["filename"]
            with zipfile.ZipFile(parent) as archive:
                data = archive.read(row["embedded"]["path"])
        else:
            request = urllib.request.Request(row["url"], headers={"User-Agent": "NeoECO-compat/1"})
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read()
        if hashlib.sha256(data).hexdigest() != row["sha256"]:
            raise ValueError(f"Build dependency hash mismatch: {row['mod_id']}")
        partial = path.with_suffix(".part")
        partial.write_bytes(data)
        partial.replace(path)

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(obtain, [row for row in lock["artifacts"] if "embedded" not in row]))
    for row in lock["artifacts"]:
        if "embedded" in row:
            obtain(row)
    gto_lock = json.loads((root / "porting/gto-dependencies.json").read_text())
    obtain(gto_lock["ae2"])
    porting = root / "porting"
    porting.mkdir(exist_ok=True)
    generated.write_text(json.dumps(lock, indent=2) + "\n")
    print(f"Verified {len(lock['artifacts'])} infrastructure dependencies and GTO AE2")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gto', action='store_true', help='Compatibility flag; GTO AE2 is always restored')
    parser.parse_args()
    restore()
