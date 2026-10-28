"""Compile the approved recipe plan into Forge 1.20.1 builtin server datapacks.

Run with --check to verify generated resources without changing the workspace.
The standalone mod resources are only read; all output stays under recipe_profiles.
"""

from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLAN = ROOT / "scripts/gt-recipe-plan.json"
GTO_PLAN = ROOT / "scripts/gto-workstation-plan.json"
OUTPUT = ROOT / "src/main/resources/recipe_profiles"
PACKS = ("gtm", "gtl", "gt_legacy", "gt_modern", "gt_gto")
WORKSTATION_TYPES = {"neoecoae:integrated_working_station", "neoecoae:large_integrated_working_station"}
# GTO pack config: GTM euToFeRatio 16 FE per EU, AE2 ForgeEnergy 0.5 AE per FE, so 1 EU = 8 AE.
GTO_AE_PER_EU = 8
WORKSTATION_MAX_INPUTS = 9
LARGE_WORKSTATION_MAX_INPUTS = 12
# Rows whose outputs build the integrated workstation itself; GTO uses ECO's standalone chain for them.
GTO_STANDALONE_BOOTSTRAP_KEYS = {"B01", "B03", "B09", "B10", "B11", "O01", "O02"}
GTO_CHARGED_BUDDING = (("flawless", "flawless"), ("flawed", "flawed"), ("chipped", "chipped"), ("damaged", "chipped"))
GTO_STANDALONE_BOOTSTRAP_RECIPES = (
    "neoecoae:integrated_working_station",
    "neoecoae:aluminum_alloy_casing",
    "neoecoae:aluminum_alloy_dust",
    "neoecoae:aluminum_alloy_ingot_from_dusts_aluminum_alloy_smelting",
    "neoecoae:aluminum_alloy_ingot_from_dusts_aluminum_alloy_blasting",
    "neoecoae:transform/crystal_ingot",
    "neoecoae:transform/energized_fluix_crystal",
    "neoecoae:transform/energized_superconductive_ingot",
)


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def predicate(sized: dict) -> dict:
    return {key: sized[key] for key in ("item", "tag") if key in sized}


def recipe_path(recipe_id: str) -> str:
    namespace, path = recipe_id.split(":", 1)
    return f"data/{namespace}/recipes/{path}.json"


def dependencies(row: dict, profile: str) -> list:
    conditions = [{"type": "forge:mod_loaded", "modid": "gtceu"}]
    if profile == "gtl":
        conditions.append({"type": "forge:mod_loaded", "modid": "gtlcore"})
    conditions.extend(copy.deepcopy(c) for c in row.get("conditions", []) if "proposal_profile" not in c)
    return conditions


def workstation(row: dict, profile: str) -> dict:
    result = {
        "type": "neoecoae:integrated_working_station",
        "conditions": dependencies(row, profile),
        "inputItems": copy.deepcopy(row["item_inputs"]),
        "energy": row["ae_energy"],
        "processingTicks": row["ticks"],
    }
    assert 0 < len(result["inputItems"]) <= 9
    for source, target in (("item_output", "itemOutput"), ("fluid_output", "fluidOutput"),
                           ("input_fluid", "inputFluid")):
        if source in row:
            result[target] = copy.deepcopy(row[source])
    return result


def gto_workstation(row: dict) -> dict:
    """Same materials as the GT plan; energy follows the GT duration at the pack's EU to AE rate."""
    inputs = copy.deepcopy(row["item_inputs"])
    large = len(inputs) > WORKSTATION_MAX_INPUTS
    assert 0 < len(inputs) <= LARGE_WORKSTATION_MAX_INPUTS, row["id"]
    energy = row["eu_per_tick"] * row["ticks"] * GTO_AE_PER_EU
    assert 0 < energy < 2 ** 31, row["id"]
    result = {
        "type": "neoecoae:large_integrated_working_station" if large else "neoecoae:integrated_working_station",
        "conditions": [{"type": "forge:mod_loaded", "modid": "gtceu"}],
        "inputItems": inputs,
        "energy": energy,
        "processingTicks": row["ticks"],
    }
    for source, target in (("item_output", "itemOutput"), ("fluid_output", "fluidOutput"),
                           ("input_fluid", "inputFluid")):
        if source in row:
            result[target] = copy.deepcopy(row[source])
    return result


