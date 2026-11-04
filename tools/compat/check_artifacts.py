"""Check the GTO release JAR identity and reject bundled third-party classes or test fixtures."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-only", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--build-directory", type=Path, default=ROOT / "build")
    args = parser.parse_args()
    version = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines()
                   if line.startswith("mod_version="))
    path = args.build_directory / "libs" / f"neoecoae-{version}.jar"
    with zipfile.ZipFile(path) as archive:
        entries = archive.namelist()
        metadata = archive.read("META-INF/mods.toml").decode()
        if 'modId="neoecoae"' not in metadata or 'modId="gtocore"' not in metadata:
            raise ValueError("Release is missing its GTO identity/dependency")
        for entry in entries:
            if entry.endswith(".class") and not entry.startswith("cn/dancingsnow/neoecoae/"):
                raise ValueError(f"Third-party class or test fixture bundled in release: {entry}")
            if "/compatprobe/" in entry:
                raise ValueError(f"Test probe bundled in release: {entry}")
            if not entry.endswith("/") and entry.startswith(("assets/", "data/", "recipe_profiles/", "classic_pack/")):
                if not any((ROOT / source / entry).is_file() for source in ("src/main/resources", "src/generated/resources")):
                    raise ValueError(f"Stale resource bundled in release: {entry}")
    result = {"file": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "entries": len(entries)}
    destination = args.build_directory / "compat-build-evidence"
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "artifacts.json").write_text(json.dumps([result], indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
