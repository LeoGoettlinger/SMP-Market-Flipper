# SC-Market

> Project-wide agent guidance, user requirements, architecture, safety invariants, and the current verification boundary are in [Agent.md](Agent.md).

**Author:** SampleCraft

A client-side market scanner and opt-in automation toolkit for Minecraft Java Edition. SC-Market records observed listings and completed sales, estimates conservative values, reports opportunities, and provides independently switchable Fishing/AH Deals workflows plus a fail-closed Order→AH strategy core for authorized private/test servers.

> **Compatibility / verification status:** the current build configuration targets **Minecraft 26.2**, Java 25, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, non-remapping Loom, and Mojang names. Commit `069392322d1ba849d3c9b9c89b1015ba0527a86d` passed CI run [39812308743](https://github.com/LeoGoettlinger/SMP-Market-Flipper/actions/runs/39812308743): tests/build and the 26.2 client launch/render smoke succeeded. This verifies compilation and startup/UI rendering, **not live fishing or private-server market behavior**; no live-server integration is claimed. See [Agent.md](Agent.md) and the [26.2 port report](docs/PORTING_26_2.md) for the exact boundary.

## Start here

1. Install Fabric Loader and Fabric API versions compatible with Minecraft 26.2.
2. Build with Java 25 using `./gradlew test build --no-daemon`.
3. Put the mod JAR from `build/libs/` into `.minecraft/mods` and start the matching 26.2 game.
4. Press **Right Shift** to open or close the ClickGUI; press **Escape** to close it.
5. Packet Scan is the default data source and does not need an API key. Open Settings to see its status.
6. For Fishing, connect to a server you control or have permission to automate, click **ALLOW CURRENT SERVER**, leave a rod and free inventory slot, then click **START FISHING · PRIVATE SERVER**. Catches are retained without a market profile; automatic selling is separately guarded. AH Deals has Auto/Smart start buttons, but buys still need the verified private profile, balance provider, fresh prices, and every transaction gate. Order→AH is locked until its full-order UI is verified.

## Download the built mod from GitHub Actions

The current branch's CI run has uploaded the installable JAR as `sc-market-069392322d1ba849d3c9b9c89b1015ba0527a86d` (sources JAR excluded; 14-day retention). Open the repository's **Actions → Verify**, select [run 39812308743](https://github.com/LeoGoettlinger/SMP-Market-Flipper/actions/runs/39812308743), download its ZIP under **Artifacts**, and copy the `.jar` into the matching Minecraft 26.2 `mods` folder. Future successful `test build` runs upload `sc-market-<commit SHA>` before the GUI smoke, so check the smoke result separately; a downloadable build is not proof of live-server behavior. The workflow can also be run manually from Actions using **Run workflow**.

## ClickGUI controls

### Settings

- **Mod switch** in the window header pauses or resumes scanning and HUD alerts immediately.
- **One tab per module:** Fishing, AH Deals, and Order→AH each have a dedicated page. Starting Fishing or an AH Deals mode activates only that module; stopping it disables automation and clears active module selections. Fishing has a one-click Auto start; AH Deals offers Auto and Smart. Order→AH remains read-only/locked until its full-order parser and claim state are verified.
- **Auto** runs scanner-approved AH Deals opportunities automatically after every safety check. **Smart** is also automatic, but additionally rechecks the current whitelist/blacklist immediately before submitting, so queued work is rejected if filters changed. Both keep exact allowlisting, current price/item checks, private profiles, transaction verification, and emergency stop. Choose Auto or Smart directly in the AH Deals tab; Dry Run and Confirm Each remain available in Automation.
- **Detection source** switches between API mode and **Packet Scan**. Packet Scan is the default, needs no API key, and is restored automatically if an old saved API preference has no key. It passively reads only the configured auction screen; a matching private-screen profile is still needed to parse prices.
- **API credential** can be saved or removed in the panel. It is stored separately in `config/sc-market/api-key.txt`, not in `config.json`. Saving a credential while API Detection is selected reconnects the client live.
- **Order→AH limits and item whitelist** are editable in the **Order→AH** tab. The default size is 1,024; the UI allows up to the screenshot-supported 2,048-item size, while total and per-item order limits never exceed the server-observed 10/2 ceilings. These settings do not unlock the order GUI interaction.
- **Item ID filters** (whitelist and blacklist) are editable in **Filters** and are rechecked by Smart mode immediately before an action.
- **Local action capture** is an explicit opt-in in the **Order→AH** tab. It saves a bounded JSONL file under `config/sc-market/diagnostics/`; chat and typed text are excluded. Review [Local action capture](docs/LOCAL_ACTION_CAPTURE.md) before sharing a file. The exact player-facing steps are in [Developer function: local Order/AH capture](docs/DEVELOPER_FUNCTION.md).

### AH Deals

The dedicated tab starts Auto or Smart without an API key when Packet Scan is selected. Packet Scan still needs the private auction-screen profile to parse listing prices. The tab shows whether the profile and transactional balance gate are ready; shipped defaults intentionally block live buys until the private profile, fresh evidence, balance verification, and all transaction checks are valid. Starting the module does not bypass those gates.

### Order → AH

The Order economics, candidate ranking, configurable order size (1,024 by default, up to 2,048), minimum profit, 10-total/2-per-item capacity ceilings, per-item whitelist, separate completion-observation ledger, and serial order coordinator are implemented with focused tests. Settings persist to `order-module.properties`; the item whitelist and limits are editable in the **Order→AH** tab. The coordinator can consume these settings live, re-reads Order/AH quotes before each new order and every stack sale, collects at most one fully completed order per cycle, and never treats partial deliveries as independent sales. It refuses stale/thin evidence, unprofitable candidates, and fill forecasts beyond four days.

**Live Order GUI actions remain fail-closed and are not yet connected.** The supplied screenshots do not show the tooltip/lore for an individual Order row or the completed-order detail/claim screen, so SC-Market cannot yet verify the server's order ID, current unit price/fill state, delivery time, or exact claim control. Do not enable automatic Order→AH actions until a private profile adapter can verify those details and the displayed deposit, 1% fee, and total against the fresh plan. Reference screenshots and the detailed contract are in [Order→AH](docs/ORDER_TO_AH.md).

### Fishing

Fishing has a dedicated **Fishing** tab. Its **START FISHING · PRIVATE SERVER** button enables only the Fishing module, selects `AUTOMATIC_AUTHORIZED_SERVER`, and leaves AH Deals/Order modules off. The exact current server must still be allowlisted; the start button does not authorize a server. **STOP FISHING** disables Automation as well as the Fishing module, so later enabling another module cannot silently resume execution. The global Mod switch and emergency stop also pause it.

Casting and reeling work without an API key and without an auction profile: SC-Market uses the server-synchronized bite flag, waits the configured action delay, reels, and checks inventory changes. It does not infer a bite from bobber motion and refuses to cast without a fishing rod in the hotbar and an empty inventory slot. Without a verified private auction profile, catches are kept in inventory and **not** listed.

Automatic sale requires the private profile and fresh, supported market evidence. A caught item's exact fingerprint must be new to this fishing session; pre-existing matching inventory is never eligible. With recent sale history, the price is bounded by the lower quartile and weighted median, and credible asks can only lower it. Without sale history—including Packet Scan—the ask-only path requires at least five fresh active listings from at least three sellers; the lowest ask must be at least 70% of the ask median. Asks older than ten minutes, sparse evidence, or suspicious prices mean the catch stays in inventory. Packet Scan needs no API key, but it still needs the configured private AH screen profile and fresh visible listings to price automatic sales.

Before live Fishing, connect only to a server you control or whose operator explicitly permits this automation, add its exact address using **ALLOW CURRENT SERVER**, keep a fishing rod in the hotbar, and leave an empty inventory slot. Configure the private profile only if automatic listing is wanted. The allowlist remains the authorization gate; the profile and transaction checks protect actions performed on that server.

## Market data sources

- **API Detection** uses the official DonutSMP API, including recently listed auctions and completed transactions. It is the recommended source for price evidence when an API credential is available.
- **Packet Scan** is the default when no API key is configured and needs no credential. It passively captures the exact private auction-house screen configured in `automation.interactionProfile`; keep that screen open while its page loads, then manually visit pages to broaden the sample. It ingests only a complete inventory snapshot quiet for 300 ms, captures visible listings and configured price/seller lore (not completed sales), and never issues `/ah` or clicks page controls. Asks expire from current-price analysis after ten minutes without reconfirmation.

Useful sale-backed fair values and trend estimates need enough completed sales plus active market evidence; without transactions they may remain unavailable even when Packet Scan has captured asks. An API key authenticates API requests only; it is not permission to automate gameplay.

## Private-server configuration and safeguards

No public-server auction screen or command profile is included. Configure `automation.interactionProfile` in `config/sc-market/config.json` for an authorized private/test server. The profile defines exact screen titles, slots, lore markers and command templates. See [Automation profile setup](docs/AUTOMATION.md) and [the example configuration](docs/config.example.json).

Real gameplay actions require all of these gates:

- The **exact connected address** is on the explicit server allowlist; wildcard addresses are rejected.
- AH buying/relisting and automatic fishing sales require a validated private-server interaction profile. Each action rechecks item identity, inventory, exact confirmation screen, and resulting inventory delta. Fishing cast/reel can run without the profile; catches are retained and never listed until the profile and price evidence are ready.
- Automation is explicitly enabled, the selected mode and module are correct, and the current exact server address is on the allowlist. No typed session-arm phrase is required. Disconnects/server changes cancel active work; the exact allowlist, private profile, configured limits, safety checks, and emergency stop still apply.

The initial configuration is disabled, dry-run, and has no allowlisted server or interaction profile. Fishing and AH Deals use the same exact-server/profile safety gates. This mod does not bypass anti-cheat, CAPTCHAs, rate limits or server restrictions. Transparent action pacing is only a user-configurable delay; it is not anti-cheat evasion.

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
