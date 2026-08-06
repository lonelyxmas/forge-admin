# Low-code preview, publish diagnostics, and table layout

## Requirements

1. Image cells rendered by low-code CRUD pages support click-to-enlarge while retaining authenticated file access.
2. Unexpected application publish failures identify the failed step and provide an actionable, non-sensitive diagnostic reference. The API must not expose stack traces, SQL, tokens, or raw provider payloads.
3. Opening a draft application runtime URL must not issue duplicate loads for the same route state; changing application, draft/runtime mode, or page must still reload as needed.
4. “All columns alignment” applies to all visible table columns, and alignment changes are visible immediately in the designer preview and runtime CRUD columns.
5. Table row spacing is read from the existing row-gap setting and is visible immediately in designer/static previews and runtime `AiTable` tables.

## Non-goals

- No Flyway/database schema change.
- No change to file permissions or download authorization.
- No automatic service/database/Flowable end-to-end startup in this turn.

## Safety

- Authenticated image URLs are resolved only by `AuthImage`; the enlarged view reuses its resolved URL.
- Publish diagnostics expose only step name, stable error code, and a short diagnostic reference. Full exception context remains server-side.
- Route deduplication is scoped to the current component instance and route signature.

## Verification status

Implementation and incremental verification completed on 2026-08-06; see `execution-log.md`.

- Authenticated thumbnail URLs are reused by the enlarged preview, and caller thumbnail attributes remain on the single component root.
- Flow client transport failures are converted to an actionable business error; unexpected failures include a safe error code and diagnostic reference.
- Identical application route loads and the initial fallback/runtime CRUD double mount are both suppressed.
- Global alignment overrides both body and header alignment, while row spacing is reactive in designer, static preview, and runtime tables.
