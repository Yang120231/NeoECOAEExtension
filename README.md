# Neo ECO AE Extension · Minecraft 1.20.1 GTO Edition

English | [简体中文](README_ZH_CN.md) | [繁體中文](README_ZH_HK.md) | [文言文](README_LZH.md)

Neo ECO AE Extension is an Applied Energistics 2 addon that provides expandable crafting, storage and computation systems for ME networks, alongside integrated workstations for processing advanced components.

The `1.20.1-GTO` branch is based on `1.20.1` and targets **GregTech Odyssey (GTO)** only. The current source version is **`20.5.8-GTO`**, and only **GregTech-Odyssey `0.6.0 dev30` and newer** are supported. For ordinary AE2 modpacks, use the `1.20.1` branch.

## Features

| System | Purpose |
| --- | --- |
| ECO Crafting System | Executes automated crafting through pattern buses, working cores and parallel cores, with batch processing, overclocking and active cooling. |
| ECO Storage System | Expands ME network capacity through storage hosts, drives and storage matrices, supporting items and fluids. |
| ECO Computation System | Provides crafting CPUs, job threads, co-processors and job storage, with capacity that scales with the structure. |
| Integrated Workstation | Processes energized materials, processors, storage components and advanced parts for the three systems. |
| Large Integrated Workstation | Provides batch processing, pattern automation, overclocking and cooling, using native ECO and GTO recipes. |

Crafting, storage and computation hosts each offer an automatic construction window with length and mirror settings. The Structure Terminal item is retained and displays: “Please use the build button in the corresponding host UI.”

The Large Integrated Workstation uses native ECO and GTO recipes without addon upgrade modules.

## Target environment

The following table lists the supported environment and pinned build dependencies:

| Component | Target version |
| --- | --- |
| Minecraft | `1.20.1` |
| GregTech Odyssey | `0.6.0 dev30+` |
| Forge | `47.4.20` |
| Java | **Java 21** for the game |
| GTOCore | `26.11.6` |
| GTO GTCEu | `26.11.20` |
| GTO AE2 | `15.2611.8` |

GuideME and LDLib are required infrastructure dependencies; see the [mod metadata](src/main/resources/META-INF/mods.toml). This edition retains only GTO integration.

This edition adapts GTO's native crafting CPU, pattern-buffer batch submission, capacity checks, programmed circuits, machine progress, CPU-list synchronization, exact amounts and nested storage. GTO Pattern Modifier Pro can edit communication-interface patterns, and workstation recipes support reloading.

## Installation and usage

1. Download the matching release from GitHub Releases, or build `neoecoae-20.5.8-GTO.jar` from the current source as described below.
2. Put the JAR in the target GTO instance's `mods` directory and remove older NeoECOAE JARs. The `-sources.jar` archive contains source code and is not an installable mod.
3. For multiplayer, install the same version on both client and server. The ECO network handshake requires an exact version match.
4. Start the target instance with Java 21 and use the recipe viewer to look up materials and machine recipes.

Start with energized crystals and an Integrated Workstation, then build storage, computation or crafting hosts as needed. Multiblock construction, cooling and upgrade instructions are available in the in-game GuideME guide and the English guide sources:

- [Getting started and system overview](guidebook/neoecoae_intro/index.md)
- [Integrated Workstation](guidebook/neoecoae_intro/integrated_working_station.md) and [Large Integrated Workstation](guidebook/neoecoae_intro/large_integrated_working_station.md)
- [Crafting System](guidebook/neoecoae_intro/crafting_system.md) and [illustrated crafting controller guide](guidebook/neoecoae_intro/crafting_controller.md)
- [Computation System](guidebook/neoecoae_intro/computation_system.md)

Interactive structure scenes, item components and recipe components in these guides require the in-game viewer.

## Building from source

You need **JDK 17, JDK 21 and Python 3.11 or newer**. `build-local.ps1` starts Gradle with Java 17. The GTO AE2 dependency uses Java 21 bytecode, so compilation uses a Java 21 toolchain while ECO itself targets Java 17 through `--release 17`. The game still requires Java 21 as listed above.

