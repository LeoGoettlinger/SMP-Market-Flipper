# Donut Algorithm

Donut Algorithm is a client-side Fabric mod for Minecraft 1.21.11. It provides a
spacious dark trading dashboard, mock fallback data, an opt-in asynchronous DonutSMP
API client, local SQLite history, conservative market evaluation, live provider
adapters, bounded HUD alerts, release diagnostics, and an authorization-gated automation
framework for private servers you own or are explicitly permitted to automate.

The installed mod starts in **mock mode when no API key is present**. When the separate
credential file exists, it automatically initializes SQLite off-thread, attaches the
live providers, starts the official DonutSMP listing/transaction poller, and switches
the dashboard to real market records. Trade automation is independently disabled by
default and cannot be enabled merely by adding the market API key.

## Supported environment

- Minecraft 1.21.11
- Fabric Loader 0.19.3
- Fabric API 0.141.4+1.21.11
- Yarn 1.21.11+build.6
- Java 21
- Gradle 9.4.0 wrapper (distribution checksum pinned)

## Build and test

```powershell
.\gradlew.bat clean build
```

macOS/Linux:

```sh
./gradlew clean build
```

The release JAR is `build/libs/donut-algorithm-0.1.0.jar`. The build runs the full
JUnit suite, including repository, mock HTTP, lifecycle, configuration migration,
corruption recovery, security-boundary, HUD queue, and full opportunity-pipeline
tests. Tests use fake HTTP transports and never contact DonutSMP.

## Install and use

1. Install Fabric Loader for Minecraft 1.21.11.
2. Put Fabric API 0.141.4+1.21.11 and the built JAR in Minecraft's `mods` folder.
3. Start Minecraft with Java 21.
4. Press **Right Shift** to open or close the ClickGUI. Escape closes it.

### Enable live prices

1. In DonutSMP, use `/api` to create a key.
2. Close Minecraft.
3. Create `.minecraft/config/donut-flip-scanner/api-key.txt` and paste the key only.
   Do not include the word `Bearer`; normal editor-added whitespace is trimmed.
4. Start Minecraft again. The status bar changes from `API: Mock Data` to
   `Connecting`, then `Connected` after the first successful official API response.

The client immediately polls recently listed auctions, completed transactions, and
the first active-listing page. It then checks recent listings every 5 seconds and
completed transactions every 30 seconds. Transaction page 1 is sampled every other poll,
with pages 2-10 interleaved for historical evidence, while active-listing pages 1-10 rotate
every 2 minutes. Listing prices shown by live opportunity cards come directly
from the official response. Fair value, profit, ROI, and confidence are locally
calculated from completed-sale evidence, so those cards can take a short warm-up and
will remain empty when the conservative requirements are not met.

Actionable cards are fail-closed on availability: the joined listing must have been
reconfirmed by a successful auction response within the last 15 seconds. Every card
shows `VERIFIED ... ago`; once that window is exceeded, it is removed from Opportunities
and recorded in History as `NO_LONGER_AVAILABLE`. An unchanged API page still advances
verification time, but a failed request never does.

The live opportunity profile gives every category equal ordering and retains the normal
newest-first opportunity sequence. Ordinary vanilla blocks, building materials, food,
farming items, redstone components, transport items, decorations, utility items, ores,
and materials are eligible commodity markets. Tools are compared regardless of durability,
but enchantments, names, lore, and trims remain separate variants. The current relaxed gates
are 5% confidence, four comparable sales, four primary fair-value sales, and two trend sales.
Severe-risk and stale/unverified-listing rejection are unchanged.

The non-pausing GUI uses a 574 x 350 logical canvas, square edges, translucent gray
gradients, a Geist Sans interface font, a provider-driven market chart, and a
dense eight-trade opportunity board. It scales down to remain safe at smaller GUI
sizes. Live HUD alerts are rendered from immutable live provider records only; mock
mode does not enqueue them.

Tab changes use a short elapsed-time dissolve transition, independent of frame rate.

