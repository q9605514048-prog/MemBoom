# MemBoom Megumin Explosion Design System

## Goal and constraints
Rebuild the web and native Android interface around quick retrieval of private records. Preserve all current account, category, note, media, backup, auth and API behavior. No Windows changes. Do not change encryption, data schemas or backend protocols. No chibi. RU and EN remain equal.

## Existing UI inventory and feature map
Web: master-password unlock, remembered entry, welcome/local mode, login/register/profile, grouped accounts, category create/rename/delete, account add/edit/delete, arbitrary fields, favorites, filters/sorting, generator and copy; notes create/edit/delete/pin/search/linked-file editor; manga/anime lists with cover retry, built-in and imported statuses, progress, favorites, edit/delete/import preview; encrypted backup export/import and merge; API account sessions, manual sync and conflict reporting; terms, ReManga export helper and locale picker.
Android: startup/account choice, local setup/unlock, server login/register/profile and legacy-vault unlock; accounts/search/category/favorite/sort, add/edit/custom fields/generator, notes/editor/pinning/link editor; settings, backup export/document picker/import preview, API registration/device access/revoke, upload/download/merge/conflicts, app lock/language/offline terms. Storage remains Android Keystore; portable backup format unchanged.
Tests: browser-independent model/action tests, auth/API/crypto tests, native debug/release tests, existing isolated QA package and emulator scripts.

## Chosen design
Domain: a private grimoire, memory, seals, ember, constellations, spell index. Signature: an eight-ray explosion seal used in identity, unlock and empty states. A quiet index sidebar serves the vault; no giant fantasy illustrations. Red is reserved for primary action; amber marks identity/favorites; danger uses a lighter separate tone and explicit labels.
Colors: night #100D16; surface #19131F; raised #231A2B; inset #120E18; crimson #D94255; amber #E9BC6A; ink #F7EDF0; secondary #BAA9BA; danger #FF929A. Base spacing 4px/dp; control radius12, card16, modal24. Borders mostly white at 9% opacity. UI motion 120–200ms, reduced motion respected. Native control targets48dp; web44px. System sans for body, Georgia serif for restrained web display headings, monospace for secret values.
Desktop: fixed 232px sidebar, compact topbar, dashboard default, accounts master/detail, responsive media library, backup and sync centers, settings. Mobile web: compact menu drawer and bottom navigation, single-column forms, details become a full panel. Native Android: compact summaries, separate account details, touch-first navigation and grouped settings.

## Implementation plan
- [x] Web presentation helpers and regression tests: safe summaries, secret classification, identity-preserving filtering.
- [x] Web app shell/dashboard/desktop and mobile navigation; keep existing IDs and feature entrypoints.
- [x] Accounts master/detail, masked values, accessible copy/reveal, useful editor and generator.
- [x] Notes sorting/reading and existing link editor; backup/password dialogs, API status and settings.
- [x] Auxiliary web screens: auth, media, ReManga helper, terms and common visual states.
- [x] Android native screens and reusable visual primitives; retain services and models.
- [x] Merge RU/EN dictionaries and validate placeholders; build and run tests.
- [x] Browser visual checks at360/390/430/768/1366/1920, native QA screenshots; report gaps honestly.

## Verification and review focus
Never render secret values in overview/account summaries, never reveal by hover. Lock clears visible details, search results and open editors. Check long escaped names, custom field labels and RU text; actual CRUD/backup round trips; API unavailable/conflict status must remain factual. Only synthetic QA records. Verify default/empty/loading/error/focus/reduced-motion states, no horizontal overflow and reachable primary actions.

## Execution boundaries
Parent owns web shell and account behavior. Android worker owns native UI. Auxiliary worker owns web library/auth/terms and their CSS. Each supplies new translations separately; parent merges once and regenerates assets. No parallel edits to crypto, server, generated locales or shared models.

## User added YummyAnime following
The user additionally requested live updates from their public profile. Added a read-only server adapter because cross-origin browser access cannot supply this reliably. Two new GET endpoints validate a fixed upstream host, cap responses and reject incomplete lists. The browser checks every60seconds only while visible/unlocked/online, shows lastcheckedtime and preserves local notes/progress/manga. A generation guard prevents delayed replies writing afterlock. Profile defaults stay in ignored private server config; no personal profile is hardcoded in the public sources. Existing encrypted-vault API, crypto and schemas are unchanged.

## Verified delivery state
Website/API: 129 passing tests. Android: 97 tests per debug/release variant and native emulator CRUD/language smoke checks. Responsive web checked at 360, 390, 430, 768, 1366 and 1920 pixels. The user waived the screenshot report. Android 0.10.0 compiles, but distribution is pending recovery of the original signing key; the current local debug key differs from v0.9.1. Do not replace the published APK with this incompatible signature.
