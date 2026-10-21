# SC-Market

**Author:** SampleCraft

A client-side market scanner and opt-in automation toolkit for Minecraft Java Edition. SC-Market records observed listings and completed sales, estimates conservative values, reports opportunities, and provides independently switchable Fishing/AH Deals workflows plus a fail-closed Order→AH strategy core for authorized private/test servers.

> **Compatibility / port status:** the working build is still **Minecraft 1.21.11**, Yarn mappings, Fabric Loader 0.19.x and Java 21. Minecraft 26.2 is **not yet supported**. The 26.2 mapping/API migration has been researched, but this checkout has not been migrated; do not install it on 26.2. The current sandbox lacks an accessible Gradle distribution and Fabric Maven host, so the requested 26.2 build could not be executed here. See the [26.2 port report](docs/PORTING_26_2.md).

## Start here

1. Install Fabric Loader and Fabric API for Minecraft 1.21.11.
2. Build with Java 21 using `./gradlew build`.
3. Put the remapped JAR from `build/libs/` into `.minecraft/mods` and start the game.
4. Press **Right Shift** to open or close the ClickGUI; press **Escape** to close it.
5. In Settings, independently toggle Fishing, AH Deals, and Order→AH, and choose the data source. Changes apply live; toggling modules invalidates the current session authorization and requires re-arming.

## ClickGUI controls

### Settings

- **Mod switch** in the window header pauses or resumes scanning and HUD alerts immediately.
- **Modules** toggle `FISHING`, `AH DEALS`, and `ORDER → AH` independently; multiple may be selected. The global **Mod** switch still pauses everything.
- **Detection source** switches between `API Detection` and `Packet Scan`.
- **API credential** can be saved or removed in the panel. It is stored separately in `config/sc-market/api-key.txt`, not in `config.json`. Saving a credential while API Detection is selected reconnects the client live.
- **Order→AH limits and item whitelist** are editable under **Item Filters → Order → AH Settings**. The default size is 1,024; the UI allows up to the screenshot-supported 2,048-item size, while total and per-item order limits never exceed the server-observed 10/2 ceilings.
- **Local action capture** is an explicit opt-in in that same panel. It saves a bounded JSONL file under `config/sc-market/diagnostics/`; chat and typed text are excluded. Review [Local action capture](docs/LOCAL_ACTION_CAPTURE.md) before sharing a file.

### AH Deals

This existing workflow scans AH/API evidence, surfaces candidates, and includes the guarded AH buy/relist state machine. Purchases/listings remain separately controlled from the Automation panel. Dry-run is the initial execution mode; it does not click, purchase, or list.

### Order → AH

The Order economics, candidate ranking, configurable order size (1,024 by default, up to 2,048), minimum profit, 10-total/2-per-item capacity ceilings, per-item whitelist, separate completion-observation ledger, and serial order coordinator are implemented with focused tests. Settings persist to `order-module.properties`; the item whitelist and limits are editable in **Item Filters → Order → AH Settings**. The coordinator can consume these settings live, re-reads Order/AH quotes before each new order and every stack sale, collects at most one fully completed order per cycle, and never treats partial deliveries as independent sales. It refuses stale/thin evidence, unprofitable candidates, and fill forecasts beyond four days.

**Live Order GUI actions remain fail-closed and are not yet connected.** The supplied screenshots do not show the tooltip/lore for an individual Order row or the completed-order detail/claim screen, so SC-Market cannot yet verify the server's order ID, current unit price/fill state, delivery time, or exact claim control. Do not enable automatic Order→AH actions until a private profile adapter can verify those details and the displayed deposit, 1% fee, and total against the fresh plan. Reference screenshots and the detailed contract are in [Order→AH](docs/ORDER_TO_AH.md).

### Fishing

Fishing mode uses the player's selected hotbar fishing rod, detects the server-synchronised bite state, waits for the configured action delay, reels, then checks for inventory changes. It does not infer a bite from bobber motion. It will not cast without an available inventory slot.

Automatic sale is more restrictive than catching. A caught item is eligible only when its exact fingerprint was absent before this fishing session (or is already tracked as loot caught by this session). Existing matching inventory items are never automatically sold. With recent sale history, the price is bounded by the lower quartile and weighted median, and credible asks can only lower it. Without sale history (including Packet Scan), an ask-only estimate is allowed only with at least five fresh active listings from at least three sellers; the lowest ask must be at least 70% of the ask median, and the mod lists one currency unit below it. Active asks older than ten minutes are excluded. This ask-only value is explicitly an estimate, not evidence of a completed sale. Sparse, stale, unsupported, or suspicious market evidence means the catch stays in inventory.

