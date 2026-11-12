

## Planning options

| Option | Contract |
| --- | --- |
| `ignorePatternSubstitutions` | Legacy API name for default planning, enabled by default (true); false selects the UI Deep Planning Mode. Default planning has no extra mode label: one producer per material, no route retries, substitutions or remainder reuse; physical byproducts are credited, secondary-only producers are filled once from selected patterns, and acyclic materials allow at most one supplemental expansion. Enabled cycles use the fixed-route cycle solver; disabled cycles require captured stock and verified output allocations. Marked processing inputs match by item ID without switching to full planning |
