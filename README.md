# Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
# MultiFeature Client Mod — Fabric 26.2 / 26.3, Java 25, Official Mappings

Simple, low-RAM (~20 MB goal) client-only Fabric mod. Singleplayer or private servers only.
No anti-cheat bypasses. No GUI. No Kotlin. No reflection. No threads.

> Mixin status: targets were validated against Mojang 1.21.11 official mappings
> on 2026-10-06, **not** against 26.x — the 26.2/26.3 jars do not exist publicly
> yet. Every retargeted method carries a `// TBC on 26.x` marker. Do not assume
> any mixin name is confirmed for 26.x until you run the Verification section below.

## Features

| Feature | Default key | Notes |
|---|---|---|
| KillAura + Reach | `R` | Per-entity targeting map (GUI tab 1): OFF / ALWAYS / WHEN_ANGERED per registry id, absent = OFF. Players via playerMode (OFF / ALL / BY_NAME list). Closest eligible in reach, one LOS raycast on winner (walled = skip), silent rot, no artificial delay (cooldown-gated max rate). Non-living targets (boats, minecarts, armor stands…) eligible with OFF / ALWAYS. |
| Menu (GUI) | `K` | Opens MultiFeatureScreen (Targeting + Keybinds tabs). Also `/kapgui`, `/KVpp3La` (`/kvpp3la` alias). |
| AutoCrit (no jump) | config `autoCritEnabled` | Four-packet fall spoof per attack (Wurst-style, randomized `critFallMin`–`critFallMax`, default 0.11–0.17). Capture-once position, no restore packet, armed-flag pairing. See note below. |
| Fullbright | `G` | Gamma tick (restores vanilla on toggle-off) + `GameRenderer.getNightVisionScale → 1.0` mixin (static injector) for shader path. |
| AutoTotem | `O` | Restocks offhand via vanilla `handleInventoryMouseClick` PICKUP clicks, `totemDelayMs` + 0–50 ms jitter. |
| Low Fire | config | `lowFirePercent` 0–100 (default 60). 0 = cancel via `ScreenEffectRenderer.renderFire`; 1–99 needs a pose-scale patch inside that method — see `FireOverlayMixin` comment. |
| No Pumpkin / No Frost | config | Single `Gui.renderTextureOverlay` filter on texture path (`pumpkin…` / `powdersnow…`). |
| No Fog | config | `fog.FogRenderer.setupFog` HEAD-cancel. Behavior check mandatory (stale-UBO risk) — see Verification. |
| Clear Water | config | Cancels `ScreenEffectRenderer.renderWater`. |
| Clear Lava | config | Opt-in (default off). Filters `ScreenEffectRenderer.renderTex` on lava sprites. Lava routing is the least certain target — in-game check mandatory. |
| Low Shield / Item Scaling | config | `ItemInHandRenderer.renderArmWithItem` push/scale/pop; shield offset is OFF_HAND-only. Zero cost when both toggles off. |
| Night Vision Cleanup | config | Cancels `Gui.renderVignette` (note: `(GuiGraphics, Entity)` descriptor) when fullbright is on. |

Action-bar message confirms each toggle.

### AutoCrit — four-packet sequence, random fall range, honest caveat

Each standing attack sends four `ServerboundMovePlayerPacket.Pos` in the same
tick: up by a fresh `fallDelta`, drop back, a `1.1E-5` nudge, settle — producing
a server-visible fall of ~`fallDelta` at attack time. `fallDelta` is drawn
uniform per hit from [`critFallMin`, `critFallMax`] (defaults 0.11–0.17): above
the common 0.1 server-side crit-cancellation threshold that kills the old 0.001
trick, below a real jump, and never a constant fingerprint value. Position is
captured once and reused across all four packets; there is no restore packet
(the sequence ends at the real position, the next movement tick corrects
`onGround`). Caveat: this is a client-side technique — movement-prediction
anti-cheats (GrimAC, Intave, HawkAC, …) simulate the fall server-side and may
reject or flag it. Randomization defeats naive constant-value matching only;
it is not undetectable. Singleplayer/private servers only.

## Project layout

```
build.gradle / settings.gradle / gradle.properties  (Loom 1.17+, Gradle 9.6.0, Java 25)
gradle/wrapper/gradle-wrapper.jar  (real wrapper, 9.6.0)
src/client/java/com/example/multifeature/
  MultiFeatureMod.java      ClientModInitializer
  TickHandler.java          single END_CLIENT_TICK entry
  config/ModConfig.java     Gson POJO, primitives only
  keybind/KeybindManager.java  R/G/O
  feature/KillAura.java, AutoCrit.java, AutoTotem.java, Fullbright.java
  mixin/LightmapMixin.java, InGameHudMixin.java, FireOverlayMixin.java,
        BackgroundRendererMixin.java, ItemRenderMixin.java,
        WaterVisionMixin.java, LavaVisionMixin.java
src/client/resources/
  fabric.mod.json  (environment=client, minimal API deps, MC version PINNED per target)
  multifeaturemod.mixins.json  (defaultRequire=1: target drift fails fast, never silent)
```

