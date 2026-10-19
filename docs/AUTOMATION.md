# Authorization-gated automation

## Scope and permission

The automation framework is for a private/test server you control or a server whose operator
has explicitly permitted this kind of client automation. An API key is only an API credential;
it is not gameplay-automation permission. No DonutSMP or other public-server screen profile is
included.

The shipped configuration cannot execute a purchase: automation is disabled, dry-run is selected,
the server allowlist is empty, all monetary/session limits are zero, and no interaction profile
exists. Arming is memory-only and is cleared on restart, disconnect, server change, mode change,
allowlist change, disarm, or emergency stop.

## State flow

```text
QUEUED -> PRECHECK -> WAITING_FOR_CONFIRMATION (confirm-each only)
       -> OPENING_AUCTION_HOUSE -> LOCATING_LISTING -> VERIFYING_LISTING
       -> PURCHASING -> VERIFYING_PURCHASE -> CALCULATING_RELIST_PRICE
       -> LISTING_FOR_SALE -> VERIFYING_LISTING_CREATED -> COMPLETED
```

Every failure is terminal and returns to a safe screen. A client restart converts any nonterminal
database row to `INTERRUPTED_REQUIRES_REVIEW`; it never resumes a purchase or relist.

In `AUTOMATIC_AUTHORIZED_SERVER`, successful immutable snapshot publications enqueue newly seen
`NEW` opportunities at the provider boundary. The queue holds at most eight entries, deduplicates
the opportunity ID and listing key, and keeps the coordinator single-flight. Before a queued entry
is submitted, it must still be `NEW`, have the same immutable item/seller/count/price/fingerprint,
contain valid canonical fingerprint metadata, and have a listing verified within 15 seconds.
Queue overflow, stale data, changed data, missing sellers, and missing metadata are journaled as
preflight failures without sending a command or clicking a slot.

## Custom profile contract

An authorized private server must expose deterministic auction screens. Configure an
`interactionProfile` under the `automation` object only after validating the server plugin:

```json
{
  "profileId": "private-test-v1",
  "searchCommandTemplate": "privateah seller {seller}",
  "resultsScreenTitle": "Private Auction Results",
  "firstResultSlot": 0,
  "lastResultSlot": 8,
  "purchaseConfirmationTitle": "Confirm Private Purchase",
  "purchaseConfirmationSlot": 4,
  "listingCommandTemplate": "privateah sell {price}",
  "listingCreatedTitle": "Private Listing Created",
  "listingKeyLorePrefix": "",
  "priceLorePrefix": "Price: ",
  "sellerLorePrefix": "Seller: ",
  "nextPageSlot": 53,
  "previousPageSlot": 45,
  "maximumPages": 10,
  "ignoredLorePrefixes": ["Click to buy", "Expires: "]
}
```

The search command may use `{listing_key}`, `{seller}`, `{item_id}`, `{item_name}`, and `{price}`;
it may also be a static auction-open command. The listing command must contain `{price}`. Commands
omit the leading slash, all substituted values are sanitized, and unknown placeholders fail closed.
Titles are exact and case-sensitive. Slot ranges and page counts are bounded. Profiles with more
than one page require both next- and previous-page slots so the adapter can scan the configured
range, reject matches duplicated across pages, navigate back, and re-read the unique target.

Set `listingKeyLorePrefix` to an empty string when the private server does not expose the internal
listing key. Numeric price and seller markers remain mandatory. `ignoredLorePrefixes` identifies
server-added auction instructions that are not intrinsic item lore; configure these narrowly.
The confirmation slot must repeat the exact item, count, normalized component fingerprint, price,
and seller markers. A generic green confirmation button is intentionally insufficient.

Before clicking, the adapter requires exactly one matching normalized fingerprint, item ID, stack
count, price, seller, and listing ID when exposed. It compares all configured pages and checks the
target again immediately before the click. It refuses purchase attribution when an identical item
already exists in inventory. After confirmation it requires the exact inventory increase. If the
unique purchased stack is not already in the hotbar, it swaps it into the first empty hotbar slot
(or the currently selected slot when the hotbar is full), selects it, and verifies the held stack
before sending the sell command. Completion requires the exact listing-created screen and inventory
decrease.

## Operating sequence

1. Validate the private server profile in `DRY_RUN` mode.
2. Set conservative nonzero maximum purchase, exposure, session-count, age, cooldown, profit,
   ROI, confidence, and comparable-sale limits in `config.json` while Minecraft is closed.
   The live dashboard automatically reads the official player-stats `money` value when an API
   credential is configured. It is cached read-only profile data rather than transactional proof
   of a purchase debit, so it deliberately does not satisfy `requireBalanceVerification`.
   A future private-server provider must verify the pre/post debit atomically before that gate can
   be satisfied. Server addresses are not brand-restricted; execution still requires an exact
   allowlist entry, a configured private-server interaction profile, and explicit per-session arming.
3. Connect to the exact server and use **ALLOW CURRENT SERVER**.
4. Select `CONFIRM EACH` for the first live tests and enable automation.
5. Select **ARM SESSION** and type exactly `I HAVE AUTHORIZATION`.
6. Press **RUN** on one opportunity, inspect the waiting state, then press **CONFIRM TRADE**.
7. Use **DISARM** after the test. **EMERGENCY STOP** cancels the active workflow and blocks new work.
   Once idle, the same control becomes **CLEAR STOP**; clearing it never rearms the session.

After confirm-each testing, selecting `AUTOMATIC_AUTHORIZED_SERVER` and arming the session enables
provider-driven submission. No GUI needs to be open: the next successful live snapshot publication
immediately queues new actionable opportunities. `RUN` remains available for explicit execution.

Do not use `AUTOMATIC_AUTHORIZED_SERVER` until the complete profile and failure behavior have been
tested on disposable server data. The mod does not bypass anti-cheat, CAPTCHAs, rate limits, or
server restrictions.

## Fishing workflow

Fishing shares the global switch, exact-server allowlist, private interaction profile, and the
same memory-only per-session authorization. Select the `FISHING` workflow, enable Automation,
choose `AUTOMATIC_AUTHORIZED_SERVER`, add the exact current server, and arm the session with
`I HAVE AUTHORIZATION`. Changing workflow or server disarms the session. Flipper's market-opportunity
dispatcher is disabled while Fishing is selected, so queued buy opportunities cannot run in Fishing.

The client casts only with a fishing rod in the hotbar and at least one free inventory slot. It reads
the server-synchronised vanilla bobber bite flag, waits the configured action delay, reels, and then
compares the inventory before/after. It never guesses a bite from bobber motion. Existing matching
items are not eligible for automatic sale: an item fingerprint must have been absent before the
first catch in the current fishing session, or already be tracked as loot caught by that session.

For a catch to be listed, the local market database must have at least the configured minimum
number of comparable sales (at least three; eight by default) and a comparable sale no older than
24 hours. A lower-quartile/weighted-median sale estimate bounds the listing price. If active asks are
used, at least two listings from two sellers are required and the lowest ask must be within a
plausible 70%-150% band of the sale anchor. Missing, sparse, stale, or suspicious evidence means
the catch stays in inventory and is retried later; no fallback price is invented.

The listing operation uses the configured `listingCommandTemplate` and `listingCreatedTitle`. It
selects the exact caught stack, rechecks authorization immediately before the command, then requires
the exact configured confirmation screen and the exact inventory decrease. If any precondition or
verification fails, the catch is retained and no further listing command is sent. Packet Scan can
price only items for which the user has actually captured sufficient recent auction data; API
Detection is recommended for a warm, broad market history.