def gt_recipe(row: dict, modern: bool) -> dict:
    def key(name: str) -> str:
        return f"gtceu:{name}" if modern else name

    def sized(item: dict) -> dict:
        return {"content": {"type": "gtceu:sized", "count": item["count"], "ingredient": predicate(item)}}

    result = {
        "type": row["recipe_type"],
        "conditions": [{"type": "forge:mod_loaded", "modid": "gtceu"}],
        "duration": row["ticks"],
        "inputs": {key("item"): [sized(item) for item in row["item_inputs"]]},
        "outputs": {key("item"): [sized(row["item_output"])]},
        "tickInputs": {key("eu"): [{"content": {"voltage": row["eu_per_tick"], "amperage": 1}
                                    if modern else row["eu_per_tick"]}]},
    }
    if "input_fluid" in row:
        fluid = row["input_fluid"]
        result["inputs"][key("fluid")] = [{"content": {
            "amount": fluid["amount"], "value": {"fluid": fluid["fluid"]}}}]
    if "blast_temperature" in row:
        result["data"] = {"ebf_temp": row["blast_temperature"]}
    if row.get("conditions"):
        assert all(set(c) == {"cleanroom"} for c in row["conditions"])
        result["recipeConditions"] = [{"type": key("cleanroom"), "cleanroom": c["cleanroom"]}
                                      for c in row["conditions"]]
    return result


def rebind(value, replacements: dict):
    if isinstance(value, dict):
        return {k: replacements.get(v, v) if k == "tag" and isinstance(v, str)
                else rebind(v, replacements) for k, v in value.items()}
    if isinstance(value, list):
        return [rebind(v, replacements) for v in value]
    return value


