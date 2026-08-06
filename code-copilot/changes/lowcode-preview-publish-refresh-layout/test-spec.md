# Incremental test specification

## P0

- `AuthImage` opens a preview only when `preview` is enabled and uses the resolved authenticated URL.
- Runtime CRUD props preserve `tableRowGap`; block-level alignment overrides compiled column alignment.
- Same application runtime route key does not invoke the loader twice concurrently or after a successful identical load.
- Unexpected publish failures return a safe message containing the failed step/reference and never the raw exception message.

## P1

- `git diff --check`.
- Focused frontend Vitest suites.
- Frontend production build with Node `v20.19.0`.
- Affected generator module compile/test with JDK 17 if available.

## Intentionally skipped

- Real Admin/Flow service startup, MySQL/Redis access, Flyway execution, and browser E2E because this session must not mutate or depend on the user's runtime environment.

## 2026-08-06 incremental result

- P0 frontend: 4 Vitest files, 21 tests passed.
- P0 backend: `BusinessProcessPublishServiceTest` and `BusinessApplicationDraftPreviewContractTest`, 12 tests passed.
- P1 target ESLint: passed with no errors.
- P1 frontend production build: passed with Node `v20.19.0`; existing chunking, component-name conflict, and CSS comment warnings were non-blocking.
- P1 generator compile/test: passed with JDK 17 and the required `enable-tests` profile.
- P1 diff whitespace check: passed before documentation closeout and rerun after closeout.
