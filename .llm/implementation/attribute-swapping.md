<!-- last updated: 2026-06-29 -->

# Attribute swapping — complete reference for client-side detection

The single source of truth for **what attribute swapping is, why it works, every
variant (including spear / Lunge swapping), and what is observable client-side**
so Visual Swap can detect it and show the correct glyph.

Verified facts are tagged with `file:line` against
`mc_decompiled/sources/26.2/{common_src,client_src}/` (MC **26.2**, Mojang
mappings). Player-facing numbers without a `file:line` come from the wiki/community
(see [Sources](#sources)) and may drift between snapshots — re-verify before relying
on a specific value.

> Scope note: Visual Swap is a **client cosmetic that detects** the conditions of a
> swap; it never performs or assists the exploit. Everything below is framed around
> *detection* (observable inputs + held items), not exploitation.

---

## 1. TL;DR for the implementer

- Attribute swapping = switch the held item and **attack in the same tick**, so the
  attack lands using the **previous item's `ATTACK_DAMAGE`/`ATTACK_SPEED` attributes**
  (and the new item's live-read enchantment effects). It works because held-item
  attribute modifiers are reconciled **once per tick, server-side only**, while the
  attack reads the *live* attribute value.
- **Version status (26.2):** attribute swapping was **removed in `26.2-pre2`** and
  **re-added in `26.2-pre3`** "with the intention of fixing it in a future version",
  so it is **live in this project's target**. Java Edition only.
- The client **cannot read the real attack damage** to detect staleness:
  `ATTACK_DAMAGE` is **not syncable** and the client never applies held-item modifiers
  locally. ⇒ Detect from **(item/slot change) + (attack/use input) within a short
  window**, which is exactly the `SwapWindow` approach. Do **not** try to read
  attribute values.
- **Spear / Lunge is a distinct, important variant.** The spear's **jab** is the
  `PiercingWeapon` path, fired by the **attack key** (no target required), it reads
  `ATTACK_DAMAGE` **live** (swap-vulnerable), and it is what triggers **Lunge**. The
  spear's **charge** attack (`KineticWeapon`) reads only the *base* value, so it is
  **not** swap-vulnerable.
- **Why the current mod misses lunge swapping:** the tick logic opens the window only
  on a change *to a non-empty item* and **clears on empty**; the common lunge-swap
  toggles `spear ⇄ empty slot`, so no window ever opens → no glyph.
  See [§7](#7-why-the-current-detection-misses-lunge-swapping).

---

## 2. Core mechanic — why a same-tick swap+attack keeps the old attributes

### 2.1 Held-item attributes reconcile once per tick, server-side only

- `LivingEntity.tick()` calls `detectEquipmentUpdates()` at **LivingEntity.java:2628**,
  **inside** the `if (!this.level().isClientSide())` guard (block ~**2607–2635**).
  So attribute application is **server-side, once per entity tick**.
- `detectEquipmentUpdates()` (**2754–2762**) → `collectEquipmentChanges(lastEquipmentItems)`
  (**2764–2797**). Change test = `!ItemStack.matches(...)` (`equipmentHasChanged`, **~2800**)
  — compares item + count + components (so same-item count/NBT-equal stacks are *not* a change).
- For each changed slot it calls `current.forEachModifier(slot, ...)` (**2783**) and, per modifier,
  `instance.removeModifier(id)` (**2786**) then `instance.addTransientModifier(modifier)` (**2787**).
- `handleEquipmentChanges()` (**2815–2823**) only sends `ClientboundSetEquipmentPacket` (**2822**);
  it does **not** re-apply attributes.

⇒ When you swap and attack **in the same tick**, this reconciliation has **not run yet**, so the
`AttributeMap` still holds the **previous** item's modifiers.

### 2.2 Both item attributes and enchantment attributes flow through the same channel

- `ItemStack.forEachModifier(slot, consumer)` (**ItemStack.java:974–978**) folds in:
    - the item's own `ItemAttributeModifiers` component (`DataComponents.ATTRIBUTE_MODIFIERS`)
      via `ItemAttributeModifiers.forEach` (**ItemAttributeModifiers.java:87–92**), **and**
    - enchantment-derived attribute modifiers via `EnchantmentHelper.forEachModifier`
      (**EnchantmentHelper.java:344–350**) — iterates `EnchantmentEffectComponents.ATTRIBUTES`.
- Both reach the same `addTransientModifier` in §2.1, so they reconcile on the same once-per-tick clock.
- Modifier IDs: `Item.BASE_ATTACK_DAMAGE_ID` / `BASE_ATTACK_SPEED_ID` (**Item.java:134–135**).

### 2.3 How the attribute value is computed

- `AttributeMap.getValue(holder)` (**AttributeMap.java:71–74**) → `AttributeInstance.getValue()`
  (**AttributeInstance.java:158–164**, cached behind a `dirty` flag) → `calculateValue()`
  (**166–179**): `base + Σ ADD_VALUE`, then `× ADD_MULTIPLIED_BASE`, then `× ADD_MULTIPLIED_TOTAL`.
- `addTransientModifier` (**111–113**) / `removeModifier(Identifier)` (**141–150**) set `dirty`.
- `Attributes.ATTACK_DAMAGE` (**Attributes.java:18**): base `2.0`, **NOT syncable**.
  `Attributes.ATTACK_SPEED` (**line 20**): base `4.0`, **syncable**.

### 2.4 Where the attack reads damage (the "live read" that makes the exploit)

- **Melee** `Player.attack(Entity)` (**Player.java:885–939**): reads
  `getAttributeValue(Attributes.ATTACK_DAMAGE)` at **line 889** (live). Calls `onAttack()` at **895**.
- **Spear jab** `PiercingWeapon.attack()` (**PiercingWeapon.java:69–84**): reads
  `attacker.getAttributeValue(Attributes.ATTACK_DAMAGE)` at **line 70** (live → **swap-vulnerable**);
  runs `POST_PIERCING_ATTACK` effects afterwards (**line 78**).
- **Spear charge** `KineticWeapon.damageEntities()` (**KineticWeapon.java:68–106**): reads
  `getAttributeBaseValue(...)` at **line 78** — **base only, NOT swap-vulnerable**;
  damage `= base + floor(relativeSpeed * damageMultiplier)` (**line 96**), applied during
  `ItemStack.onUseTick()` (**ItemStack.java:1053–1064**), i.e. *after* reconciliation, not in the swap window.

### 2.5 The attack-strength bar resets on swap (cosmetic, not the exploit)

- `Player.tick()` (**Player.java:232–276**): `++attackStrengthTicker; ++itemSwapTicker` (**262**);
  if `!ItemStack.matches(lastItemInMainHand, mainHand)` then if `!isSameItem(...)`
  → `resetAttackStrengthTicker()` (**267**); `lastItemInMainHand = mainHand.copy()`.
- `resetAttackStrengthTicker()` (**1721–1724**) zeroes `attackStrengthTicker` **and** `itemSwapTicker`.
- This is why the vanilla charge bar empties the instant you switch items — see
  `mc_decompiled/.knowledge/attack-indicator-hud.txt`. The mod must not be confused by it.

---

## 3. Client tick / input order (when the swap and the attack happen)

`Minecraft.tick()` (**Minecraft.java:1745–1819**): `handleKeybinds()` (**1769**) runs **before**
`level.tickEntities()` (**1779**). So input (including the swap + attack) is processed, then the
player entity ticks. Fabric `ClientTickEvents.END_CLIENT_TICK` fires after both — the mod's snapshot point.

`handleKeybinds()` (**1840–1918**), order within the same tick:

1. Hotbar select `inventory.setSelectedSlot(i)` (**1866**) — the swap; `getMainHandItem()` now returns the new item.
2. `keyAttack.consumeClick()` → `startAttack()` (**1900**).
3. `keyUse.consumeClick()` → `startUseItem()` (**1903**). (Attack clicks are suppressed while `isUsingItem()`.)

`startAttack()` (**1613–1677**):

- Gets `getItemInHand(MAIN_HAND)` (**1637**); gated by `isHandsBusy` (1624), `cannotAttackWithItem` (1641).
- **Spear branch:** if `heldItem.get(DataComponents.PIERCING_WEAPON) != null` (**1645**) →
  `gameMode.piercingAttack(weapon)` + `player.swing` + `return` (**1646–1650**).
  **No entity/block hit required — the jab fires while aiming at air.**
- Otherwise `case ENTITY` → `gameMode.attack(player, entity)` (**1655**), honoring the item's
  `AttackRange` component (**1653**); blocks/miss handled below.

`MultiPlayerGameMode.attack(player, entity)` (**451–456**): `ensureHasSentCarriedItem()`,
send `ServerboundAttackPacket` (**453**), `player.attack(entity)` (**454**), `resetAttackStrengthTicker()`.

`MultiPlayerGameMode.piercingAttack(weapon)` (**529–535**):
`ensureHasSentCarriedItem()` (**530**); send `ServerboundPlayerActionPacket(Action.STAB, ZERO, DOWN)` (**531**);
`player.onAttack()` (**532**); **`player.postPiercingAttack()` (533) — the Lunge fires CLIENT-SIDE here**;
`weapon.makeSound(player)` (**534**).

> Detection takeaway: the spear jab + lunge is driven by **`keyAttack`** and is **client-predicted**
> (the lunge impulse is applied locally at 533), so it is fully observable without the server.

---

## 4. The Spear weapon (MC 26.2)

- **No `SpearItem` class.** Spears are built with the `Item.Properties.spear()` builder
  (**Item.java:503**, components set at **504**) and registered as `WOODEN_SPEAR … NETHERITE_SPEAR`
  in `Items.java`.
- Components attached by `spear()`:
    - `DataComponents.PIERCING_WEAPON` — the **instant jab/stab** (melee, attack-key).
    - `DataComponents.KINETIC_WEAPON` — the **charge attack** (held / use-key, movement-scaled).
    - `DataComponents.ATTACK_RANGE` — extended reach.
    - `DataComponents.SWING_ANIMATION` = `SwingAnimationType.STAB`.
    - `ItemAttributeModifiers` — `ATTACK_DAMAGE` / `ATTACK_SPEED` on the MAINHAND slot.
- **Client spear test:** `stack.get(DataComponents.PIERCING_WEAPON) != null`
  (or `KINETIC_WEAPON`). This is how vanilla itself detects "is a spear" (Minecraft.java:1645,
  SpearAnimations.java) — prefer it over an item-id allowlist.

### Two attacks — only the jab is swap-vulnerable

| Attack         | Component        | Input                                                                             | Reads damage                                                         | Swap-vulnerable? | Triggers Lunge?                  |
|----------------|------------------|-----------------------------------------------------------------------------------|----------------------------------------------------------------------|------------------|----------------------------------|
| **Jab / stab** | `PiercingWeapon` | **attack key** (`keyAttack` → `startAttack` → `piercingAttack`), no target needed | `getAttributeValue(ATTACK_DAMAGE)` **live** (PiercingWeapon.java:70) | **Yes**          | **Yes** (`POST_PIERCING_ATTACK`) |
| **Charge**     | `KineticWeapon`  | **use key** held (right-click), movement-scaled                                   | `getAttributeBaseValue(...)` base only (KineticWeapon.java:78)       | No               | No                               |

Player-facing numbers (wiki/community, Java; approximate):

- Jab damage by tier: wood/gold `0.5❤`, stone/copper `1❤`, iron `1.5❤`, diamond `2❤`, netherite `2.5❤`.
- Attack speed / jab cooldown: wood `0.65s` (1.54) → netherite `1.15s` (0.87).
- Charge damage `= multiplier × relative speed (b/s)`, min `5.1 b/s`; multipliers wood/gold `0.7×` → netherite `1.2×`.
- Range ~`2–4.5` blocks. Spears **cannot crit or sprint-knockback**. Java: spears can charge from the offhand.

---

## 5. The Lunge enchantment (MC 26.2)

- `Enchantments.LUNGE` (**Enchantments.java:136**, registered **181**); registry id `lunge`;
  enchantable via tag `ItemTags.LUNGE_ENCHANTABLE`; **spear-only**, max level **III**.
- Implemented purely as an **`EnchantmentEffectComponents.POST_PIERCING_ATTACK`** effect
  (**EnchantmentEffectComponents.java:41**). **There is no Lunge keybind** — it auto-fires after a jab.
  Effects: `ApplyEntityImpulse` `perLevel(0.458)` (the forward dash), `ApplyExhaustion` `perLevel(4.0)`,
  `ChangeItemDamage 1`, `PlaySoundEffect` `LUNGE_1/2/3`.
- Activation conditions: not in a vehicle, not fall-flying, not in water, and (player) food level `≥ 7`
  (non-players / creative bypass the food check).
- Trigger path: `PiercingWeapon.attack()` → `LivingEntity.postPiercingAttack()` (**1594–1600**) →
  `EnchantmentHelper.doPostPiercingAttackEffects` (**217–218**) → `Enchantment.doPostPiercingAttack` (**279**).
  Client-predicted copy at `MultiPlayerGameMode.piercingAttack` **line 533**.
- **Client Lunge test:** `EnchantmentHelper.getItemEnchantmentLevel(lungeHolder, stack) > 0` — the client
  *can* read enchantments on the local stack (unlike attributes), so "is a lunge spear" is reliably detectable.

---

## 6. The variants to detect

### 6.1 Classic melee swap-hit

Switch hotbar to a different item and press **attack on an entity** in the same tick. The melee
`Player.attack` reads `ATTACK_DAMAGE` live (Player.java:889) before reconciliation → the hit lands with
the **previous** item's attribute damage; enchantment *effects* that re-read the held stack use the **new** item.

- Observable: main-hand **item changed** this tick + **`keyAttack`** press + (for particles) an entity target
  (`AttackEntityCallback`). `keyUse` matters for use-on-entity interactions.

### 6.2 Spear attribute swap (the user's "attribute swapping on a spear with lunge")

Hold a high-`ATTACK_DAMAGE` weapon, switch to the (Lunge) spear, and press **attack** in the same tick.
`PiercingWeapon.attack` reads the lingering high `ATTACK_DAMAGE` (PiercingWeapon.java:70) → a **big jab**,
and the same jab fires **Lunge** for the dash. The jab needs **no target**.

- Observable: main-hand item **changed to a spear** (`PIERCING_WEAPON` present, optionally `LUNGE > 0`) +
  **`keyAttack`** press. The lunge is client-predicted (Minecraft path §3), so it's visible locally even with no entity
  hit.

### 6.3 Lunge swapping (cooldown skip) — community tech

Rapidly toggle **spear ⇄ an empty / no-cooldown slot** so the spear's attack cooldown keeps resetting,
letting you jab+lunge again sooner (switching items zeroes the attack-strength/charge state — §2.5). Used
for continuous lunges in PvP.

- Observable: the **main-hand slot keeps changing**, frequently **to and from an empty slot**, while a spear is one of
  the two stacks.
- ⚠️ This is the case the current code structurally cannot see — see §7.

---

## 7. Why the original detection missed lunge swapping (now fixed)

> **STATUS (2026-06-29): FIXED.** The §8 approach is implemented in
> `VisualSwapClient.onEndClientTick`: any `!ItemStack.isSameItem` main-hand change (incl. to/from empty)
> opens the window, the empty-hand `clear()` is gone, a `primed` flag skips the first post-spawn tick, and
> `AttackEntityCallback` now joins `UseEntityCallback`. The text below records the original bug.


In `VisualSwapClient.onEndClientTick` the window opens **only** on a change to a *non-empty* item and
**clears on empty**:

```java
if (!this.previousMainHand.isEmpty() && !ItemStack.matches(held, this.previousMainHand)) {
    if (held.isEmpty()) this.swapWindow.clear();   // spear -> empty : window CLOSED
    else this.swapWindow.onSwap(tick);
}                                                   // empty -> spear : guarded out (prev was empty)
```

For the `spear ⇄ empty` lunge-swap (§6.3):

1. **spear → empty**: `held.isEmpty()` → `clear()` closes the window.
2. **empty → spear**: gated by `!previousMainHand.isEmpty()` (previous was empty) → `onSwap` never runs.

So **no window ever opens**, and the glyph never appears. Secondary gaps:

- The mod has **no spear/Lunge awareness** — it can't show a spear-specific glyph or know the jab needs no target.
- The spear jab fires with **no entity target**, but particles only spawn via `UseEntityCallback` (use *on* an entity) —
  that path never fires for a jab into open air. (Melee swap-hits would similarly want `AttackEntityCallback`.)
- The `WINDOW_TICKS = 2` + "possible last tick" gate can be too tight for the fast spear-swap cadence.

---

## 8. Recommended detection approach (client-only, no attribute reads)

1. **Identify the held item by component, not id:** spear = `stack.get(DataComponents.PIERCING_WEAPON) != null`;
   lunge spear = additionally `EnchantmentHelper.getItemEnchantmentLevel(lungeHolder, stack) > 0`.
2. **Track the selected slot / item every tick** and treat **any** main-hand change as a swap — **including
   to/from an empty slot** (don't `clear()` on empty if you want to catch lunge swapping; instead key off
   `inventory.getSelectedSlot()` changing, or compare item identity allowing empty).
3. **Hook the inputs that drive each path — but the spear jab needs the SWING, not the key edge.** The jab
   takes a special branch: `startAttack()` sees `PIERCING_WEAPON` and calls `gameMode.piercingAttack()` then
   `return`s (Minecraft.java:1646-1650), so it **never reaches `gameMode.attack()`** → **`AttackEntityCallback`
   never fires for a jab**. And the jab is a fast sub-tick click, so a once-per-tick `keyAttack.isDown()` edge
   **misses it intermittently** (this is the "spear sometimes works, sometimes doesn't" symptom). Reliable
   signal: the swing the jab always raises — `player.swinging` / `player.swingTime` (both public; the swing
   animation persists several ticks so it survives the tick boundary). Detect a swing start as
   `swinging && (!wasSwinging || swingTime < prevSwingTime)` (the `swinging` guard avoids a false hit on the
   tick a swing naturally ends). `keyUse` edge still covers right-click interactions / spear charge. Note: the
   lunge is **client-predicted** (`player.postPiercingAttack()` at MultiPlayerGameMode.java:533), but there is
   no callback for it — the swing is the observable proxy. Gate the click to the swap window so a non-swap
   swing (mining, etc.) never flashes.
4. **Do not read `ATTACK_DAMAGE`.** It is not syncable and is never reconciled on the client (§9), so it
   cannot tell you whether a swap is "live". Base the glyph purely on **(slot/item change) + (input) within the window
   **.
5. **Optional richer signals:** consider `AttackEntityCallback` (melee on entity) alongside `UseEntityCallback`;
   and a distinct glyph/variant when the involved item is a **Lunge spear** so the spear case reads differently
   from a plain swap-hit.

---

## 9. The client cannot read the live attack damage (why §8.4 holds)

- `LivingEntity.onEquipItem` (**679–693**) returns early when `isClientSide()` (**680**) — the client never
  applies held-item modifiers locally.
- `ClientPacketListener.handleSetEquipment` (**1661–1668**) → `setItemSlot` (**2175–2177**) → `onEquipItem`
  updates the *item bag* only, **no attributes**.
- The **only** client attribute update is `handleUpdateAttributes` (**2370–2393**): `setBaseValue`,
  `removeModifiers` (**2388**), `addTransientModifier` (**2390**) — driven by `ClientboundUpdateAttributesPacket`,
  i.e. **server-sent and latency-dependent**.
- `ATTACK_DAMAGE` is **not syncable** (Attributes.java:18), so the client's value does not reflect held-item
  modifiers reliably. ⇒ Detect from inputs + items, never from attribute values.

---

## 10. Quick file:line reference

| What                                                       | File                              | Line(s)                               |
|------------------------------------------------------------|-----------------------------------|---------------------------------------|
| `detectEquipmentUpdates()` (server-only guard)             | LivingEntity.java                 | 2607–2635, 2628                       |
| `detectEquipmentUpdates` / `collectEquipmentChanges`       | LivingEntity.java                 | 2754–2762 / 2764–2797                 |
| modifier remove/add (reconcile)                            | LivingEntity.java                 | 2786–2787                             |
| `handleEquipmentChanges` (+ SetEquipment packet)           | LivingEntity.java                 | 2815–2823                             |
| `postPiercingAttack` (lunge hook)                          | LivingEntity.java                 | 1594–1600                             |
| `onEquipItem` client early-return                          | LivingEntity.java                 | 679–693 (680)                         |
| `setItemSlot`                                              | LivingEntity.java                 | 2175–2177                             |
| `Player.attack` live ATTACK_DAMAGE read                    | Player.java                       | 885–939 (889)                         |
| `Player.tick` swap detect + reset                          | Player.java                       | 232–276 (262–269)                     |
| `resetAttackStrengthTicker`                                | Player.java                       | 1721–1724                             |
| `ItemStack.forEachModifier` (+ enchantments)               | ItemStack.java                    | 974–978                               |
| `ItemStack.onUseTick` (kinetic damage)                     | ItemStack.java                    | 1053–1064                             |
| `ItemAttributeModifiers.forEach`                           | ItemAttributeModifiers.java       | 87–92                                 |
| `EnchantmentHelper.forEachModifier`                        | EnchantmentHelper.java            | 344–350                               |
| `EnchantmentHelper.doPostPiercingAttackEffects`            | EnchantmentHelper.java            | 217–218                               |
| `AttributeInstance` add/remove/get/calc                    | AttributeInstance.java            | 111–113 / 141–150 / 158–164 / 166–179 |
| `AttributeMap.getValue`                                    | AttributeMap.java                 | 71–74                                 |
| `Attributes.ATTACK_DAMAGE` (not syncable) / `ATTACK_SPEED` | Attributes.java                   | 18 / 20                               |
| `Item.BASE_ATTACK_DAMAGE_ID` / `BASE_ATTACK_SPEED_ID`      | Item.java                         | 134–135                               |
| `Item.Properties.spear()` builder                          | Item.java                         | 503–504                               |
| `PiercingWeapon.attack` live read                          | PiercingWeapon.java               | 69–84 (70)                            |
| `KineticWeapon.damageEntities` base read                   | KineticWeapon.java                | 68–106 (78, 96)                       |
| `Enchantments.LUNGE`                                       | Enchantments.java                 | 136, 181                              |
| `EnchantmentEffectComponents.POST_PIERCING_ATTACK`         | EnchantmentEffectComponents.java  | 41                                    |
| `Minecraft.tick` order                                     | Minecraft.java                    | 1745, 1769, 1779                      |
| `Minecraft.handleKeybinds` (slot/attack/use)               | Minecraft.java                    | 1840–1918 (1866, 1900, 1903)          |
| `Minecraft.startAttack` (spear branch)                     | Minecraft.java                    | 1613–1677 (1645–1650)                 |
| `MultiPlayerGameMode.attack`                               | MultiPlayerGameMode.java          | 451–456                               |
| `MultiPlayerGameMode.piercingAttack` (client lunge)        | MultiPlayerGameMode.java          | 529–535 (531, 533)                    |
| `ClientPacketListener.handleSetEquipment`                  | ClientPacketListener.java         | 1661–1668                             |
| `ClientPacketListener.handleUpdateAttributes`              | ClientPacketListener.java         | 2370–2393 (2388, 2390)                |
| STAB packet handler (server)                               | ServerGamePacketListenerImpl.java | 1232–1253                             |

---

## 11. Version history

- **Attribute swapping:** added Java `1.6.2`; **removed Java `26.2-pre2`**; **re-added Java `26.2-pre3`**
  "with the intention of fixing it in a future version". Java Edition exclusive. ⇒ present in **26.2**.
- **Spear:** added in the "Mounts of Mayhem" update.
- **Lunge:** added `25w41a` (originally `2×4^level` durability); reworked `25w43a` (exhaustion, no durability);
  `25w44a` (`4×level` exhaustion + 1 durability); Mending compatibility added `25w42a`.

---

## Sources

Decompiled MC 26.2 at `mc_decompiled/sources/26.2/` (authoritative; all `file:line` above).
Companion caches: `mc_decompiled/.knowledge/attribute-swap-mechanic.txt`,
`mc_decompiled/.knowledge/spear-lunge-mechanic.txt`, `mc_decompiled/.knowledge/attack-indicator-hud.txt`.

Player-facing / community (numbers may drift between snapshots):

- minecraft.wiki — Attribute swapping: <https://minecraft.wiki/w/Attribute_swapping>
- minecraft.wiki — Lunge: <https://minecraft.wiki/w/Lunge>
- thespike.gg — Spear guide & Lunge enchantment guide
- pcgamesn.com — "Minecraft's new spear has already been buffed" (lunge + charge)
- Community lunge-swap tutorials (YouTube/TikTok "spear attribute lunge swap")