Low-RAM rules enforced: scan every `scanIntervalTicks` only when cooldown full, one AABB per scan, plain `for` loops, no streams/lambdas/`Optional` in tick, one raycast per attack (not per candidate), no logging in tick, mixins early-return when toggle off. Per-scan allocs are bounded and documented in `KillAura.java` (eye Vec3, scanBox, MC entity list).

## Setup

1. Install Java 25 (Temurin/Oracle). This scaffold was built on a machine with Java 21 — set `JAVA_HOME` to JDK 25; Gradle toolchains can also auto-provision 25.
2. Gradle comes via the wrapper (`gradlew` / `gradlew.bat`, 9.6.0, real jar in `gradle/wrapper/`).
   **Slow-link note:** the first `./gradlew` run downloads the ~130 MB Gradle 9.6.0
   distribution. On a slow connection the wrapper times out (`Connect timed out`
   / `SocketTimeoutException`) — this is a network issue, not a wrapper issue.
   Just retry, or run once on a faster connection; after the distribution is cached
   in `~/.gradle/wrapper/dists` no download is needed. (`networkTimeout=120000` is
   already set in `gradle/wrapper/gradle-wrapper.properties`.)
3. Verify latest versions for 26.x (do not exist publicly as of Oct 2026 — check before building):
   - https://fabricmc.net/develop → Loader `0.19.3+` (26.2), `0.19.5+` (26.3)
   - https://modrinth.com/mod/fabric-api/versions → `0.160.0+` / `0.160.5+`
   - Loom `1.17+`: https://fabricmc.net/develop
4. Edit `gradle.properties`: set `minecraft_version=26.2` or `26.3` (+ matching loader/fabric lines).
   **One jar per target.** `fabric.mod.json` pins `minecraft` to `~26.2` (per-build).
   Do NOT widen to `>=26.2 <26.4` without ABI evidence — the 2026-10-06 audit
   proved Mojang moves/renames rendering classes between minor versions
   (`FogRenderer` package move, `renderItem` descriptor change). Build twice.
5. If granular API artifacts fail to resolve for 26.x, replace the 3 `modImplementation` lines in `build.gradle` with:
   `modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"`
6. Mappings: this project uses **official Mojang mappings** (`loom.officialMojangMappings()`). Source uses Mojang names (`Gui`, `ServerboundMovePlayerPacket`, `ItemInHandRenderer`, `fog.FogRenderer`, `ScreenEffectRenderer`, `KeyMapping`). Do not switch to Yarn.
7. Build: `./gradlew build` (or `gradlew.bat build` on Windows). Jar in `build/libs/`.
8. Run client: `./gradlew runClient` (assets download on first run).
9. Install: drop jar in `.minecraft/mods/` with matching Loader + Fabric API. Client-side only — no server install.

## Config

File: `config/multifeature.json` (created on first run). All primitives. Edit while game closed, or toggle in-game (R/G/O auto-saves).

```json
{
  "killAuraEnabled": false,
  "reach": 3.0,
  "scanIntervalTicks": 3,
  "silentRotation": true,
  "killAuraCheckLOS": true,
  "targeting": {
    "minecraft:zombie": "ALWAYS",
    "minecraft:zombified_piglin": "WHEN_ANGERED"
  },
  "playerMode": "OFF",
  "playerNames": [],
  "autoCritEnabled": true,
  "critFallMin": 0.11,
  "critFallMax": 0.17,
  "fullbrightEnabled": false,
  "fullbrightGamma": 10.0,
  "autoTotemEnabled": false,
  "totemDelayMs": 120,
  "lowFireEnabled": true,
  "lowFirePercent": 60,
  "noPumpkin": true,
  "noFrost": true,
  "clearWater": true,
  "clearLava": false,
  "lowShield": true,
  "noFog": true,
  "nightVisionCleanup": true,
  "itemScaling": false,
  "itemScale": 1.0
}
```

## GUI, Commands & Targeting

Press `K`, or type `/kapgui` or `/KVpp3La` (`/kvpp3la` alias works too — Brigadier
literals are case-sensitive, so both cases are registered; no other variants).
All three open the same `MultiFeatureScreen` (Targeting tab default), silently —
no chat feedback. If a server defines its own `/kapgui`, the client registration
wins for locally typed text: the screen opens instead of the command reaching
the server. There is no ambiguity to resolve in-game — client commands
intercept before anything is sent.

### Targeting tab

