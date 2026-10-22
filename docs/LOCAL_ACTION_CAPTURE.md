# Local action capture — developer handoff

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

SC-Market has an **opt-in, local-only** diagnostic recorder for inspecting private-server Order/AH screens and manual GUI flows. It does not automate an action, read network packets, or upload the recording. In the current code the Order→AH adapter remains fail-closed; capture data is intended to help build and test a future exact screen/profile parser. For the player-facing, step-by-step instructions (including the newest supplied screenshot observations), read [Developer function: local Order/AH capture](DEVELOPER_FUNCTION.md).

## Record a private test flow

1. Use only a server you own or whose operator explicitly permits this testing. Prefer a disposable test profile. Do not test on a public/unapproved server.
2. Start Minecraft with SC-Market and connect to that authorized test server. Do not enable live automation just to record clicks. Order→AH currently reports that no verified Order-ID/claim parser is connected and should remain idle.
3. Open the ClickGUI with **Right Shift**.
4. Select the **Order→AH** tab.
5. Set the action cap if desired, then toggle **LOCAL CAPTURE · OFF**. It should change to **LOCAL CAPTURE · ON** and the panel notice reports the local filename.
6. Manually navigate the relevant Order/AH screens and click only what is safe to inspect. The recorder passively logs supported GUI clicks; it never clicks, claims, buys, or lists for you. Do not collect a partial order or submit an unapproved trade. If a screen requires a consequential confirmation, stop and capture the visible state instead.
7. When finished, toggle **LOCAL CAPTURE · ON** off. The panel should show **LOCAL CAPTURE · OFF**. If the cap is reached, recording stops automatically; toggle off before starting a fresh file.

The capture is saved under:

```text
.minecraft/config/sc-market/diagnostics/order-capture-*.jsonl
```

On Windows, `.minecraft` is commonly `%APPDATA%\.minecraft`; on macOS it is `~/Library/Application Support/minecraft`; on Linux it is commonly `~/.minecraft`. Use the actual game directory configured by your launcher if it differs.

## What is recorded and what is not

JSONL records include a UTC timestamp, event type, screen title, container/sync ID when available, clicked slot, item registry ID/count when available, mouse button, and screen-relative click coordinates. Only GUI clicks are recorded; world/player interactions are not recorded. Files are capped at **4 MiB** and at the selected action limit (100–10,000; default 5,000).

The recorder does not explicitly record chat text, typed text, key contents, credentials, player names, world coordinates, network payloads, or transaction outcome. A custom screen title or item ID can still be identifying. Review the file before sharing it. The recorder is best-effort: disk errors stop recording without interrupting gameplay. If it is stopped at the cap or by an I/O error, the toggle may still be on; turn it off and back on to create a new file.

The workflow artifact upload in GitHub Actions is only for the built mod JAR; it does **not** upload these local diagnostics. Attach the `.jsonl` file manually when sending it for analysis. Never commit captures or private server configuration to the repository.

## What to send so the Order→AH flow can be finalized

A JSONL file alone does not expose tooltip/lore text or prove what the server meant by a control. Send the following together, with private identifiers removed or obscured:

- the capture from manually opening the relevant Order/AH screens and hovering/clicking safe controls;
- screenshots with the full relevant Order row and its tooltip/lore visible, showing item, order ID if exposed, unit price, requested/filled/remaining quantity, and status;
- a screenshot of the completed-order details screen and the exact claim/collect control, with the entire screen title and visible state;
- a screenshot of the order-creation/confirmation display showing quantity, deposit, the 1% fee, and total;
- an AH listing screen showing the item, stack size, price and seller fields used for matching/relisting;
- the Minecraft version, SC-Market JAR version or commit SHA, GUI scale/resolution, and a short numbered description of which screen/button each capture segment represents.

If a full-order claim can be demonstrated only by actually claiming it, use disposable test data and do not claim a partial order. Do not create an order or list an item merely to produce a recording. When a detail is not visible, say so rather than guessing. Keep the server address, player name, chat, credentials, and unrelated inventory details out of shared material. If a screenshot needs redaction, preserve a separate private original and note which labels were redacted; exact screen titles and labels are important to parser correctness.

With this evidence, the implementation work can define a profile/parser and tests for each observed state, matching, prices/fees, full-order gating, and safe failure. The live feature should only be enabled after those tests and a private disposable end-to-end test pass.