Run the following in PowerShell from the project root:

```powershell
# If Java 17 is not found, set this to your installed JDK 17 directory
$env:NEOECOAE_JAVA17_HOME = 'C:\path\to\jdk-17'

# First checkout: download and verify pinned dependencies and generate the local lock
python tools/compat/bootstrap.py --gto

# Configure the matching local GTOLib development JAR in ignored local.properties:
# gtolibLocalJar=C:/private/build/gtolib/gtolib-local-compile.jar

# Build the mod and sources archives
.\build-local.ps1 jar sourcesJar --console=plain

# Check the release archive contents
python tools/compat/check_artifacts.py --release-only --with-sources
```

Gradle must be able to discover JDK 21. If automatic detection fails, specify its installation directory with Gradle's `org.gradle.java.installations.paths` property. Run Gradle validation sequentially to avoid conflicts in shared outputs. Once dependencies are cached, you can add `--offline` to the build command.

Artifacts are written to `build/libs/`:

- `neoecoae-20.5.8-GTO.jar`: the installable mod.
- `neoecoae-20.5.8-GTO-sources.jar`: source code.

Compilation and native-JAR checks use GTO AE2 **`15.2611.8`**; see the [GTO dependency lock](porting/gto-dependencies.json). `bootstrap.py --gto` restores build dependencies and does not install the complete modpack.

### Local builds and Release verification

The GTOLib development dependency is for local compilation only. Never commit, upload, or bundle it in either ECO archive. Public CI does not run Gradle or download/build GTOLib.

1. Build locally and run the release and sources archive checker above.
2. Push the matching ECO source and tag. The tag must equal `mod_version` in `gradle.properties`.
3. Create a draft Release for that tag, write its release notes, and upload only `build/libs/neoecoae-<version>.jar` and `build/libs/neoecoae-<version>-sources.jar`.
4. Publish the Release. CI downloads both assets, checks their version, source contents and absence of private content, then attaches `SHA256SUMS.txt` and saves verification reports.

Missing or invalid assets fail verification; CI does not rebuild them. After correcting the assets, rerun the failed workflow or manually run `Verify Local Release Artifacts` in Actions with the published tag. Verification does not retract a published Release or establish in-game compatibility.

## Repository layout

```text
src/main/java/cn/dancingsnow/neoecoae/
  api/                      ECO business and extension contracts
  blocks/                   Machines, interfaces and block entities
  crafting/                 Crafting planning and execution
  impl/storage/             Storage and exact amount handling
  compat/                   AE2, GTO and optional addon compatibility
  gui/                      Host pages and construction windows
  platform/                 Forge 1.20.1 platform adapters
src/main/resources/         Mod metadata, assets and recipes
guidebook/                  GuideME guides and structure scenes
docs/compat/                GTO adaptation records and dependency lock
docs/releases/              Chinese and English release notes
tools/compat/               Pinned dependency restoration and artifact checks
```

`libs/`, `build/` and `porting/` are generated or local working directories and are excluded from Git. Build dependency information for a clean checkout is provided by `tools/compat/build-dependencies.json` and `porting/gto-dependencies.json`. The pinned GTO lock `porting/gto-dependencies.json` is tracked; other files in `porting/` remain local.

## Validation status and issue reports

Historical test results for `20.5.4-gto.1` are recorded in its [release notes](docs/releases/20.5.4-gto.1.md). Automated test sources and custom Gradle test tasks have been removed. Use `jar sourcesJar` and the artifact checker for build validation; Minecraft client/server startup and real interactions require separate acceptance.

When reporting an issue, include the ECO version, GTO modpack version, installed GTOCore/GTM/AE2 versions, reproduction steps, and `logs/latest.log` or the relevant crash report.

## Authors and license

Authors: DancingSnow, ZhuRuoLing, Yang120231 and y271727uy.

This project is licensed under [GNU GPL v3](LICENSE).