Entity tree is enumerated at Screen-open time from
`BuiltInRegistries.ENTITY_TYPE` — every registered entity, including modded
ones, grouped Hostile / Neutral / Passive / Utility / Other (NeutralMob
override first, then `MobCategory`; Hostile+Neutral expanded by default).
`EntityType.PLAYER` is excluded from the tree — players have their own section.
Within a category rows sort alphabetically by translated name. Nothing is
attacked until opted in: every entity defaults OFF and the file records
explicit OFF entries for user choices.

Per-entity button cycles OFF → ALWAYS → WHEN_ANGERED (anger-capable only) →
OFF. WHEN_ANGERED attacks only while the entity's attack target is you
(`Mob.getTarget()`, no mixin needed). Note on semantics: that means *current
attack target*, not persistent anger state — a neutral mob briefly between
targets, or targeting something else while still angry at you, reads as
not-angered for that tick. The label is not oversold; the check is exactly
`getTarget() == player`.
Anger-capable types (javap-verified on 1.21.11 — re-verify on 26.x, single
list in `TargetMode`): wolf, iron_golem, bee, polar_bear, enderman,
zombified_piglin. Correction notice: the original assumed list also included
llama, panda, goat, piglin, dolphin and spider — javap against the 1.21.11
Mojang jar shows none of those implement NeutralMob (brain/lunge hostility
instead), so they are deliberately NOT gated. If you hold the old spec,
the README is right and the spec was wrong. Config accepts all three values
defensively; KillAura treats WHEN_ANGERED on a non-anger entity as OFF with a
once-log. Non-living entities (boats, minecarts, falling sand/gravel, item
frames, armor stands, primed TNT, …) appear with OFF / ALWAYS only.

Search filters at 250 ms debounce (no per-keystroke rebuild). Bulk row:
three "Visible→…" buttons act on expanded + filtered rows; "ALL→ALWAYS" hits
every registered entity and needs a two-click confirm (3 s window, any other
click or timeout reverts, status line confirms the count for ~2 s).

### Wall penetration (checkLOS)

