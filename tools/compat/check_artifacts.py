"""Check the GTO release JAR identity and reject bundled third-party classes or test fixtures."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def reject_private_content(archive, label, depth=0):
    if depth > 4:
        raise ValueError(f"Too many nested archives: {label}")
    for entry in archive.namelist():
        normalized = entry.replace("\\", "/").lower()
        if ("gtolib" in normalized or normalized.startswith("native0/")
                or normalized.endswith((".prod.bin", ".dev.bin"))
                or normalized.rsplit("/", 1)[-1] == "local.properties"):
            raise ValueError(f"Private GTOLib content or local configuration bundled in {label}: {entry}")
        if normalized.endswith((".jar", ".zip")):
            with zipfile.ZipFile(io.BytesIO(archive.read(entry))) as nested:
                reject_private_content(nested, f"{label}!{entry}", depth + 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-only", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--build-directory", type=Path, default=ROOT / "build")
    parser.add_argument("--with-sources", action="store_true", help="Also verify the matching ECO sources archive")
    args = parser.parse_args()
    version = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines()
                   if line.startswith("mod_version="))
    path = args.build_directory / "libs" / f"neoecoae-{version}.jar"
    with zipfile.ZipFile(path) as archive:
        reject_private_content(archive, path.name)
        if archive.testzip() is not None:
            raise ValueError("Corrupt release archive")
        entries = archive.namelist()
        metadata = archive.read("META-INF/mods.toml").decode()
        if 'modId="neoecoae"' not in metadata or 'modId="gtocore"' not in metadata:
            raise ValueError("Release is missing its GTO identity/dependency")
        mods = tomllib.loads(metadata).get("mods", [])
        if not any(mod.get("modId") == "neoecoae" and mod.get("version") == version for mod in mods):
            raise ValueError("Release JAR version must match mod_version")
        for entry in entries:
            if entry.endswith(".class") and not entry.startswith("cn/dancingsnow/neoecoae/"):
                raise ValueError(f"Third-party class or test fixture bundled in release: {entry}")
            if "/compatprobe/" in entry:
                raise ValueError(f"Test probe bundled in release: {entry}")
            if not entry.endswith("/") and entry.startswith(("assets/", "data/", "recipe_profiles/", "classic_pack/")):
                if not any((ROOT / source / entry).is_file() for source in ("src/main/resources", "src/generated/resources")):
                    raise ValueError(f"Stale resource bundled in release: {entry}")
    result = {"file": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "entries": len(entries)}
    results = [result]
    if args.with_sources:
        sources = path.with_name(f"neoecoae-{version}-sources.jar")
        with zipfile.ZipFile(sources) as archive:
            reject_private_content(archive, sources.name)
            if archive.testzip() is not None:
                raise ValueError("Corrupt sources archive")
            source_entries = archive.namelist()
            expected_sources = {file.relative_to(ROOT / "src/main/java").as_posix(): file
                                for file in (ROOT / "src/main/java").rglob("*.java")}
            actual_sources = {entry for entry in source_entries if entry.endswith(".java")}
            if actual_sources != expected_sources.keys():
                raise ValueError("Sources archive Java files do not match the release source tree")
            for entry, source in expected_sources.items():
                if archive.read(entry).replace(b"\r\n", b"\n") != source.read_bytes().replace(b"\r\n", b"\n"):
                    raise ValueError(f"Sources archive differs from release source: {entry}")
        results.append({"file": sources.name, "sha256": hashlib.sha256(sources.read_bytes()).hexdigest(),
                        "entries": len(source_entries)})
    destination = args.build_directory / "compat-build-evidence"
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "artifacts.json").write_text(json.dumps(results, indent=2) + "\n")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
