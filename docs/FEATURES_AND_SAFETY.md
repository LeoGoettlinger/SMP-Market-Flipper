# What the mod does and what it checks

## Actual functions

### Market detection and analysis

- **API Detection** reads documented auction listings, recently listed items, and completed-transaction pages using the key stored in `config/donut-flip-scanner/api-key.txt` (or saved in Settings). The API key authenticates read requests; it does not authorize gameplay automation.
- **Packet Scan** works without an API key. It reads auction item stacks and updates from the Minecraft client only while the exact configured results-screen title is open. It parses the configured result-slot range, visible item lore for price/seller, and supported item metadata. A complete inventory snapshot must arrive, then remain quiet for 300 ms before capture. It does not send a command, click the GUI, or traverse pages. The user must open the market and manually visit pages to broaden the sample.
- Packet Scan does **not** observe completed sales. A listing missing from a page is not treated as sold. Current ask statistics exclude active listings that have not been reconfirmed within ten minutes. Thus no-key mode has a smaller and less authoritative market sample.
- Both sources feed the local market database and market analysis: comparable-item statistics, conservative fair-value estimates, confidence/risk/opportunity analysis, and the UI/HUD opportunity list. Sale-backed fair values and trend estimates may remain unavailable in Packet Scan when no completed-sale history exists; active asks are not represented as completed transactions. The ask-only fallback is specifically for guarded Fishing listing quotes. The scanner itself is read-only; a separate automation adapter is needed for game actions.

### Flipper workflow

The Flipper workflow surfaces candidates and contains a private-profile buy/relist state machine. **The current adapter is coded to search for and buy a unique AH listing, then relist it through the configured AH listing command. It does not read or place buy Orders and does not collect completed Orders.** Live execution is still blocked by shipped defaults: automation is disabled, mode is `DRY_RUN`, the profile/allowlist are empty, and `requireBalanceVerification` is true. The coordinator hard-rejects live trades while that balance check is enabled because there is no transactional balance-verification provider. If manually configured for an authorized test environment, its AH flow still is not the Order→AH flow you described. Its `UNDERCUT_LOWEST_CREDIBLE_ASK` option currently falls back to the conservative fair-value price; it does not guarantee exactly one currency unit below the lowest AH listing. The verified AH listing price is already used as that trade's acquisition-cost basis for profit/ROI checks and relist floors, but is not added as a separate observation to market-wide completed-sales history. `DRY_RUN` is non-interacting; `CONFIRM_EACH` requires a confirmation at each execution; `AUTOMATIC_AUTHORIZED_SERVER` allows automatic submission after all gates pass. A global Mod switch pauses scanner execution; selecting Fishing stops Flipper dispatch. Workflow and source can be changed live; saved settings are applied without a Minecraft restart.

### Fishing workflow

Fishing uses the selected hotbar rod, the server-synchronized vanilla bobber bite flag, configurable action pacing (0-10 seconds in config; the loop enforces at least 750 ms between actions), and an inventory before/after comparison. It requires an empty inventory slot before casting. It queues only new loot attributable to the current session, checks market evidence, then may list the exact stack through the configured private-server listing command. It does not sell an identical item that was already in inventory before the catch. It does not use bobber motion guesses, and a catch without a price quote is kept rather than listed.

## Authorization and execution protections

These checks apply to live Flipper execution. Fishing additionally has its own stricter live predicate below.