Targeting tab, checkbox above the bulk row: `[X] Check line of sight`
(Wurst's tooltip wording: "Ensures that you don't reach through blocks when
attacking."). Global for the whole kill aura, not per-entity. Persists as
`killAuraCheckLOS` (default `true` = vanilla-safe, zero behavior change).
When off, the LOS raycast is skipped entirely — one fewer allocation per
attack than the checked path — and walled targets are attacked. Reach and
checkLOS are independent settings that compound: off + 3-block reach hits
the zombie behind a one-block pillar; off + 7-block reach is close to full
Wurst behavior (their slider maxes at 10; this mod's cap is unchanged).

Honest caveats:

- **Vanilla servers**: attacks through walls will land. The server does not
  check LOS for entity attacks; it only checks distance and cooldown.
- **Servers with anti-cheats that validate LOS** (HawkAC, some Grim
  configurations, most modern combat anti-cheats): the attack will be rejected
  or flagged. The client sends the packet; the server decides.
- **This is not an anti-cheat bypass.** It is a client-side removal of a
  client-side check. The server's opinion is unaffected.

### Player rules

`playerMode`: OFF / ALL / BY_NAME (button above the tree). BY_NAME reveals an
independently-scrolling names list: text field + Add, 1–16 chars,
case-insensitive dedup (duplicate flashes the existing row), no length cap.
Empty list + BY_NAME behaves as OFF. Names persist as `playerNames`
(order + case preserved, matched case-insensitively).

### Keybind rebinding tab

Rows for all four binds (R/G/O/K): click → "Press a key..." → next press
rebinds (`setKey` + `resetMapping`, config re-saved), Escape cancels, Reset
restores default. Vanilla Controls lists the same binds (same KeyMapping
objects) — expected, not redundant.

### Config with mods

Example targeting block (ids are `BuiltInRegistries` keys, so modded entities
just work — e.g. `"some_mod:boss": "ALWAYS"`):

```json
"targeting": {
  "minecraft:zombie": "ALWAYS",
  "minecraft:zombified_piglin": "WHEN_ANGERED",
  "minecraft:boat": "OFF"
},
"playerMode": "BY_NAME",
"playerNames": ["Alice", "Bob", "Charlie_The_Third"],
```

Old `attackPlayers/Hostile/Passive/Invisible` booleans are ignored, dropped
on next save, once-logged. Same treatment for the removed
`humanDelayMinMs`/`humanDelayMaxMs`: KillAura now attacks at the maximum rate
allowed by the vanilla attack cooldown. No artificial delay is inserted
between attacks. On servers with cooldown-based anti-cheat, this will not
change what the server accepts — the cooldown gate is server-enforced.

## Health & TTK overlay

Targeting tab, two checkboxes above checkLOS: "Show health & TTK above
target" (`showHealthOverlay`, default off) and "Show measured Last TTK line"
(`showMeasuredTTK`, default on). When KillAura has a live target, the overlay
draws above its head (anchor `boundingBox.maxY + 0.3`, +0.35 more under a
custom nametag): a vanilla-sprite heart row (10 max, `+N` overflow,
yellow absorption row above when present), then `Est. X.Xs`, then — after one
recorded kill of that (weapon, entity) pair this session — `Last: X.Xs`.
Estimate = `ceil(health / damagePerHit) * attackInterval`, damage from the
vanilla formula mirrored client-side (weapon + Sharpness/Smite/Bane +
Strength vs armor/toughness + Resistance), interval from cooldown period only
(no artificial delay — KillAura attacks at cooldown rate). A `*` suffix means the estimate assumes hits that are
not landing (out of reach, or walled with checkLOS on). Estimate text is
cached and rebuilt only on target/health/weapon change; projection math is
per-frame primitives with reused scratch.

"The hearts show the client's view of the target's health. In singleplayer this is exact. On a server, health sync is delayed by a tick or two and can lag during heavy combat.

The Est. time-to-kill is a client-side estimate using the vanilla damage formula. It will be close on vanilla servers. It will be wrong on modded or plugin-based servers where damage multipliers, custom armor, or scripted damage rules exist. The estimate assumes every Kill Aura attack lands — if checkLOS is on and the target moves behind a wall, the real TTK will be longer than shown, which is why the estimate is marked with '*' in that case.

The Last: X.Xs line is a real measurement from the previous time you killed this entity type with this weapon in this session. It's usually more accurate than the estimate on servers that behave like vanilla, and equally wrong on servers that don't."

## Mining (`*mine`, `*c`, `*p`, `*up`)

Star-prefixed chat commands, intercepted client-side before anything reaches
the server (`*mine diamond_ore`, `*mine mode cave|xray`, `*mine radius 8-64`,
`*mine stop`, `*c` cancel, `*p` pause, `*up` resume). Anything else passes
through untouched. Pausing mining never pauses KillAura — independent systems.
KillAura's damage estimate reads the held item, so auto-equipping a pickaxe
changes the estimate; that is correct behavior, not a bug.

Greedy straight-line walker (no A*): walks to the nearest match, jumps
1-block steps, mines directly-blocking breakable blocks inline, blacklists
targets unreachable after 100 stuck ticks, rescans every 20 ticks. Auto-equip
picks the fastest hotbar pickaxe (tier tie-break netherite→wooden) via one
hotbar-switch packet per change; no pickaxe → red cancel line. Mining tab
(Status panel, test scan, quick actions) mirrors the same controller methods
as the commands — no duplicated logic.

## Mining tab

Third tab (Targeting | Mining | Keybind): master enable toggle (`mineEnabled`,
off gates commands with a red line and cancels running ops), Cave/Xray radio
with dynamic sub-labels, auto-equip toggle, radius field (blur-validated
8–64), live status panel (state / target / block / pickaxe-in-hotbar /
loaded-vs-total chunks / last scan), test-scan row (default
`minecraft:diamond_ore`, reports in-panel, disabled while running), quick
actions mirroring the commands with spec'd enable states. Non-blocking: mining
runs while the tab is open.

What works
- Mining blocks the player can walk to in a straight line
- 1-block step-ups and breaking blocks directly in the path
- Cave mode and Xray mode filtering
- Pause / resume / cancel at any time
- Auto-equip of the best pickaxe in the hotbar

What doesn't work (v1 limitations)
- Navigating caves, cliffs, or ravines
- Descending to reach an ore the player can't see
- Pathfinding through unloaded chunks
- Handling water, lava, ladders, or scaffolding
- Working on servers with movement-prediction anti-cheat

Why: the pathfinder is a simple greedy walker, not Baritone's A*. It will find and mine ores reachable by walking. It will blacklist and skip ores that require real navigation. Baritone handles the harder cases with ~50,000 lines of pathfinding code. This mod is not Baritone.

On servers: if the server has a modern anti-cheat (GrimAC, HawkAC, Matrix, Spartan), walking to a block or sending movement packets the server can't validate will get flagged. This works on singleplayer, LAN, and your own servers. It will not work reliably on public servers.

## TEMP-SMOKE-TEST revert checklist (26.x migration)

The repo currently builds against 1.21.11 on JDK 21 so javac runs on this
machine. Every smoke override is marked `TEMP-SMOKE-TEST` in-code. Revert all
of these together when JDK 25 and the 26.x artifacts exist — partial reverts
break the build:

1. `gradle.properties`: `minecraft_version` → 26.2/26.3, `loader_version` →
   0.19.3+/0.19.5+, `fabric_version` → 0.160.x (verify latest).
2. `build.gradle`: toolchain + `release` 21 → 25 (needs JDK 25 installed).
3. `build.gradle`: full `fabric-api` → the 3 minimal modules (base,
   lifecycle-events, key-binding-api).
4. `build.gradle`: `fabric-command-api-v2:2.4.7+6b42a6003e` (hash-pinned to
   the 1.21.11 smoke API) → re-resolve the module version for 26.x; the
   minimal-modules set then gains this fourth entry.
   Same for `fabric-rendering-v1:16.2.10+0290ad933e` (health overlay hook).
   Same for `fabric-message-api-v1:6.1.12+4ebb5c083e` (star-chat mining commands).
5. `fabric.mod.json`: `java >=21` → `>=25` (JSON holds no comment — this list
   is its marker).
6. `multifeaturemod.mixins.json`: `JAVA_21` → `JAVA_25` (same — no comment
   possible in JSON).
7. Re-run the Verification javap table against the 26.x jar (mixin names,
   `// TBC on 26.x` markers, NeutralMob set in `TargetMode`).

## Verification (run this before any release)

All mixin targets below were validated against **Mojang 1.21.11** official mappings
(mappings.dev, 2026-10-06). They are **unverified for 26.x**. `defaultRequire=1`
means any drift now fails fast at game load instead of silently no-op'ing — a
load-time `InvalidMixinException` naming one of these methods is the expected
symptom of 26.x drift, and the fix is exactly this procedure:

**1. Confirm method names against the mapped 26.x jar** (get it via
`./gradlew :migrateMappings` / Loom caches, or decompile the mapped client):

```
javap -p -classpath <minecraft-26.x-mapped.jar> net.minecraft.client.gui.Gui | findstr /i "renderTextureOverlay renderVignette renderCameraOverlays"
javap -p -classpath <minecraft-26.x-mapped.jar> net.minecraft.client.renderer.ScreenEffectRenderer
javap -p -classpath <minecraft-26.x-mapped.jar> net.minecraft.client.renderer.fog.FogRenderer
javap -p -classpath <minecraft-26.x-mapped.jar> net.minecraft.client.renderer.ItemInHandRenderer | findstr /i "renderArmWithItem renderItem"
javap -p -classpath <minecraft-26.x-mapped.jar> net.minecraft.client.renderer.GameRenderer | findstr /i "NightVision"
```

Expected (1.21.11 baseline — update the `// TBC on 26.x` markers if 26.x differs):

| Class | Expected method | Descriptor |
|---|---|---|
| `Gui` | `renderTextureOverlay` | `(GuiGraphics, Identifier, float)` private, void |
| `Gui` | `renderVignette` | `(GuiGraphics, Entity)` private, void |
| `ScreenEffectRenderer` | `renderFire` | `(PoseStack, MultiBufferSource, TextureAtlasSprite)` private static, void |
| `ScreenEffectRenderer` | `renderWater` | `(Minecraft, PoseStack, MultiBufferSource)` private static, void |
| `ScreenEffectRenderer` | `renderTex` | `(TextureAtlasSprite, PoseStack, MultiBufferSource)` private static, void |
| `fog.FogRenderer` | `setupFog` | `(Camera, int, DeltaTracker, float, ClientLevel)→Vector4f` instance |
| `ItemInHandRenderer` | `renderArmWithItem` | `(AbstractClientPlayer, float, float, InteractionHand, float, ItemStack, float, PoseStack, SubmitNodeCollector, int)` |
| `GameRenderer` | `getNightVisionScale` | `(LivingEntity, float)→float` public static |

If a name/descriptor changed, update only the `method = "…"` string (and the
injector signature) in that mixin — logic stays.

**2. Prove injection with mixin debug export:**

```
./gradlew runClient -Pmixin.debug.verbose=true -Dmixin.debug.verbose=true -Dmixin.debug.export=true
# or: GRADLE_OPTS="-Dmixin.debug.verbose=true -Dmixin.debug.export=true" ./gradlew runClient
```

What to look for:
- `run/.mixin.out/` must contain one exported class per mixin target
  (`Gui`, `ScreenEffectRenderer`, `fog.FogRenderer`, `ItemInHandRenderer`,
  `GameRenderer`). Missing export = that mixin never applied.
- Log lines `Mixing Gui from multifeaturemod.mixins.json:InGameHudMixin` (etc.)
  for all 7 mixins. With `defaultRequire=1`, a non-applying injector now throws
  `InvalidMixinException` at load — treat that as the drift signal, not a bug.

**3. In-game checks (one per mixin — all must be exercised, silent no-ops hide here):**
- Fullbright ON in darkness + shader path (LightmapMixin).
- Wear carved pumpkin, `noPumpkin=true` → no blur; powder snow, `noFrost=true` → no frost edges (InGameHudMixin filter).
- Set on fire, `lowFirePercent=0` → no fire overlay (FireOverlayMixin).
- `noFog=true` on a far render distance → distance fog gone. If fog persists
  (stale-UBO risk documented in `BackgroundRendererMixin`), retarget per-environment
  fog classes instead of `setupFog`.
- Submerge in water, `clearWater=true` → no underwater tint (WaterVisionMixin).
- Submerge in lava, `clearLava=true` → no lava tint. **Mandatory**: lava routing
  is the least certain target — if the tint persists, re-`javap`
  `ScreenEffectRenderer` on 26.x (LavaVisionMixin).
- Hold a shield offhand, `lowShield=true` → lowered; `itemScaling=true`,
  `itemScale=2.0` → enlarged (ItemRenderMixin).

## Constraints

- Singleplayer/private only. Do not use on public servers.
- No bypasses, no GUI, minimal Fabric API modules (`fabric-api-base`, `fabric-lifecycle-events-v1`, `fabric-key-binding-api-v1`).
- Java 25, Loom 1.17+, Gradle 9.6.0 (wrapper), Loader 0.19.3+/0.19.5+, API 0.160.0+/0.160.5+ (verify latest).
- MC version range stays pinned per target (see Setup step 4).

## API differences from older tutorials (1.21.11, javap-verified)

Tutorial code written for ≤1.20 will fail against these; the replacements are
already used in this repo:

- `BlockPos.offset(Direction)` is **gone**. Only the packed-long statics and
  `offset(int,int,int)` / `offset(Vec3i)` remain. Use
  `pos.offset(dir.getStepX(), dir.getStepY(), dir.getStepZ())`.
- `KeyMapping` has no public key getter; `setKey(Key)` + `resetMapping()`
  still exist, and `KeyMapping.setDown(boolean)` (instance method) is the
  supported way to simulate presses — never swap `player.input`.
- `ItemStack.getAttributeModifiers` returns nothing; use
  `forEachModifier(EquipmentSlot, BiConsumer)`.
- `Enchantments.*` are `ResourceKey`, not `Holder` — resolve via
  `registryAccess().lookupOrThrow(Registries.ENCHANTMENT)`.
- `GameProfile` (authlib 7.x) is a record — `.name()`, not `.getName()`.
- Screen input callbacks take event records: `keyPressed(KeyEvent)`,
  `mouseClicked(MouseButtonEvent, boolean)`. `MouseButtonEvent` exposes
  `x()`, `y()`, `button()`.
- Attack cooldown gate is `getAttackStrengthScale(0.0f)`, not
  `getAttackCooldownProgress` — the old name is gone on 1.21.11.

## Human test queue (ordered by priority)
Done headless so far: game launches, mod loads, no crash, mixins inject.

  0. setDown go/no-go — first `*mine` must move the player immediately after
     scanning. Standing still = broken wiring. (Fastest single check.)
  1. Walker movement — does `*mine` actually walk to a target on flat ground.
  2. `*mine` test scan in Mining tab — reports correctly for a known block.
  3. Master toggle — off: `*mine` says disabled; on: scans.
  4. Cave vs Xray — ore surrounded by stone: Xray finds it, Cave doesn't.
  5. Auto-equip — hold a sword, `*mine`, watch the hotbar switch.
  6. `*c` / `*p` / `*up` — pause preserves target, cancel clears, resume continues.
  7. Second input-affecting mod installed — with `setDown` driving there is no
     `player.input` swap left to conflict, so this item is expected to pass
     trivially; run it once to confirm and then drop it.
  8. Overlay visuals (health + TTK) — hearts above target, estimate reads sane.
  9. Lava sprite filter scope — only lava changes, not fire.
 10. Fog stale-UBO on toggle-off mid-session.
 11. Fullbright under Iris/shader if available.

## Deviations from spec

Section 2 of the build spec required 6 minimal Fabric API modules
(base, lifecycle-events-v1, key-binding-api-v1, command-api-v2,
message-api-v1, rendering-v1). The current build uses the full
fabric-api artifact because the minimal module set had hash-resolution
issues during the smoke-test build. RAM impact is small (single-digit
MB) but not measured. Reverting to the minimal set is a future task
once hash resolution is confirmed working.

Source-layout note: this repo uses Loom `splitEnvironmentSourceSets()`,
so client code lives in `src/client/java/` and mod metadata in
`src/client/resources/` (fabric.mod.json, mixins json). The jar builds
correctly with this layout (see build/libs). `src/main/java` is empty by
design — this is a deliberate client-only choice, not an accident. Do not
migrate to `src/main/java` without a separate work order.

Config-name note (wiring, not a bug): Section 9 of the spec uses
`killAuraReach`, `fullbrightMultiplier`, `noPumpkinEnabled`,
`noFrostEnabled`, `clearWaterEnabled`, `clearLavaEnabled`,
`lowShieldEnabled`, `noFogEnabled`, `itemScalingEnabled`,
`nightVisionCleanupEnabled`. The implemented `ModConfig` uses `reach`,
`fullbrightGamma`, `noPumpkin`, `noFrost`, `clearWater`, `clearLava`,
`lowShield`, `noFog`, `nightVisionCleanup`, `itemScaling` (+ `itemScale`,
`scanIntervalTicks`, `silentRotation`, `totemDelayMs` which the spec
schema omits but the code reads). Old keys are ignored by Gson and
dropped on next save. The spec JSON below is the spec's canonical
example; the field table after it is what this build actually reads.

## Phase 8 appendix — spec compliance

### Overview

Client-side Fabric mod: legit kill aura with reach + wall-penetration
toggle, Wurst-style four-packet auto-crit, auto-totem refill,
per-entity/per-player targeting GUI, mining command with cave/xray +
hotbar auto-equip, visual toggles, live health + TTK overlay, two GUI
commands. Singleplayer/private only.

### Feature list

KillAura + Reach, AutoCrit (no jump), AutoTotem, Fullbright, Low Fire,
No Pumpkin, No Frost, Clear Water, Clear Lava, Low Shield, No Fog,
Item Scaling, Night Vision Cleanup, Targeting GUI, Mining, Health/TTK
overlay, `/kapgui` + `/KVpp3La` + `/kvpp3la`.

### Install

Fabric Loader for 1.21.11, Fabric API, drop jar in `mods/`.
Client-side only — no server install.

### Usage — GUI entry points

`K`, `/kapgui`, `/KVpp3La`, `/kvpp3la`. All open the same
`MultiFeatureScreen` (Targeting default), silently.

### Usage — Mining commands

`*mine <block_id>`, `*mine mode cave|xray`, `*mine radius <8-64>`,
`*mine stop`, `*c` cancel, `*p` pause, `*up` resume. Both
`diamond_ore` and `minecraft:diamond_ore` accepted.

### Usage — Targeting tab

Player submenu (OFF/ALL/BY_NAME + names list), 250 ms debounced search,
entity tree (Hostile/Neutral/Passive/Utility/Other), per-entity
OFF/ALWAYS/WHEN_ANGERED, checkLOS checkbox, health-overlay checkboxes,
bulk actions with 3 s two-click confirm on ALL→ALWAYS.

### Usage — Keybind tab

R/G/O/K rows, click → "Press a key..." (15 s timeout, Escape cancels),
per-row Reset. Vanilla Controls shows the same binds (expected).

### Usage — Health & TTK overlay

Above current KillAura target only when `showHealthOverlay` is on:
hearts (10 max, `+N`, absorption row), `Est. X.Xs` (`*` when
checkLOS-on and walled), `Last: X.Xs` after one recorded kill of that
(weapon, entity) pair this session.

### Config schema example (spec Section 9, canonical — DO NOT COPY)

Do not copy this block verbatim. Copy the `## Config` block near the top
of this README instead — that one uses the real field names the code
reads. The block below is the spec's canonical example, kept for
traceability; its names differ (see mapping table after it).

```json
{
  "killAuraEnabled": false,
  "killAuraReach": 3.0,
  "killAuraCheckLOS": true,
  "showHealthOverlay": false,
  "showMeasuredTTK": true,
  "autoCritEnabled": true,
  "critFallMin": 0.11,
  "critFallMax": 0.17,
  "autoTotemEnabled": true,
  "fullbrightEnabled": false,
  "fullbrightMultiplier": 1.0,
  "lowFireEnabled": false,
  "lowFirePercent": 60,
  "noPumpkinEnabled": false,
  "noFrostEnabled": false,
  "clearWaterEnabled": false,
  "clearLavaEnabled": false,
  "lowShieldEnabled": false,
  "noFogEnabled": false,
  "itemScalingEnabled": false,
  "nightVisionCleanupEnabled": false,
  "targeting": {
    "minecraft:zombie": "ALWAYS",
    "minecraft:zombified_piglin": "WHEN_ANGERED",
    "minecraft:boat": "OFF"
  },
  "playerMode": "BY_NAME",
  "playerNames": ["Alice", "Bob"],
  "mineEnabled": true,
  "mineMode": "CAVE",
  "mineScanRadius": 32,
  "mineMaxBlocks": 64,
  "mineAutoEquipPickaxe": true,
  "mineScanIntervalTicks": 20
}
```

Implemented-field mapping for this build: `killAuraReach` → `reach`,
`fullbrightMultiplier` → `fullbrightGamma`,
`noPumpkinEnabled` → `noPumpkin`, `noFrostEnabled` → `noFrost`,
`clearWaterEnabled` → `clearWater`, `clearLavaEnabled` → `clearLava`,
`lowShieldEnabled` → `lowShield`, `noFogEnabled` → `noFog`,
`itemScalingEnabled` → `itemScaling`,
`nightVisionCleanupEnabled` → `nightVisionCleanup`, plus
`scanIntervalTicks`, `silentRotation`, `totemDelayMs`, `itemScale`
which the code reads but the spec JSON omits. Every entity not in
`targeting` resolves to OFF. Legacy `attackPlayers/attackHostile/
attackPassive/attackInvisible` and `humanDelayMinMs/humanDelayMaxMs`
are detected by raw-text scan, ignored, dropped on next save, warned
once. `critFallMin > critFallMax` swaps with a once-warning.

### API differences from older tutorials (spec Section 11)

- No KeyMapping.getKey()
- ItemStack.getAttributeModifiers -> forEachModifier(EquipmentSlot, BiConsumer)
- Enchantments return ResourceKey; use registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
- GameProfile is a record -> .name() not .getName()
- Input callbacks are event records, not raw ints
- No BlockPos.offset(Direction) — use getStepX/Y/Z or offset(int,int,int)
- getAttackStrengthScale(0.0f) not getAttackCooldownProgress(0.0f)
- No PickaxeItem class — use ItemTags.PICKAXES
- ItemStack.getDestroySpeed(BlockState) not getMiningSpeedMultiplier
- Player.getInventory().getSelectedSlot() / setSelectedSlot(int) — the "selected" field is private
- Level.getChunk(x, z, ChunkStatus.FULL, false) for no-force-load
- EntityType.getCategory() returns MobCategory, not Category
- ServerboundSetCarriedItemPacket(int) for hotbar switch
- ClientSendMessageEvents.ALLOW_CHAT returns boolean

Discovered during build (added): `forEachModifier` has two overloads
on 1.21.11 (`EquipmentSlotGroup+TriConsumer` and
`EquipmentSlot+BiConsumer`) — this build uses the latter;
`LevelChunkSection.maybeHas` + `hasOnlyAir` confirmed as the palette
gate; `Gui.renderTextureOverlay(GuiGraphics, Identifier, float)` is the
single pumpkin/frost choke point; `renderVignette` takes
`(GuiGraphics, Entity)`.

### Known limitations (spec Section 14, verbatim — do not soften)

KILL AURA
- Silent rotation is a known signature. Modern anti-cheats (HawkAC,
  Glacier, Intave, GrimAC) specifically target the rotate-to-target-then-
  snap-back pattern.
- Wall-penetration (checkLOS off) attacks land on vanilla servers. Servers
  with LOS-validating anti-cheats will reject or flag the attack. This is
  not a bypass — the server decides.

AUTO-CRIT
- The four-packet sequence produces a fall distance in [0.11, 0.17]. This
  beats naive thresholds (common cancellation point is 0.1). Modern
  movement-prediction anti-cheats reconstruct server-side position and can
  reject it regardless of value.
- The randomized range defeats constant-value fingerprinting, not server-
  side simulation checks.

AUTO TOTEM
- Uses vanilla swap packets. Dedicated detectors (e.g. TotemGuard) flag
  "impossible click timing" via jitter analysis. Jitter helps; doesn't
  guarantee.

MINING
- The v1 walker is a greedy straight-line walker, NOT Baritone's A*. It
  handles: blocks reachable by walking, 1-block step-ups, breaking blocks
  directly in the path. It does NOT handle: caves, cliffs, ravines,
  descending to unreachable ores, water/lava/ladders/scaffolding.
- Baritone closes the gap with ~50,000 lines of pathfinding. This mod is
  not Baritone.
- On servers with movement-prediction anti-cheat, walking to a block will
  be flagged. Works on singleplayer, LAN, and your own servers.

HEALTH + TTK OVERLAY
- Hearts show the client's view of target health. Exact in singleplayer.
  Lagged by a tick or two on servers.
- "Est." uses the vanilla damage formula client-side. Close on vanilla
  servers. Wrong on modded/plugin servers with custom damage rules.
- "Last" is a real measurement from the previous kill of this entity type
  with this weapon in this session.

GENERAL
- "Always crit on any server" is not a real guarantee. If you own the
  server, disable the anti-cheat check. If you don't, it's a cat-and-mouse
  game.
- This mod is for singleplayer and private servers. Public servers with
  modern anti-cheat will flag or ban.

### Additional honest caveats discovered while building

- Mining auto-cancels after 10 min active or 10 min paused, and on world
  change — a runaway op never waits forever. Blacklist caps at 256.
- Keybind rebind arms for 15 s then auto-disarms; Escape cancels.
- KillAura's hotbar switch to a pickaxe changes the damage estimate —
  correct behavior, not a bug (estimate reads the held item).
- Fullbright gamma restores vanilla on toggle-off; fog-off leaves the fog
  UBO untouched (stale values) — if distance fog persists, retarget
  per-environment fog classes. Lava overlay routing is the least certain
  target — in-game lava check mandatory.
- No wiring bug found during documentation: every documented config flag
  was grepped to a reader (`killAuraCheckLOS` in ModConfig + KillAura +
  TargetingTab verified this turn).
