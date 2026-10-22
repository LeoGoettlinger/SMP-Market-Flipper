# Authorization-gated automation

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

## Scope and permission

The automation framework is for a private/test server you control or a server whose operator
has explicitly permitted this kind of client automation. An API key is only an API credential;
it is not gameplay-automation permission. No DonutSMP or other public-server screen profile is
included.

The shipped configuration cannot execute a purchase: automation is disabled, dry-run is selected,
the server allowlist is empty, all monetary/session limits are zero, and no interaction profile
exists. The exact current-server allowlist is the server-access gate; there is no separate ARM SESSION control or typed authorization phrase. Transactional AH/Order actions and automatic sale require the relevant private profile, enabled module/mode, bounded nonzero limits, and all verification checks. Fishing cast/reel is the narrow exception: an exact allowlisted server suffices, and profile-free catches are retained without listing. Disconnect/server or allowlist changes cancel active work; the emergency stop remains available.

## State flow

```text
QUEUED -> PRECHECK -> WAITING_FOR_CONFIRMATION (confirm-each only)
       -> OPENING_AUCTION_HOUSE -> LOCATING_LISTING -> VERIFYING_LISTING
       -> PURCHASING -> VERIFYING_PURCHASE -> CALCULATING_RELIST_PRICE
       -> LISTING_FOR_SALE -> VERIFYING_LISTING_CREATED -> COMPLETED
```

Every failure is terminal and returns to a safe screen. A client restart converts any nonterminal
database row to `INTERRUPTED_REQUIRES_REVIEW`; it never resumes a purchase or relist.

In either `AUTOMATIC_AUTHORIZED_SERVER` (Auto) or `SMART_AUTHORIZED_SERVER` (Smart), successful immutable snapshot publications enqueue newly seen
`NEW` opportunities at the provider boundary. The queue holds at most eight entries, deduplicates
the opportunity ID and listing key, and keeps the coordinator single-flight. Before a queued entry
is submitted, it must still be `NEW`, have the same immutable item/seller/count/price/fingerprint,
contain valid canonical fingerprint metadata, and have a listing verified within 15 seconds.
Queue overflow, stale data, changed data, missing sellers, and missing metadata are journaled as
preflight failures without sending a command or clicking a slot. Auto processes scanner-approved
opportunities. Smart additionally checks the current item whitelist/blacklist against each refreshed
candidate immediately before submission; a filter change therefore rejects already-queued work.

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
   allowlist entry, and a configured private-server interaction profile.
3. Connect to the exact server and use **ALLOW CURRENT SERVER**. This saves that exact address; no separate ARM SESSION action or typed phrase is required.
4. Select `CONFIRM EACH` and enable automation for the first controlled test.
5. Press **RUN** on one opportunity, inspect the pending state, then press **CONFIRM TRADE** only after reviewing it.
6. Use **CANCEL EXECUTION** to cancel pending work or **EMERGENCY STOP** to block all new work and cancel the active workflow. Once idle, the emergency-stop control becomes **CLEAR STOP**; clearing it does not change your mode, server allowlist, profile, or other settings.

After confirm-each testing, selecting Auto (`AUTOMATIC_AUTHORIZED_SERVER`) or Smart
(`SMART_AUTHORIZED_SERVER`) with Automation enabled and the current exact server allowlisted enables
provider-driven submission. No GUI needs to be open: the next successful live snapshot publication
immediately queues new actionable opportunities. `RUN` remains available for explicit execution.

Do not use Auto or Smart until the complete profile and failure behavior have been tested on
disposable server data. The mod does not bypass anti-cheat, CAPTCHAs, rate limits, or
server restrictions.

## Fishing workflow

Fishing shares the global switch and exact-server allowlist. The dedicated Fishing tab starts a
Fishing-only module set (AH Deals and Order→AH remain off), chooses Auto
(`AUTOMATIC_AUTHORIZED_SERVER`), and enables Automation. This is a clear start/stop control, not a
separate authorization step: every tick still requires the exact connected address to be allowlisted.
Stop disables global Automation as well as the Fishing module, so enabling another module later
cannot silently resume automatic execution.
Changing server cancels active work, and the new address must independently be allowlisted. No API
key is required when Packet Scan is selected.

Catching and reeling do not require an auction profile. The client casts only with a fishing rod in
the hotbar and at least one free inventory slot. It reads the server-synchronised vanilla bobber bite
flag, waits the configured action delay, reels, and checks inventory before/after. It never guesses a
bite from bobber motion. If no profile is configured, a newly detected catch is kept in inventory;
SC-Market does not list it. If a profile is configured, existing matching items are still ineligible:
an item fingerprint must have been absent before the first catch in the current fishing session, or
already be tracked as loot caught by that session.

Automatic listing requires a validated private interaction profile, fresh market evidence, and the
usual exact-server/action gates. The price policy has two explicit evidence paths:

1. **Completed-sales path:** requires the configured minimum number of comparable sales (at least
   three; eight by default) and a most-recent sale within 24 hours. The candidate is bounded by the
   lower quartile and weighted median of sales. When there are at least two fresh active asks from
   two sellers, the lowest ask must be between 70% and 150% of the sale anchor; otherwise the
   evidence is considered suspicious and listing is refused. A credible ask can only lower the
   conservative sale-derived candidate (the policy undercuts it by one currency unit where relevant).
2. **Ask-only path:** used when there is no adequate recent sale feed, including Packet Scan mode.
   It requires at least five current active listings, three distinct sellers, both a lowest and median
   ask, and a lowest-to-median ratio of at least 70%. The proposed price is one currency unit below
   the lowest ask, rounded down to whole currency. This is a competitive asking-price estimate—not
   a completed sale, a guaranteed fair value, or proof that the item will sell. Ask observations older
   than ten minutes are excluded. If either path lacks sufficient, fresh, supported evidence, the catch
   remains in inventory and is retried later. Unsupported/approximate item matches are not priced.

Packet Scan has no transaction-history feed: it sees only visible listing snapshots. It captures the
configured exact-title auction screen after a complete inventory snapshot has been quiet for 300 ms.
It does not open the auction screen or click next-page controls. To build broader evidence without an
API key, leave that screen open and manually visit pages; listings are retained as observations but
stop contributing to current ask statistics if not reconfirmed within ten minutes. API Detection can
provide completed transactions and is preferable for a sale-based estimate.

The listing operation uses the configured `listingCommandTemplate` and `listingCreatedTitle`. It
selects the exact caught stack, rechecks authorization immediately before the command, then requires
the exact configured confirmation screen and the exact inventory decrease. If any precondition or
verification fails, the catch is retained and no further listing command is sent.