To run Fishing automatically, all of the following must be true:

1. Select **Fishing** and keep the global **Mod** switch enabled.
2. In Automation, enable the authorized execution setting and choose `AUTOMATIC_AUTHORIZED_SERVER`.
3. Configure the private-server auction profile, add the exact current server using **ALLOW CURRENT SERVER**, then explicitly arm the session by typing `I HAVE AUTHORIZATION`.
4. Put a fishing rod in the hotbar, leave an empty inventory slot, and close other screens.
5. Use **Emergency Stop** or **Disarm** to stop further actions.

The profile's `listingCommandTemplate` must describe a legitimate sale command for your own private test server, and `listingCreatedTitle` must exactly match its successful listing confirmation screen. Automatic selling remains off when those requirements or safe market evidence are missing.

## Market data sources

- **API Detection** uses the official DonutSMP API, including recently listed auctions and completed transactions. It is the recommended source for price evidence when an API credential is available.
- **Packet Scan** captures the configured auction-house screen from server packets without an API key. Open the exact configured screen and keep it open while the page loads; only a complete inventory snapshot that has been quiet for 300 ms is ingested. To broaden the no-key market sample, manually visit other pages; the scanner does not issue `/ah`, click page controls, or automatically traverse them. It captures visible listing items, configured price/seller lore and supported item metadata—not completed sales. Reconfirmed asks expire from price analysis after ten minutes without a fresh observation. Fishing may use the guarded ask-only estimate described above; it never invents transaction history.

Useful sale-backed fair values and trend estimates need enough completed sales plus active market evidence; without transactions they may remain unavailable even when Packet Scan has captured asks. An API key authenticates API requests only; it is not permission to automate gameplay.

## Private-server configuration and safeguards

No public-server auction screen or command profile is included. Configure `automation.interactionProfile` in `config/sc-market/config.json` for an authorized private/test server. The profile defines exact screen titles, slots, lore markers and command templates. See [Automation profile setup](docs/AUTOMATION.md) and [the example configuration](docs/config.example.json).

Real gameplay actions require all of these gates:

- The **exact connected address** is on the explicit server allowlist; wildcard addresses are rejected.
- A valid private-server interaction profile is configured. Before a listing, the item fingerprint and count are rechecked; completion requires the exact configured confirmation screen and inventory delta.
- The automation setting is enabled and the user arms the current session with the exact phrase `I HAVE AUTHORIZATION`. Authorization is memory-only and clears on disconnect, server change, restart, workflow/mode change, allowlist change, disarm or emergency stop.

The initial configuration is disabled, dry-run, and has no allowlisted server or interaction profile. Fishing and Flipper use the same exact-server/profile/session protections. This mod does not bypass anti-cheat, CAPTCHAs, rate limits or server restrictions. Transparent action pacing is only a user-configurable delay; it is not anti-cheat evasion.

## Configuration and local data

```text
config/sc-market/config.json               # filters, limits, private auction profile
config/sc-market/api-key.txt                # separate API credential
config/sc-market/market-detection-mode.txt  # API or packet source
config/sc-market/runtime-settings.txt       # global switch and independent module set
config/sc-market/order-module.properties   # Order→AH limits, whitelist and capture opt-in
config/sc-market/diagnostics/              # local action captures, only when enabled
config/sc-market/market-data.db             # local market history
config/sc-market/exports/                   # user exports
config/sc-market/backups/                   # configuration/database backups
```

On first launch, existing files from the historical `config/donut-flip-scanner/` folder are copied into `config/sc-market/` only when the destination file does not already exist. The old folder is retained as a backup; databases, exports, API credentials, and settings are never deleted or overwritten by migration. The previous single-workflow runtime setting is also read and migrated to the independent module format.

For API credential details see [API integration](docs/API_INTEGRATION.md). For exact feature and safety-check behavior, see [Features and safety](docs/FEATURES_AND_SAFETY.md). For the automation state machine, private profiles, limits and safe testing sequence see [Automation](docs/AUTOMATION.md). For the Order→AH contract and current live-GUI boundary, see [Order→AH](docs/ORDER_TO_AH.md). For UI/backend wiring see [GUI/backend integration](docs/GUI_BACKEND_INTEGRATION.md). For the local diagnostic capture format and privacy boundaries, see [Local action capture](docs/LOCAL_ACTION_CAPTURE.md).
