# Item normalization and fingerprinting

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

Chunk 3 converts documented DonutSMP item data into deterministic internal
identities. It does not estimate value or detect opportunities.

## API fields used

Only fields currently present in the official API schema are interpreted:

- namespaced item ID and stack count
- display name and lore
- enchantment IDs and levels
- armor-trim material and pattern
- container item IDs, quantities, names, enchantments, and trims

The documented schema does not expose durability/damage, repair cost, potion type,
book content, player-head owner, custom-model data, or nested container contents.
If a production response supplies numeric `damage`, `durability`, or `max_damage`,
the validator bounds the value but deliberately excludes it from the fingerprint.
The normalizer does not invent any missing value.

## Match types and scores

| Match type | Score | Default meaning |
| --- | ---: | --- |
| `EXACT` | 100 | All metadata required by an explicitly safe identity is available |
| `COMMODITY` | 75 | Allowlisted fungible item with no custom metadata |
| `VISIBLE_METADATA` | 60 | API-visible enchantment/name/trim identity; durability may be grouped |
| `APPROXIMATE` | 50 | Valid item, but its category or non-identity data is incomplete |
| `UNSUPPORTED` | 0 | Safe valuation is not possible with the available metadata |

Scores are evidence for a future confidence system. No alert decision uses them
in this chunk.

## Default supported categories

The configurable safe-category registry initially allows:

- every vanilla overworld, deepslate, and Nether ore block; metal ingots,
  nuggets, scraps, raw ores, gems, and their standard storage blocks
- common crops and farming materials
- common mob and crafting materials
- standardized golden apples and Totems of Undying
- a small exact-safe set such as beacons, conduits, nether stars, dragon eggs,
  and hearts of the sea
- empty shulker boxes as exact identities

The production live registry additionally treats every otherwise ordinary vanilla item
as a commodity. This covers building and decorative blocks, logs and plants, food,
redstone components, rails and transport, utility blocks, dyes, smithing templates,
music discs, and other fixed-ID vanilla categories without maintaining a brittle manual
allowlist. Explicit metadata-sensitive and container rules run before this fallback.

A commodity with a custom name, lore, enchantment, or trim is removed from
commodity matching and receives an exact metadata fingerprint when its category
is otherwise safe.

The live profile additionally accepts tools, swords, bows, crossbows, tridents,
shields, elytra, and similar equipment using `VISIBLE_METADATA`. Tool durability is
intentionally ignored, while enchantments, custom names, lore, and trims remain in
the exact fingerprint and therefore never mix across variants.

## Default unsupported categories

- Damageable equipment remains unavailable to the core conservative policy; the
  live profile opts into the durability-agnostic visible-metadata policy described above
- Written/writable/enchanted books
- Maps and player heads
- Potions and tipped arrows
- Fireworks, suspicious stew, goat horns, banners, spawn eggs, and other items
  whose important components are not exposed
- Filled containers, even though their exposed contents are fingerprinted
- Unknown API item fields
- Invalid IDs, counts, enchantments, or incomplete trims
- Container structures beyond the configured depth limit or 256-entry safety cap
- Server-specific namespaces unless explicitly added to a custom safe registry

Unsupported items remain viewable and retain stable fingerprints, but should not
produce strong opportunity alerts later.

## Canonicalization

- Missing namespaces become `minecraft:` and IDs use lowercase.
- Enchantments sort by normalized ID and level.
- CRLF/CR lore endings become LF.
- Legacy formatting codes are case-normalized and retained. Even when the plain
  text matches the default item name, formatting makes it a custom name.
- JSON text-component object keys are sorted recursively.
- Normal display names derived from the item ID are omitted; custom names remain.
- Container entry order is ignored. Equivalent split stacks are aggregated.
- Top-level stack count is not part of item identity; contained quantities are.
- Unknown field names are retained, but their undocumented values are not
  interpreted. Their presence forces `UNSUPPORTED`.

The canonical representation is compact JSON and is hashed directly with
SHA-256. Both JSON and hash are persisted.

Example for a normal Netherite Ingot:

```json
{"formatVersion":1,"matchType":"COMMODITY","itemId":"minecraft:netherite_ingot","customName":null,"lore":[],"enchantments":[],"trim":null,"contents":[],"contentsTruncated":false,"unrecognizedFields":[]}
```

```text
9c287234bd49f75c8084a1f5f0963c605b1560789fa321c94fa339bbae39ae85
```

Changing an enchantment level, custom name, lore, trim, or contained quantity
changes the hash. Reordering enchantments or container entries does not.

## Stack prices

`COMMODITY` and `VISIBLE_METADATA` items are eligible for stack normalization:

```text
unit price = total price / stack count
```

Calculations use `BigDecimal` with DECIMAL128 precision. Exact item and container
prices are never divided automatically.
