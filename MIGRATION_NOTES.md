# Migration Notes — NeoECOAEExtension

## Forge 1.20.1 port cleanup

**Date**: 2026-05-30  
**Branch**: main (target: Minecraft 1.20.1 / Forge 47.x)

### Background

The current `main` branch was ported from a newer codebase targeting NeoForge 1.21.1+.
During initial porting, several packages were retained but excluded from compilation via
`build.gradle`'s `forge120JavaExcludes`. This document records the cleanup decisions.

### Removed from `src/main/java` (moved to `legacy/1.21-porting/`)

| Original path | Reason | Replacement |
|---|---|---|
| `integration/**` (20 files) | 1.21.x NeoForge porting reference code using APIs not available in Forge 1.20.1 (`RecipeHolder`, `NeoForgeTypes`, `RegistryFriendlyByteBuf`, `RecipeOutput`, etc.) | `compat/jei/`, `compat/emi/`, `compat/appmek/` (Forge 1.20.1 compatible) |

### Still excluded from Forge 1.20.1 compilation

| Package/file | Reason |
|---|---|
| `mixins/**` | Contains 1.20.5+ APIs (`RegistryFriendlyByteBuf`) and references to absent mods (ExtendedAE) |
| `client/model/**` | GeckoLib-based model code not yet migrated |
| `data/**` | 1.21 datagen code |
| `recipe/*Builder.java` | 1.21 recipe builder API |
| `all/NEDataComponents.java` | 1.20.5+ DataComponent API |
| `api/components/**` | 1.20.5+ component API |

### Notes for future NeoForge 1.21.1 branch

If a NeoForge 1.21.1+ branch is created:
- Restore `integration/**` from `legacy/1.21-porting/`
- Re-evaluate all other excludes for NeoForge compatibility
- Do NOT merge Forge 1.20.1-specific code (`compat/**`, `forge/mixin/**`) into the NeoForge branch