### Track personal realized profit

After manually buying a live opportunity, click its `BOUGHT` button and confirm the exact purchase.
The client stores the official listing price as cost basis. When the official completed-transaction
feed later reports your sale of the same normalized item variant and stack count, the dashboard
chart adds `sale proceeds - purchase cost`. The ledger persists across restarts and never counts a
completed sale twice.

The chart is intentionally labeled **gross** because the consumed official schema does not document
auction fees. It tracks confirmed scanner flips, not balance changes or all account activity; an
identical pre-existing item cannot be distinguished from the confirmed lot. See
[personal profit tracking](docs/PERSONAL_PROFIT.md) for the exact matching boundary.

The header uses the supplied DonutSMP logo as its sole top-left branding.

When live mode has a valid API credential, the Automation tab now reads the logged-in
Minecraft profile's `money` value from the official `/v1/stats/{user}` endpoint. The
non-blocking provider caches the result for 30 seconds, displays refresh/error states,
and never performs file or network access from a render method. This balance is read-only
profile data; it is not treated as transactional proof that an auction purchase succeeded.

## Authorization-gated automation

The **Automation** tab contains dry-run, confirm-each, authorized automatic, allowlist,
per-session arming, disarm, confirmation, and emergency-stop controls. The default is:

- `enabled: false`
- `mode: DRY_RUN`
- empty server allowlist
- zero purchase/exposure/session limits
- no auction interaction profile

Dry run uses a separate adapter and never sends commands or clicks slots. Real modes require
all of the following: nonzero limits, an exact current-server allowlist entry, a custom screen
profile, the exact per-session phrase `I HAVE AUTHORIZATION`, and a matching configured mode.
Changing server, mode, or allowlist disarms the session. Restarting the client never restores
arming, and unfinished executions are marked `INTERRUPTED_REQUIRES_REVIEW` instead of resumed.
The default `requireBalanceVerification: true` also keeps real execution blocked because the
official stats balance is cached read-only profile data, not a transactional debit verifier.
Gameplay automation is explicitly blocked for `donutsmp.net` and its subdomains in accordance
with the server's published rules.

There is deliberately no built-in DonutSMP or other public-server interaction profile. The
official market API key only permits read-only API requests; it does not grant permission to
automate gameplay. Only create a custom profile for a private/test server you control or a
server whose operator has given explicit permission. See
[automation safety and profile contract](docs/AUTOMATION.md).

When an explicitly authorized session uses `AUTOMATIC_AUTHORIZED_SERVER`, immutable snapshot
publication—not GUI rendering—feeds a bounded eight-entry queue. New opportunities are deduplicated
by opportunity ID and listing key, revalidated immediately before execution, and then processed by
the existing single-flight coordinator. The configured adapter sends the auction chat command,
scans the bounded result pages for one exact item fingerprint/count/price/seller match, confirms the
purchase, verifies the inventory delta, places the unique purchased stack in hand, sends the priced
sell command, and verifies the final inventory delta. Any stale, changed, missing, or ambiguous state
fails closed and is written to the execution journal.

## Local files

The persistence APIs use:

```text
config/donut-flip-scanner/config.json
config/donut-flip-scanner/api-key.txt
config/donut-flip-scanner/market-data.db
config/donut-flip-scanner/exports/
config/donut-flip-scanner/backups/
```

The API key is deliberately absent from `config.json`, filter exports, diagnostics,
logs, and scanner snapshots. `FileApiCredentialsStore` writes programmatically supplied
keys with owner-only POSIX permissions or an owner-only Windows ACL. The client reads
the separate file on demand, keeps the scanner state credential-free, and falls back
to mock mode if the file is missing or live startup cannot complete safely.

See [release hardening](docs/RELEASE_HARDENING.md),
[API integration](docs/API_INTEGRATION.md), [database design](docs/DATABASE.md),
and [GUI/backend integration](docs/GUI_BACKEND_INTEGRATION.md).
