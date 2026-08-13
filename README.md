# Donut Algorithm

This algorithm is made for the DONUTSMP auction house. It tracks arbitrage opportunities through previous data. This project was completely made with Ai, specifically GPT SOL 5.6

Credits: https://youtu.be/qK_7ByDtBbM?si=dSBCk3gnvKjUJ4mk 
Inspired the basis for the algorithm and how machine learning can use previous statistics to find price weakness. 🙏

## Install and use

1. Install Fabric Loader for Minecraft 1.21.11.
2. Put Fabric API 1.21.11 and the built JAR in Minecraft's `mods` folder.
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
with pages 2-10 overlapped for historical evidence, while active-listing pages 1-10 rotate
every 2 minutes. Listing prices shown by live opportunity cards come directly
from the official response. Fair value, profit, ROI, and confidence are locally
calculated from completed-sale evidence, so those cards can take a short warm-up and
will remain empty when the requirements are not met.

## Local files

The persistence APIs use:

```text
config/donut-flip-scanner/config.json
config/donut-flip-scanner/api-key.txt
config/donut-flip-scanner/market-data.db
config/donut-flip-scanner/exports/
config/donut-flip-scanner/backups/
```
See [release hardening](docs/RELEASE_HARDENING.md),
[API integration](docs/API_INTEGRATION.md), [database design](docs/DATABASE.md),
and [GUI/backend integration](docs/GUI_BACKEND_INTEGRATION.md).
