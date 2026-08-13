# Personal profit tracking

The live dashboard chart shows **tracked realized profit (gross)** rather than a static market
index. It deliberately does not infer profit from the player's current balance or from estimated
opportunity values.

## Workflow

1. Buy an exact opportunity manually after verifying the item, count, seller, and price in game.
2. Click `BOUGHT` on that opportunity and confirm the dialog while the listing is still inside the
   15-second API verification window.
3. The client stores that opportunity's exact listing price as an open cost-basis position.
4. When the official completed-transactions endpoint reports a sale by the current Minecraft
   profile with the same normalized item fingerprint and stack count, the oldest compatible open
   position is realized.
5. The dashboard plots cumulative `sale proceeds - acquisition cost` and retains it across restarts.

Matching prefers the current profile UUID and falls back to an exact case-insensitive username
when UUID data is unavailable. Completed-sale keys are unique, so a transaction cannot be counted
twice. Startup also reconciles stored transactions to recover safely from an interrupted session.
The scanner samples the newest completed-transaction page every 60 seconds, interleaving older
pages between those requests without increasing its existing request rate.

## Accuracy boundary

The result uses actual official API sale proceeds and the purchase price explicitly confirmed by
the user. It is not a complete account tax return: the API does not prove that a manual click
succeeded, distinguish a tracked item from identical pre-existing inventory, or document an
auction fee in the consumed schema. Therefore the chart is labeled gross, requires manual purchase
confirmation, and tracks only scanner-originated flips. Balance deposits, withdrawals, gifts,
orders, non-scanner trades, and other money changes are excluded.