def compile_packs() -> dict[str, dict[str, dict]]:
    plan = read_json(PLAN)
    originals = {}
    for directory in ("src/generated/resources", "src/main/resources"):
        root = ROOT / directory / "data/neoecoae/recipes"
        if root.exists():
            originals.update({"neoecoae:" + f.relative_to(root).as_posix()[:-5]: read_json(f)
                              for f in sorted(root.rglob("*.json"))})
    native_ids = {r["replaces"] for r in plan["native_workstation_recipes"]}
    actual_native = {k for k, v in originals.items() if v.get("type") == "neoecoae:integrated_working_station"}
    # The approved 42-recipe plan predates the separately retained MEGACells resource family.
    retained_native = {k for k in actual_native if k.startswith("neoecoae:compat/megacells/")}
    assert native_ids == actual_native - retained_native, (
        f"Workstation coverage changed: {native_ids ^ (actual_native - retained_native)}")
    disabled = set(plan["disable_old_recipe_ids_when_profile_active"])
    assert disabled <= originals.keys(), f"Missing old recipes: {disabled - originals.keys()}"
    packs = {name: {"pack.mcmeta": {"pack": {"pack_format": 15,
             "description": f"NeoECOAE {name} progression recipes"}}} for name in PACKS}

    for profile in ("gtm", "gtl"):
        pack = packs[profile]
        for recipe_id in disabled:
            # Forge's empty conditional recipe explicitly removes an overridden recipe.
            pack[recipe_path(recipe_id)] = {"type": "forge:conditional", "recipes": []}
        for row in plan["native_workstation_recipes"]:
            pack[recipe_path(row["replaces"])] = workstation(row, profile)
        for row in plan["module_recipes"]:
            card = row["item_output"]["item"].split("integrated_working_station_module_", 1)[1]
            pack[recipe_path(f"neoecoae:integrated_working_station/module_{card}")] = workstation(row, profile)
        for row in plan["finished_material_conversions"]:
            material = row["item_inputs"][0]["item"].split(":", 1)[1]
            assert row["item_inputs"][0]["count"] == row["item_output"]["count"] == 1
            pack[recipe_path(f"neoecoae:compat/gt/materials/{material}")] = {
                "type": "minecraft:crafting_shapeless", "conditions": dependencies({}, profile),
                "ingredients": [predicate(row["item_inputs"][0])], "result": row["item_output"]}
        for row in plan["addon_recipe_adjustments"]:
            adjusted = copy.deepcopy(originals[row["recipe_id"]])
            assert adjusted["input_items"] == row["item_inputs_source"]
            assert adjusted["energy"] == row["ae_energy"]
            assert adjusted["output"]["#"] == row["item_output"]["count"]
            adjusted["conditions"] = dependencies(row, profile)
            adjusted["fluid"] = {"fluidStack": {"FluidName": row["input_fluid"]["fluid"],
                                                "Amount": row["input_fluid"]["amount"]}}
            pack[recipe_path(row["recipe_id"])] = adjusted
        for recipe_id, original in originals.items():
            if recipe_path(recipe_id) in pack:
                continue
            adjusted = rebind(original, plan["local_input_rebindings"])
            if adjusted != original:
                adjusted["conditions"] = dependencies(adjusted, profile)
                pack[recipe_path(recipe_id)] = adjusted
        for tag_id, members in plan["private_material_tags"].items():
            namespace, path = tag_id.split(":", 1)
            pack[f"data/{namespace}/tags/items/{path}.json"] = {
                "replace": False,
                "values": [{"id": item, "required": False} for item in members.get("item_ids", [])]
                          + ["#" + tag for tag in members.get("tag_ids", [])]}
        for metal, ids in plan["canonical_pure_metal_packing"].items():
            local = "aluminum" if metal == "aluminium" else metal
            ingots = ("neoecoae:compat/gt/aluminium_ingots" if metal == "aluminium"
                      else "forge:ingots/tungsten")
            blocks = ("neoecoae:compat/gt/aluminium_blocks" if metal == "aluminium"
                      else "forge:storage_blocks/tungsten")
            assert ids["ingots_per_block"] == 9
            pack[recipe_path(f"neoecoae:{local}_block")] = {
                "type": "minecraft:crafting_shaped", "conditions": dependencies({}, profile),
                "pattern": ["AAA", "AAA", "AAA"], "key": {"A": {"tag": ingots}},
                "result": {"item": ids["block"], "count": 1}}
            pack[recipe_path(f"neoecoae:{local}_ingot")] = {
                "type": "minecraft:crafting_shapeless", "conditions": dependencies({}, profile),
                "ingredients": [{"tag": blocks}], "result": {"item": ids["ingot"], "count": 9}}

    for section, directory in (("gt_bootstrap_recipes", "bootstrap"), ("ore_processing_recipes", "ore_processing")):
        for row in plan[section]:
            result_name = row["item_output"]["item"].split(":", 1)[1]
            path = recipe_path(f"neoecoae:compat/gt/{directory}/{row['key'].lower()}_{result_name}")
            for schema in ("legacy", "modern"):
                packs[f"gt_{schema}"][path] = gt_recipe(row, schema == "modern")
    # GTO seals its GT recipe stage and refuses addon mixins into GT classes, so GTO keeps ECO's own
    # workstations: the approved GTO materials are processed by the integrated workstations instead.
    gto = packs["gt_gto"]
    progression = read_json(GTO_PLAN)["recipes"]
    assert len(progression) == 26
    replacement_ids = {row["id"] for row in progression}
    rows = []
    for section, directory in (("gt_bootstrap_recipes", "bootstrap"), ("ore_processing_recipes", "ore_processing")):
        for row in plan[section]:
            if row["key"] in GTO_STANDALONE_BOOTSTRAP_KEYS:
                continue
            result_name = row["item_output"]["item"].split(":", 1)[1]
            row_id = f"neoecoae:compat/gt/{directory}/{row['key'].lower()}_{result_name}"
            if row_id not in replacement_ids:
                rows.append({"id": row_id, **row})
    for row in progression:
        if "copy_from" in row:
            # The infinite component is obtained from GTO's own assembly line and exchanged 1:1.
            gto[recipe_path(row["replaces"])] = {"type": "forge:conditional", "recipes": []}
        else:
            rows.append(row)
    # Omni and MEGA routes are not part of GTO; keep their workstation resources disabled.
    for recipe_id, original in originals.items():
        if original.get("type") in WORKSTATION_TYPES and any(
                marker in recipe_id for marker in ("omni_cell", "spent_nuclear_waste_cell", "compat/megacells/")):
            gto[recipe_path(recipe_id)] = {"type": "forge:conditional", "recipes": []}
    # A row takes over the existing workstation recipe for its output, e.g. B16 keeps the empty module
    # makeable in a regular workstation before any large workstation exists.
    by_output = {}
    for recipe_id, original in originals.items():
        if original.get("type") in WORKSTATION_TYPES:
            by_output.setdefault(original.get("itemOutput", {}).get("item"), []).append(recipe_id)
    for row in rows:
        output = row.get("item_output", {}).get("item")
        target = row.get("replaces") or (by_output.get(output) or [row["id"]])[0]
        gto[recipe_path(target)] = gto_workstation(row)
    # The workstation itself and its prerequisites keep ECO's standalone recipes so the chain can start.
    for recipe_id in GTO_STANDALONE_BOOTSTRAP_RECIPES:
        restored = rebind(originals[recipe_id], plan["local_input_rebindings"])
        if restored != originals[recipe_id]:
            restored["conditions"] = dependencies({}, "gtm")
        gto[recipe_path(recipe_id)] = restored
    # GTO loads only this pack (same-position packs are ordered by ID, so a separate gtm pack would win),
    # so it carries the whole GTM profile beneath the GTO replacements.
    for path, data in packs["gtm"].items():
        if path.startswith("data/"):
            gto.setdefault(path, copy.deepcopy(data))
    effective = {path: data for path, data in gto.items() if path.startswith("data/")}
    for path, original in ((recipe_path(k), v) for k, v in originals.items()):
        effective.setdefault(path, original)
    routes = {}
    for path, data in effective.items():
        if data.get("type") in WORKSTATION_TYPES:
            routes.setdefault(data.get("itemOutput", data.get("fluidOutput", {})).get("item")
                              or data.get("fluidOutput", {}).get("fluid"), []).append(path)
    for row in rows:
        output = row.get("item_output", {}).get("item") or row["fluid_output"]["fluid"]
        assert len(routes.get(output, [])) == 1, f"GTO workstation routes for {output}: {routes.get(output)}"
    assert "neoecoae:eco_infinite_cell_component" not in routes, "The infinite component is exchange-only"
    # Lightning cannot be summoned in GTO, so the AE2 charger stands in for the strike that starts
    # energized crystal growth. Same mapping as LightningBoltMixin, including damaged -> chipped.
    for source, target in GTO_CHARGED_BUDDING:
        gto[recipe_path(f"neoecoae:compat/gto/charger/{source}_budding_quartz")] = {
            "type": "ae2:charger", "conditions": [{"type": "forge:mod_loaded", "modid": "gtocore"}],
            "ingredient": {"item": f"ae2:{source}_budding_quartz"},
            "result": {"item": f"neoecoae:{target}_budding_energized_crystal"}}
    # The ECO and GTO infinite components are only exchanged 1:1 on the crafting table.
    for source, target in (("gtocore:infinite_cell_component", "neoecoae:eco_infinite_cell_component"),
                           ("neoecoae:eco_infinite_cell_component", "gtocore:infinite_cell_component")):
        name = "to_eco" if target.startswith("neoecoae:") else "to_gto"
        gto[recipe_path(f"neoecoae:compat/gto/infinite_component_{name}")] = {
            "type": "minecraft:crafting_shapeless",
            "ingredients": [{"item": source}], "result": {"item": target, "count": 1}}
    return packs


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    packs = compile_packs()
    failures = []
    for name, files in packs.items():
        root = OUTPUT / name
        actual = {p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()} if root.exists() else set()
        unexpected = actual - files.keys()
        if unexpected:
            failures.extend(f"Unexpected file: {name}/{path}" for path in sorted(unexpected))
        for relative, data in sorted(files.items()):
            path = root / relative
            text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
            if args.check:
                if not path.is_file() or path.read_text(encoding="utf-8") != text:
                    failures.append(f"Out of date: {name}/{relative}")
            else:
                if path.is_file() and path.read_text(encoding="utf-8") == text:
                    continue
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8", newline="\n")
        print(f"{name}: {len(files) - 1} resources")
    if failures:
        print("\n".join(failures))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
