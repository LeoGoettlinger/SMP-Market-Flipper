# Local action capture

SC-Market includes an opt-in diagnostic capture for reproducing private-server Order/AH GUI flows.
It is **off by default** and is enabled from **Item Filters → Order → AH Settings → Local Action Capture**.
A capture is written only to `config/sc-market/diagnostics/order-capture-*.jsonl`; no upload, HTTP request,
telemetry, or automatic sharing is performed. The UI confirms the generated filename so it can be attached
for later analysis.

The capture stores timestamps, screen title and sync ID, screen-relative click coordinates, clicked slot number
when available, item registry ID, stack count, mouse button, and a small allowlist of player action types (item
use, block interaction/attack, and entity interaction/attack) with the held item's registry ID. It does not
explicitly collect chat, typed text, key contents, player names, world coordinates, credentials, or network payloads.
A custom screen title could still contain session-specific text, so review every file before sharing. Each file is
bounded to 4 MiB and to the configured action cap
(100–10,000; default 5,000). The action cap can be changed in the same UI panel. If the disk write fails or
reaches a bound, capture stops without affecting gameplay.

Use a capture only on a private server where this kind of testing is permitted. Review the file before sharing
it because screen titles and item/slot state can still reveal information about the test session. Disable capture
when the reproduction is complete. Existing files remain local until the user removes them.

The current GUI capture hooks are tied to the repository's pre-port Fabric screen/interaction API. A screen that replaces `mouseClicked` without calling the base implementation may not produce generic dialog-click records; handled-inventory screens use a separate slot-aware hook. Validate the exact Hugo server Order/AH screens in a private test session, and revalidate every hook alongside the Minecraft 26.2 mapping and GUI migration before a 26.2 build is considered usable.
