"""Generate the GTO material-progression replacements; use --check for a read-only check.

The bootstrap recipes, material tags and charger recipes remain ordinary checked-in pack resources.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/recipe_profiles/gt_gto/data/neoecoae/recipes"


def compile_recipe(row):
    if "copy_from" in row:
        return {"type": "forge:conditional", "recipes": []}
    inputs = row["item_inputs"]
    if not 0 < len(inputs) <= 12:
        raise ValueError(f"Invalid workstation input count: {row['id']}")
    result = {
        "type": "neoecoae:large_integrated_working_station" if len(inputs) > 9
                else "neoecoae:integrated_working_station",
        "conditions": [{"type": "forge:mod_loaded", "modid": "gtceu"}],
        "inputItems": inputs,
        "energy": row["eu_per_tick"] * row["ticks"] * 8,
        "processingTicks": row["ticks"],
    }
    for source, target in [("item_output", "itemOutput"), ("fluid_output", "fluidOutput"),
                           ("input_fluid", "inputFluid")]:
        if source in row:
            result[target] = row[source]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    plan = json.loads((ROOT / "scripts/gto-workstation-plan.json").read_text(encoding="utf-8"))
    failures = []
    for row in plan["recipes"]:
        path = OUTPUT / (row["replaces"].split(":", 1)[1] + ".json")
        data = compile_recipe(row)
        if args.check:
            if not path.is_file() or json.loads(path.read_text(encoding="utf-8")) != data:
                failures.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if failures:
        print("Out of date: " + ", ".join(failures))
        return 1
    print(f"Verified {len(plan['recipes'])} GTO progression recipes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
