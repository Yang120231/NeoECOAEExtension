# Neo ECO AE Extension · Minecraft 1.20.1 GTO Edition

English | [简体中文](README_ZH_CN.md) | [繁體中文](README_ZH_HK.md) | [文言文](README_LZH.md)

Neo ECO AE Extension is an Applied Energistics 2 addon that provides expandable crafting, storage and computation systems for ME networks, alongside integrated workstations for processing advanced components.

The `1.20.1-GTO` branch is based on `1.20.1` and specifically adapts to the AE2 and GregTech interfaces used by **GregTech Odyssey (GTO)**. The current version is **`20.5.2-gto.1`**, a prerelease targeting **GTO `0.6.0-dev25` (Dev25)**. For ordinary AE2 modpacks, use this repository's `1.20.1` branch and its corresponding releases.

## Features

| System | Purpose |
| --- | --- |
| ECO Crafting System | Executes automated crafting through pattern buses, working cores and parallel cores, with batch processing, overclocking and active cooling. |
| ECO Storage System | Expands ME network capacity through storage hosts, drives and storage matrices, supporting items, fluids and optional resource types. |
| ECO Computation System | Provides crafting CPUs, job threads, co-processors and job storage, with capacity that scales with the structure. |
| Integrated Workstation | Processes energized materials, processors, storage components and advanced parts for the three systems. |
| Large Integrated Workstation | Provides batch processing, pattern automation, overclocking and cooling, plus processing recipes from supported addons. |

Crafting, storage and computation hosts each offer an automatic construction window with length and mirror settings. The Structure Terminal item is retained and displays: “Please use the build button in the corresponding host UI.”

The Large Integrated Workstation supports four categories: **regular, AdvancedAE, ExtendedAE and AE2LT**. AE2LT overload processing, lightning assembly and lightning simulation share the same workstation category. Upgrade modules are registered only when their corresponding addon mods are loaded. Other ExtendedAE Plus integrations remain available; there is no separate Plus workstation category.

## Target environment

The following combination is the target instance documented in the `20.5.2-gto.1` release notes, rather than a general compatibility range for ordinary AE2:

| Component | Target version |
| --- | --- |
| Minecraft | `1.20.1` |
| GregTech Odyssey | `0.6.0-dev25` (Dev25) |
| Forge | `47.4.20` in the instance; `47.4.28` for source builds |
| Java | **Java 21** for the game |
| GTOCore | `26.10.18` |
| GTO GTM | `26.10.80` |
| GTO AE2 | `15.2610.40` |

GuideME and LDLib are required; see the [mod metadata](src/main/resources/META-INF/mods.toml) for their declarations. Keep the dependency combination supplied by the target GTO modpack. AdvancedAE, ExtendedAE, ExtendedAE Plus and AE2LT are optional integrations and require their own dependencies when used.

This edition adapts GTO's native crafting CPU, pattern dispatch, crafting confirmation, exact amount synchronization and nested network storage interfaces. It also supports editing patterns in the Large Integrated Workstation communication interface with GTO's Pattern Modifier Pro. Workstation recipes support reloading and have EMI categories.

## Installation and usage

1. Select **`20.5.2-gto.1`** in this repository's GitHub Releases and download `neoecoae-20.5.2-gto.1.jar`.
2. Put the JAR in the target GTO instance's `mods` directory and remove older NeoECOAE JARs. The `-sources.jar` archive contains source code and is not an installable mod.
3. For multiplayer, install the same version on both client and server. The ECO network handshake requires an exact version match.
4. Start the target instance with Java 21 and use the recipe viewer to look up materials and machine recipes.

Start with energized crystals and an Integrated Workstation, then build storage, computation or crafting hosts as needed. Multiblock construction, cooling and upgrade instructions are available in the in-game GuideME guide and the English guide sources:

- [Getting started and system overview](guidebook/neoecoae_intro/index.md)
- [Integrated Workstation](guidebook/neoecoae_intro/integrated_working_station.md) and [Large Integrated Workstation](guidebook/neoecoae_intro/large_integrated_working_station.md)
- [Crafting System](guidebook/neoecoae_intro/crafting_system.md) and [illustrated crafting controller guide](guidebook/neoecoae_intro/crafting_controller.md)
- [Storage System](guidebook/neoecoae_intro/storage_system.md) and [compatible storage matrices](guidebook/neoecoae_intro/compat_storage_matrices.md)
- [Computation System](guidebook/neoecoae_intro/computation_system.md)

Interactive structure scenes, item components and recipe components in these guides require the in-game viewer.

## Building from source

You need **JDK 17, JDK 21 and Python 3**. `build-local.ps1` starts Gradle with Java 17. The GTO AE2 dependency uses Java 21 bytecode, so compilation uses a Java 21 toolchain while ECO itself targets Java 17 through `--release 17`. The game still requires Java 21 as listed above.

Run the following in PowerShell from the project root:

```powershell
# If Java 17 is not found, set this to your installed JDK 17 directory
$env:NEOECOAE_JAVA17_HOME = 'C:\path\to\jdk-17'

# First checkout: download and verify pinned dependencies and generate the local lock
python tools/compat/bootstrap.py --gto

# Build the mod and sources archives, and run the focused GTO regression tests
.\build-local.ps1 jar sourcesJar gtoCompatibilityTest --console=plain

# Check the release archive contents
python tools/compat/check_artifacts.py --release-only
```

Gradle must be able to discover JDK 21. If automatic detection fails, specify its installation directory with Gradle's `org.gradle.java.installations.paths` property. Run Gradle validation sequentially to avoid conflicts in shared outputs. Once dependencies are cached, you can add `--offline` to the build command.

Artifacts are written to `build/libs/`:

- `neoecoae-20.5.2-gto.1.jar`: the installable mod.
- `neoecoae-20.5.2-gto.1-sources.jar`: source code.

The compile dependency is currently pinned to GTO AE2 **`15.2610.31`**, as recorded in the [GTO dependency lock](docs/compat/gto-dependencies.json). Dev25 uses **`15.2610.40`** as the runtime adaptation target; the two versions are tracked separately. `bootstrap.py --gto` restores build dependencies, but does not install the full GTO modpack.

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
src/test/java/              Business and platform tests
src/gtoTest/java/           Focused GTO regression tests
guidebook/                  GuideME guides and structure scenes
docs/compat/                GTO adaptation records and dependency lock
docs/releases/              Chinese and English release notes
tools/compat/               Pinned dependency restoration and artifact checks
```

`libs/`, `build/` and `porting/` are generated or local working directories and are excluded from Git. Build dependency information for a clean checkout is provided by `tools/compat/build-dependencies.json` and `docs/compat/gto-dependencies.json`.

## Validation status and issue reports

According to the [`20.5.2-gto.1` release record](docs/releases/20.5.2-gto.1.md), **all 27 focused GTO regression tests passed without skips** after the fixes. The full Dev25 instance passed the previous startup crash point, all four crafting CPU menu bridges were confirmed to apply, and the user confirmed normal operation in that instance. See the [GTO adaptation and acceptance record](docs/compat/GTO_ADAPTATION_ZH_CN.md) for the detailed history (in Simplified Chinese).

The full legacy test suite, all addon combinations and a standalone dedicated server have not completed acceptance testing. Some legacy tests still depend on vanilla AE2 classes removed by GTO, so the build command above uses the independent `gtoCompatibilityTest` task. Its results do not establish that the full `test` or `build` tasks pass.

When reporting an issue, include the ECO version, GTO modpack version, installed GTOCore/GTM/AE2 versions, reproduction steps, and `logs/latest.log` or the relevant crash report.

## Authors and license

Authors: DancingSnow, ZhuRuoLing, Yang120231 and y271727uy.

This project is licensed under [GNU GPL v3](LICENSE).