1. **Disabled-by-default configuration:** shipped automation is disabled, in `DRY_RUN`, has no allowed server, no interaction profile, zero purchase/exposure limits, and no session purchases permitted. These values do not enable live actions by themselves.
2. **Exact server allowlist:** the current server address must be valid and present as an exact normalized allowlist entry. Wildcards and whitespace are rejected. A broad pattern is not interpreted as permission for other hosts.
3. **Explicit session arming:** the user must type exactly `I HAVE AUTHORIZATION` for the current allowlisted server. The armed server is session-only. Disconnect/server change, restart, workflow/mode change, allowlist edit, disarm, or emergency stop clears or invalidates authorization. The current server must still equal the armed server at execution time.
4. **Configured private profile:** there is no built-in public-server auction layout. Exact screen titles, bounded slots/pages, command templates and lore markers must be configured. Commands omit `/`, reject line breaks, sanitize substituted values, and reject unknown placeholders. A listing command must contain `{price}`.
5. **Mode and queue checks:** automation must be enabled and the requested mode must equal the configured mode. The live queue is bounded to eight and deduplicates opportunity/listing IDs; executions are single-flight. Opportunity data must still be `NEW`, immutable fields and canonical fingerprint metadata must match, and the listing must have been verified within 15 seconds.
6. **Economic and evidence prechecks:** reject stale/future opportunities, confidence or comparable-sale count below configured minimums, profit/ROI below limits, severe or unknown risk, listing price above request/configured maximum, session purchase cap, open-exposure cap, or active cooldown. Nonzero live limits are required. If `requireBalanceVerification` is enabled, live execution is rejected because the available profile balance is read-only rather than transactional proof.
7. **Exact listing identity:** the adapter scans every configured page and requires one unique matching item, component fingerprint, item ID, count, seller, price, and listing key when the server exposes one. It checks duplicates across pages, returns to the page, and re-reads the target immediately before clicking. Changed, ambiguous, missing, or unsupported data is rejected.
8. **Purchase/listing verification:** the confirmation screen title must match exactly and its configured slot must repeat the exact item identity. Purchase is not considered complete until inventory increases by the exact count. The exact purchased stack is selected and rechecked before relisting. A sale is not considered complete until the exact configured listing-created screen and expected inventory decrease are observed.
9. **Failure handling:** each failed step stops the state machine; it does not blindly retry a purchase or relist. A restart marks unfinished trades interrupted for review. Emergency stop cancels active work and blocks new work; clearing the stop does not re-arm. Duplicate opportunity/listing IDs cannot be submitted twice in the same session.

### Additional Fishing gates

Fishing live actions require **all** of the following on each tick: global Mod enabled; workflow `FISHING`; Automation enabled; mode `AUTOMATIC_AUTHORIZED_SERVER`; a configured interaction profile; a connected world/player/network; exact current server in the allowlist; a currently armed session for that same server; and no emergency stop. Fishing pauses if a GUI is open. Authorization is rechecked after the asynchronous price lookup and immediately before sending the listing command.

A rod must still be in the hotbar at cast/reel time; there must be a free inventory slot before casting. A catch must be confirmed by inventory delta after the server bobber bite/reel and settling delay. Existing identical inventory is not sale-eligible. If matching inventory changes outside the Fishing workflow, the sale is disabled for that item. Listing requires a positive price, the exact caught stack still present, the configured exact listing-created title, and an exact inventory decrease. Failed or unverified listings are retained; retries are delayed rather than immediate.

## Price policy, including no-key mode

For transaction-backed evidence, Fishing requires the configured comparable-sale minimum (at least three; default eight) and a most-recent sale within 24 hours. It uses the lower quartile/weighted median conservatively. If it uses current asks, it requires at least two asks from two sellers and rejects an ask outside 70%-150% of the sale anchor; a credible ask can only lower the candidate.

If no adequate sale feed is present, Fishing can use a lower-confidence **ask-only estimate**: at least five fresh active listings, three distinct sellers, lowest and median asks, and lowest ask at least 70% of median. It proposes one currency unit below the lowest ask, rounded down. This is not a completed-sale observation or a guaranteed fair value. If the evidence does not pass these checks—or the item match is unsupported/approximate—the caught item stays in inventory.

## Important boundaries

- This is client-side software. Configuration checks reduce accidental actions; they cannot establish that a server operator permits automation. The user must obtain permission and must not use the mod to bypass anti-cheat, CAPTCHA, rate limits, or server rules.
- Configurable action delays (“Ghost Mode”) are ordinary pacing between actions, not stealth, evasion, or anti-cheat bypass.
- Fishing and authorized auction interaction have not been end-to-end tested on a live server in this repository. A syntactically valid custom profile is not proof that its server semantics are correct; first validate it in `DRY_RUN` and a private disposable test environment.
