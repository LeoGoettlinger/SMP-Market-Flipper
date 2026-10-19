# Donut Algorithm

**Author:** SampleCraft

A client-side auction-market scanner and opt-in automation toolkit for Minecraft Java Edition. It records observed listings and completed sales, estimates conservative values, reports opportunities, and includes a guarded Fishing workflow for authorized private/test servers.

> **Compatibility:** this repository targets **Minecraft 1.21.11**, Yarn mappings, Fabric Loader 0.19.x and Java 21. Minecraft 26.2 is not a supported build target here. The 26.2 port requires the official-name mapping/API migration and is not enabled by merely changing version numbers.

## Start here

1. Install Fabric Loader and Fabric API for Minecraft 1.21.11.
2. Build with Java 21 using `./gradlew build`.
3. Put the remapped JAR from `build/libs/` into `.minecraft/mods` and start the game.
4. Press **Right Shift** to open or close the ClickGUI; press **Escape** to close it.
5. In Settings, choose the workflow and data source. Changes to the workflow, data source and API credential apply live; Minecraft does not need to restart.

## ClickGUI controls

### Settings

- **Mod switch** in the window header pauses or resumes scanning and HUD alerts immediately.
- **Workflow** switches between `FLIPPER` and `FISHING`.
- **Detection source** switches between `API Detection` and `Packet Scan`.
- **API credential** can be saved or removed in the panel. It is stored separately in `config/donut-flip-scanner/api-key.txt`, not in `config.json`. Saving a credential while API Detection is selected reconnects the client live.

### Flipper

The existing analysis workflow scans market evidence and surfaces candidates. Purchases/listings remain separately controlled from the Automation panel. Dry-run is the initial execution mode; it does not click, purchase or list.

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

No public-server auction screen or command profile is included. Configure `automation.interactionProfile` in `config/donut-flip-scanner/config.json` for an authorized private/test server. The profile defines exact screen titles, slots, lore markers and command templates. See [Automation profile setup](docs/AUTOMATION.md) and [the example configuration](docs/config.example.json).

Real gameplay actions require all of these gates:

- The **exact connected address** is on the explicit server allowlist; wildcard addresses are rejected.
- A valid private-server interaction profile is configured. Before a listing, the item fingerprint and count are rechecked; completion requires the exact configured confirmation screen and inventory delta.
- The automation setting is enabled and the user arms the current session with the exact phrase `I HAVE AUTHORIZATION`. Authorization is memory-only and clears on disconnect, server change, restart, workflow/mode change, allowlist change, disarm or emergency stop.

The initial configuration is disabled, dry-run, and has no allowlisted server or interaction profile. Fishing and Flipper use the same exact-server/profile/session protections. This mod does not bypass anti-cheat, CAPTCHAs, rate limits or server restrictions. Transparent action pacing is only a user-configurable delay; it is not anti-cheat evasion.

## Configuration and local data

```text
config/donut-flip-scanner/config.json               # filters, limits, private auction profile
config/donut-flip-scanner/api-key.txt                # separate API credential
config/donut-flip-scanner/market-detection-mode.txt  # API or packet source
config/donut-flip-scanner/runtime-settings.txt       # global switch and workflow
config/donut-flip-scanner/market-data.db             # local market history
config/donut-flip-scanner/exports/                   # user exports
config/donut-flip-scanner/backups/                   # configuration/database backups
```

For API credential details see [API integration](docs/API_INTEGRATION.md). For exact feature and safety-check behavior, see [Features and safety](docs/FEATURES_AND_SAFETY.md). For the automation state machine, private profiles, limits and safe testing sequence see [Automation](docs/AUTOMATION.md). For UI/backend wiring see [GUI/backend integration](docs/GUI_BACKEND_INTEGRATION.md).
