# DonutSMP API integration notes

Verified on 2026-08-03 against the official Swagger document at
<https://api.donutsmp.net/doc.json>.

## Documented contract used by this chunk

- Base host: `https://api.donutsmp.net/`
- Authentication: `Authorization: Bearer {YOUR_API_KEY}`
- Published limit: 250 requests per minute per API key
- Current listings: `GET /v1/auction/list/{page}`
- Recently listed: the same listings endpoint with the documented optional JSON
  body sort value `recently_listed`
- Completed transactions: `GET /v1/auction/transactions/{page}`
- Transaction pages: minimum 1, maximum 10, 100 items per page, ordered by sale
  date according to the endpoint description

The parser models only documented fields: response `status` and `result`;
listing/transaction `item`, `price`, `seller`, `time_left`, and
`unixMillisDateSold`; item `id`, `count`, `display_name`, `lore`, `enchants`, and
`contents`; enchantment level maps; armor-trim material/pattern; seller
name/UUID.

Unknown fields inside item, enchantment-data, trim, or contained-item objects are
recorded by name without interpreting their undocumented values. The item
normalizer uses this signal to classify the identity as unsupported.

Before Gson allocates a JSON tree, the client enforces the configured character
limit and a nesting depth of 24. The JDK transport independently streams at most
four UTF-8 bytes per configured response character (16 MiB with defaults), rejects
an excessive `Content-Length`, and closes oversized bodies. Arrays and individual
strings are bounded separately by the schema validator.

Numeric `damage`, `durability`, and `max_damage` fields are accepted defensively when
present but are intentionally ignored for tool-market fingerprints. Unknown component
fields remain unsupported rather than being silently interpreted.

## Conservative assumptions and unresolved fields

- The Swagger schema does not mark listing-page bounds. The client rejects page
  numbers below 1 but imposes no listing-page maximum.
- The listings response documents no page size, total count, total pages, or
  cursor. No remote pagination values are invented.
- The API describes `price` as a JSON number but does not state whether
  fractional currency is possible. It is retained losslessly as `BigDecimal`.
- The unit and meaning of `time_left` are not documented. It remains an opaque
  `long`; it is not converted to an expiration time.
- `unixMillisDateSold` is interpreted as epoch milliseconds based on its
  documented field name. An invalid/out-of-range value is retained by the raw
  model but produces an empty `Instant` conversion.
- Auction order without an explicit sort, listing IDs, listing creation time,
  buyers, and transaction IDs are not documented.
- HTTP 429 and retry headers are not listed in the Swagger response tables. The
  client still handles 429 conservatively and recognizes the standard
  `Retry-After` format when present; otherwise it uses configured backoff. A
  malformed value is ignored and an extreme server cooldown is safety-bounded
  to 24 hours.
- The official GET listings operation documents an optional JSON request body.
  The recently-listed call follows that contract exactly. Some generic HTTP
  intermediaries may handle GET bodies inconsistently; credentialed live verification
  is still required on the user's installation.

No live API key or response was used to infer additional fields in this chunk.

## Production client startup

The Fabric client now checks `config/donut-flip-scanner/api-key.txt` during startup.
Credential absence is treated as an intentional mock-mode launch. When a key exists,
`ClientProductionRuntime` asynchronously loads configuration, initializes/migrates the
SQLite market database, composes ingestion, statistics, fair-value, confidence, risk,
and opportunity services, atomically selects live GUI providers, and starts the
scanner. Registry-backed item search is constructed on Minecraft's client executor;
network, JSON, database, and analysis work remain off the render thread.

Completed-transaction pages rotate through the documented range 1-10. Active listing
pages rotate through the configured range 1-10, while the documented `recently_listed`
sort is polled separately for low-latency detection. Only accepted conservative
evaluations become active opportunity cards. A live card's purchase price is the
official listing `price`; derived values are explicitly local estimates.

An opportunity remains actionable only while its listing has an `ACTIVE` observation no
older than 15 seconds. Successful duplicate page responses advance `last_seen_at` even when
the page hash is unchanged. Failed requests never advance it. Unreconfirmed opportunities
transition atomically to `NO_LONGER_AVAILABLE` with an append-only state-history reason.

The API client and scanner are read-only and never select slots or send gameplay commands.
A separate private-server automation adapter exists behind independent disabled-by-default
authorization gates; possessing an API key neither enables nor authorizes that adapter.
