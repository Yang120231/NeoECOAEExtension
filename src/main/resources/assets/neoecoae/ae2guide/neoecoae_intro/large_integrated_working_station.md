---
navigation:
  title: Large Integrated Working Station
  icon: neoecoae:integrated_working_station
  position: 21
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:cryogenic_aluminum_alloy_casing
  - neoecoae:phase_change_air_cooler
  - neoecoae:large_integrated_working_station_casing
  - neoecoae:large_integrated_working_station_input_hatch
  - neoecoae:large_integrated_working_station_smart_cooling_module
  - neoecoae:large_integrated_working_station_output_hatch
  - neoecoae:large_integrated_working_station_interface
---

# Large Integrated Working Station

<GameScene interactive={true} fullWidth={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" p:formed="true" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" p:formed="true" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" p:formed="true" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="1"></Block>
</GameScene>

The Large Integrated Working Station expands an [Integrated Working Station](integrated_working_station.md) into a 3×2×2 multiblock. Once formed, it joins ME as a pattern provider and processes tens of thousands of recipe operations in parallel. This edition uses native ECO and GTO progression without addon upgrade cards.

Compared with the single-block station, the large workstation:

- accepts jobs, pulls materials and delivers products through the ME network, with no manual loading;
- runs 256 parallel operations per batch normally, and up to 262,144 once heated;

## Building the structure

<ItemGrid>
  <ItemIcon id="neoecoae:integrated_working_station" />
  <ItemIcon id="neoecoae:large_integrated_working_station_casing" />
  <ItemIcon id="neoecoae:large_integrated_working_station_interface" />
  <ItemIcon id="neoecoae:large_integrated_working_station_input_hatch" />
  <ItemIcon id="neoecoae:large_integrated_working_station_output_hatch" />
</ItemGrid>

You need 1 <ItemLink id="neoecoae:integrated_working_station" /> as the controller, 8 <ItemLink id="neoecoae:large_integrated_working_station_casing" />, 1 <ItemLink id="neoecoae:large_integrated_working_station_interface" />, 1 <ItemLink id="neoecoae:large_integrated_working_station_input_hatch" /> and 1 <ItemLink id="neoecoae:large_integrated_working_station_output_hatch" />. All parts are crafted in the Integrated Working Station; their recipes are listed at the end of this section.

The structure has a front and back row on two layers. Drag the scenes below to rotate them, and hover the colored blocks for details.

**Step 1: lower layer.** Three casings in front. In the back row, viewed from behind the structure, left to right: the **coolant output hatch, communication interface and coolant input hatch**.

<GameScene interactive={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" x="2" y="0" z="1"></Block>
  <IsometricCamera yaw="45" pitch="30" />
  <BlockAnnotation x="0" y="0" z="1" color="#ffa726">
    **Coolant output hatch**: on the left when viewed from behind; receives coolant byproducts.
  </BlockAnnotation>
  <BlockAnnotation x="1" y="0" z="1" color="#4fc3f7">
    **Communication interface**: in the middle of the back row; connects to the ME network and holds patterns.
  </BlockAnnotation>
  <BlockAnnotation x="2" y="0" z="1" color="#66bb6a">
    **Coolant input hatch**: on the right when viewed from behind; keep its pipes connected after installing smart cooling.
  </BlockAnnotation>
</GameScene>

**Step 2: upper layer.** Place the Integrated Working Station **in the middle of the upper front row**, facing outward, with one casing on each side and three casings behind it. The middle rear casing, directly behind the controller, can be replaced with a smart cooling controller.

<GameScene interactive={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="1" z="1"></Block>
  <BlockAnnotation x="1" y="1" z="0" color="#ffee58">
    **Controller**: the Integrated Working Station, in the middle of the upper front row, facing outward.
  </BlockAnnotation>
</GameScene>

A correct structure forms automatically; the casings hide and the whole machine switches to the large workstation model.

Alternatively, place only the controller, open its screen and click **Auto-build Large Integrated Working Station** on the side. If your inventory holds the materials (not needed in Creative) and nothing is in the way, every part is placed at once according to the controller's facing.

### Part recipes

<Row>
  <RecipeFor id="neoecoae:large_integrated_working_station_casing" />
  <RecipeFor id="neoecoae:large_integrated_working_station_interface" />
</Row>

<Row>
  <RecipeFor id="neoecoae:large_integrated_working_station_input_hatch" />
  <RecipeFor id="neoecoae:large_integrated_working_station_output_hatch" />
</Row>

## Connecting to the network and adding patterns

Connect a cable to the **communication interface** and the workstation appears on the ME network as a pattern provider. Open the interface and insert processing patterns that list every item and fluid input and output of the recipe. Then request crafts as with any pattern provider; materials and products move through the ME network.

Native ECO and GTO workstation recipes appear in EMI under **Large Integrated Working Station**. Recipes with up to twelve item inputs are reserved for the multiblock; the single-block workstation keeps its nine-input recipe type.

The communication interface starts with **4 rows / 36 pattern slots**. Each AE2 **Capacity Card** in the right-hand upgrade slots unlocks one more row, up to 4 cards for **8 rows / 72 slots**. Empty the last row before removing a capacity card. Patterns outside the unlocked capacity in older saves can still be retrieved, but reinstall cards to insert patterns into those rows again.

The viewport shows up to 7 rows and scrolls smoothly with the mouse wheel and draggable scrollbar, clipping partial rows at both edges. Empty the last row before removing a capacity card. Existing patterns outside the currently unlocked capacity remain retrievable when loading older saves; reinstall capacity cards to insert patterns into those rows again.

## Parallelism and temperature

Temperature mode is enabled by default: turn on overclocking and heat the machine to unlock more parallelism.

| Tier | Required temperature | Batch limit |
| --- | ---: | ---: |
| Normal | None | 256 |
| 4 | ≥ 20% | 16,384 |
| 6 | ≥ 50% | 65,536 |
| 9 | ≥ 80% | 262,144 |

Energy is paid per recipe operation actually processed: a batch of 100 operations pays for 100 recipes. Higher tiers process more at once but never reduce total energy.

The tier is read-only and follows the current temperature. **Left-click** the Overclock button to toggle overclocking. Overclocking alone generates no heat; recipes must actually be processing.

The three lower rows of the controller screen show tier and temperature, coolant and actual cooling rate, then overclock status and parallel capacity. Hover for details, including last-second processing heat, preheating, cooling and net change.

**Tier up.** New batches use the highest temperature-ready tier. Available parallelism is filled from ready tasks in the same crafting job with the same recipe and identical per-craft inputs and outputs. Fully owned, unstarted tasks for other recipes may be skipped and retain their positions in the queue. Before crossing another recipe, a simulation checks whether current power and buffered FE can pay the enlarged next processing step; otherwise the runnable smaller batch is retained. Scanning stops at another crafting job, progressed or downgraded work, blocked output or missing compatibility inputs; later compatibility inputs are never acquired ahead of another recipe. Any portion that does not fit stays on its original task. Refilling works in temperature and coolant modes, preserving paid work, fractional progress and remaining input/output ledgers across save/reload without charging twice.

**Tier down.** If temperature falls below a batch's threshold, its remaining work continues in portions of at most 256 operations, with no reheating and no double charge. Once overclocking is on and temperature returns to the original threshold, the original parallelism is restored automatically.

## Heating and preheating

Temperature ranges from **0% to 100%** and limits sustained overclocking. Successfully paid recipe progress with overclocking enabled counts as one working tick; multiple batches or processing calls within the same server tick generate heat only once. Continuous processing generates `0.65 + 3.35 × (1 − temperature / 100)²` percentage points/s, scaled by actual working ticks, **independently of batch size, multiplied patterns, recipe duration or maximum parallel capacity**. New batches still process in one tick, and small batches no longer generate negligible heat at high capacities. Normal batches below the first threshold can also warm up an overclocked machine. **Heating starts quickly and slows as temperature rises.** Disabled overclocking, idle, failed payment, missing inputs and blocked output generate no processing heat; progress already paid before saving never generates heat again.

Approximate times at 20 TPS with overclocked processing every tick and no cooling:

| Range | Time |
| --- | ---: |
| 0% → 20% | ~6 s |
| 20% → 50% | ~15 s |
| 50% → 80% | ~29 s |
| 80% → 100% | ~29 s |

**Electric preheating.** **Right-click** the Overclock button to start or stop preheating. At full power it costs 1,000 AE/t and heats twice as fast as full-load processing; a weaker supply slows it proportionally. Preheating stops at the next threshold (20% / 50% / 80%) until you right-click again, and also stops once processing advances. Reaching 20% / 50% / 80% from 0% takes about 3 / 10 / 25 seconds of preheating in total.

**Natural cooling.** Without overclocked processing or preheating, temperature falls by 0.1 percentage points per second down to 0%.

## Cooling

Reaching 100% overheats the machine, so sustained full load needs active cooling. Coolant enters through the **coolant input hatch** and byproducts go to the **coolant output hatch**; each holds 1,024,000 mB.

With **Active Cooling** enabled on the controller screen, the machine consumes 100 mB of coolant per tick (2 B per second). Active cooling is independent of overclocking: even while idle it keeps consuming and cooling while temperature is above 0% and coolant is available.

| Coolant | Cooling per second | Byproduct |
| --- | ---: | --- |
| Water | 0.1 points | None |
| Custom tier-6 coolant | 0.3 points | As specified by the cooling recipe |
| Cryotheum Solution | 0.6 points | None |

Stronger coolant removes more heat, which can also push temperature below a tier threshold:

Stronger coolant can lower temperature. At 80%, continuous overclocked processing adds **0.784 percentage points/s**; Cryotheum Solution removes 0.6, leaving a net gain of **0.184 percentage points/s**. Continuous processing with Cryotheum takes about **4 minutes 10 seconds** to go from 80% to 100%. Working for only half the ticks generates about **0.392 percentage points/s** of processing heat, with additional natural cooling during idle ticks, so the same coolant can lower temperature. Small batches processed every tick still use the continuous processing heat rate. Falling below the highest-tier requirement switches the existing task to 256-craft portions, while new batches follow the current temperature band.

With manual control, aim to keep temperature within the tier you need and below 100%. For automatic regulation, use the smart cooling controller below.

Cooling does nothing, and consumes nothing, while coolant is missing or the output hatch is full. Keep the output hatch drained when using a coolant with byproducts.

## Overheating and recovery

At **100%**, the machine locks: processing stops, no new jobs are accepted and preheating turns off. Accepted materials and progress are kept.

The only way out is forced cooling with **Cryotheum Solution**:

1. Keep Active Cooling enabled.
2. Supply Cryotheum Solution continuously and keep the output hatch clear.
3. Cool without interruption for **15 seconds**, using 30,000 mB.

Temperature then drops to 0% and the lock clears; hover the information button on the right of the controller screen to watch progress. Existing high-tier tasks resume in 256-operation portions and regain their parallelism once reheated to their original tier.

Recovery must be **uninterrupted**. Disabling cooling, running out of coolant, a blocked output, breaking the structure or reloading resets the timer to 100%. Water, sodium, other coolants, toggling overclocking or simply waiting cannot clear the lock.

## Smart cooling controller

The <ItemLink id="neoecoae:large_integrated_working_station_smart_cooling_module" /> replaces the upper casing directly behind the workstation controller. The lower coolant input hatch stays in place, and the formed model shows the cooling controller at the rear:

<GameScene interactive={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:smart_cooling="true" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" p:formed="true" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" p:formed="true" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" p:formed="true" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_smart_cooling_module" p:formed="true" p:facing="south" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="1"></Block>
  <IsometricCamera yaw="45" pitch="30" />
  <BlockAnnotation x="1" y="1" z="1" color="#66bb6a">
    **Smart cooling controller**: directly behind the workstation controller; right-click to set the target temperature. The lower input hatch keeps its pipe connection.
  </BlockAnnotation>
</GameScene>

Replace the upper rear middle casing with a **Large Integrated Working Station Smart Cooling Controller**, directly behind the workstation controller. Keep the coolant input hatch in its original position. Right-click to set a target temperature from 0 to 99% (default 75%) and mark coolant in the slot at the top right of the buffer card. Right-click with a fluid container or drag from EMI; left-click with an empty cursor to clear. Marking consumes no fluid. Both settings are saved. The module only extracts the marked fluid from the same ME network, without choosing or switching tiers automatically. Every **200 ticks (10 seconds)**, it replenishes the deficit up to its **16,384,000 mB** capacity without scanning network stock. Byproducts still go to the output hatch. The ordinary inlet feeds the controller buffer: while a coolant is marked it accepts that type, and with no mark it can run on coolant supplied directly by pipes.

The panel has no player inventory. Hold a fluid container in your main hand before opening the module, then right-click the marker slot; the container is not consumed. A container already on the cursor takes precedence.

Changing or clearing the mark returns the old fluid to ME on the next refill attempt. Rejected or partially accepted returns stay in the buffer; the new type is only extracted after all old fluid has been returned. An empty mark never extracts fluid. Older saves retain real stock but require the player to set a mark explicitly.

The module automatically heats, cools and holds its target, **including while idle, paused or waiting for processed outputs**. At a target of 80%, it gradually heats below the target and cools above it, then compensates for processing heat and natural cooling to remain at 80%. Change the target at any time; it replaces the manual preheat and cooling switches while installed. Overheating still requires uninterrupted cryotheum recovery before returning to the target. Manually mark cryotheum for recovery; the module never overrides your selection.

Each successfully regulated tick costs a fixed **100,000 AE**, including holding and idle operation. Module heating is included; recipe energy is separate, and the operating cost is charged at most once per tick. Coolant circulation consumes at least **300 mB/t**, three times an ordinary hatch, and increases as needed to remove processing heat. Byproducts scale with the actual fluid consumed. Missing power or coolant, or blocked coolant outputs, pauses active regulation without freezing natural temperature changes; regulation resumes when resources return. Existing stock is retained while ME is offline; refills resume on the normal interval after reconnection. Disabling temperature mechanics disables target regulation and retains coolant mode at 300 mB per tick of actual overclocked progress.

Craft the smart cooling controller in the Large Integrated Working Station using 12 ingredient types and 512 buckets of Cryotheum Solution. See EMI for the full ingredient list.



The server option `smartCoolingRefillThresholdPercent` defaults to `100`, topping up on every poll. A lower value refills only below that stock percentage; the interval remains 200 ticks.

<RecipeFor id="neoecoae:large_integrated_working_station_smart_cooling_module" />

## Disabling temperature: coolant mode

The server option `largeWorkstationTemperatureEnabled` defaults to `true`. Set it to `false` and reopen the world or restart the server to use **coolant mode**: no heating, no overheating, and the batch limit comes directly from the coolant.

| Coolant | Batch limit |
| --- | ---: |
| None, or overclocking and active cooling not both enabled | 256 |
| Water | 16,384 |
| Custom tier-6 coolant | 65,536 |
| Cryotheum Solution | 262,144 |

Both **Overclock** and **Active Cooling** must be on. An overclocked batch consumes 100 mB of coolant per processing tick that actually advances (300 mB with the smart cooling controller); idle, unpowered and paused machines consume none. Insufficient coolant, a coolant tier below the batch's requirement or blocked byproducts pause the batch without losing materials or progress. Higher-tier coolant can keep lower-tier batches running.

Switching modes never discards accepted batches. Saved temperature and overheat state are kept while disabled and checked again when temperature mode returns, so an overheated machine still needs Cryotheum recovery.

## Processing time and energy

- **Processing time.** Newly accepted batches currently process in **one tick**. Parallelism determines simultaneous operations, and total per-craft costs stay unchanged. EMI retain the converted source-recipe duration as a reference rather than the workstation's actual runtime; the source duration's 200-tick cap do not limit new batches. Multiplied patterns scale materials and costs proportionally; new batches still process in one tick. Waiting for output space does not charge processing energy; energy or cooling shortages preserve progress. Previously accepted tasks retain their recorded duration, costs and paid progress without being repriced during processing.

## Custom cooling recipes in modpacks

The large workstation maps a cooling recipe's `max_overclock` capability to an existing band: **2–5 selects band 2, 6–8 selects band 6, and 9 or higher selects band 9**. Values below 2 cannot provide overclock cooling. For example, cryotheum with a custom capability of 15 still dissipates heat and supports up to 262,144 parallel crafts in coolant mode. Temperature mode still determines parallelism from temperature. This mapping applies only to the large workstation; other crafting systems keep the recipe's original capability and coolant value.

The Active Cooling tooltip shows both the recipe's maximum overclock and the effective workstation cooling band. Byproducts follow the custom recipe's input/output ratio. Insufficient output space prevents coolant consumption; in coolant mode, the batch retains its inputs and progress until output space becomes available. Custom capabilities do not increase the existing bands' cooling rates or change the requirement for 15 seconds of uninterrupted cryotheum cooling to recover from overheating.


## GTO recipes and energy

This edition uses native ECO and GTO progression. The large workstation needs no addon upgrade card and supports up to 12 item inputs. GTO processing energy follows the recipe duration at 1 EU = 8 AE. Existing tasks retain their materials, progress and payment records.


The smart controller uses the existing AE2 Import Bus as its network-pull component. Its remaining material and coolant requirements follow the workstation recipe.

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Blocks are placed but the structure doesn't form | Check that the controller is in the middle of the upper front row and that the lower back row reads "output hatch, interface, input hatch" from behind; or use auto-build |
| Overclocking is on but capacity is still 256 | Temperature is below 20%; let recipes run or right-click Overclock to preheat |
| A task only uses 256 after cooling down | Temporary portion mode; it recovers once overclocked and back at its original threshold |
| Temperature keeps rising with cooling on | Heating exceeds cooling; use a stronger coolant or check the coolant level and output hatch |
| With Cryotheum, temperature keeps falling and drops a tier | Cooling exceeds heating; use a weaker coolant or the smart cooling controller |
| Overheat recovery doesn't advance or resets | Supply real Cryotheum Solution with Active Cooling on and a clear output hatch, without interruptions or reloads |
