# Changelog

All notable user-facing changes to Actua are recorded here. Releases use calendar versions in `YYYY.M.D` format.

## Unreleased

## [2026.9.1] - 2026-09-20

### Added

- Added Home customization: reorder Home's sections with a drag handle or the accessible up/down arrows, show or hide any optional section (Favorite Categories, Favorite Accounts, Upcoming, This Month, Reports, Recent Activity), and restore the default layout, from a new "Customize Home" entry on the Home app bar and Settings → Home. Ready to Budget stays pinned first and can't be hidden, since it's Home's primary purpose. The layout is a device-local UI preference (not synced budget data), persisted once per committed change rather than on every drag step, and newly introduced sections are appended deterministically instead of disturbing a saved layout

## [1.0.0-beta.33] - 2026-09-19

### Added

- Added per-server certificate trust on the Connection screen: when a server's certificate can't be verified against Android's trusted authorities, Actua shows the host, issuer, validity dates and SHA-256 fingerprint and lets you explicitly trust that one certificate for that one host (scoped to Actua, with hostname verification still enforced); if the server later presents a different certificate, Actua warns that it changed and asks you to verify the new fingerprint before trusting it

### Fixed

- Fixed the budget group summary row's balance pill not lining up with the category rows below it: the pill compensates for its own centered text padding by offsetting itself past its column's edge, which only stayed on-screen because nothing clipped the overflow — once the totals row's `animateContentSize` (beta.32) started clipping content to its own bounds, that offset got cut off. The first attempted fix dropped the offset, which un-clipped the pill but left its digits sitting inset instead of flush; the second attempt dropped the pill's own text padding instead, which realigned the digits but made the pill's background look lopsided (no breathing room on the trailing side) compared to the nicely padded pill elsewhere on the screen. The actual fix moves the balance pill out of the totals row's `animateContentSize` scope (which now only wraps Budgeted/Spent, the part that actually needs it) so the original offset — and its normal, symmetric padding — works exactly as it does in the category rows below, with nothing left to clip
- Fixed the Connection screen showing the raw `Trust anchor for certification path not found` Java exception message on a failed connection (password or OpenID) instead of telling the user what to do about it; an untrusted server certificate now shows guidance for both causes: a self-signed/private-CA cert needs installing as a user CA certificate scoped to "VPN and apps" (a certificate scoped to Wi-Fi only reproduces this exact error even after installing it), and a publicly-issued cert (e.g. Let's Encrypt via Tailscale) hitting this error usually means the server is sending only its own certificate instead of the full chain, which Android — unlike browsers — won't complete on its own
- Hardened the trusted-certificate hostname check: SANs are verified explicitly during trust inspection, wildcard matching is limited to a single leftmost label, IP-address certificates only match IP hosts, and IPv4 parsing accepts ASCII digits only
- Fixed ordinary budget category progress bars (Plan, table and details) ignoring spending: they now show spent / (absolute spending + positive available balance), including carryover, with shared status colors and accessible spending labels; goal-only, save-by-date, cover-schedule and synced long-goal targets keep their balance-funded progress
- Improved navigation responsiveness: the selected bottom-navigation tab updates immediately, each tab retains its scroll and detail state when you switch away, and Reports now loads off the main thread, appearing at once with a loading state and keeping its last snapshot while a newer one is prepared

## [1.0.0-beta.32] - 2026-09-18

### Added

- Made the Budgets section on the Connection & data screen collapsible (tap-to-toggle chevron), since the budget list can grow long and push Backups and other settings far down the screen

### Fixed

- Fixed saving or deleting a transaction running its local database write on the main thread; it now runs off the main thread so the editor dismisses as soon as the transaction is durably committed locally, without any extra wait for (already-asynchronous) server sync
- Fixed connecting to a self-hosted Actual server over plain HTTP on a local/private network: Android's network security config only allowlisted one developer's hardcoded test IP (`192.168.68.109`) for cleartext traffic, so every other private/local HTTP server was rejected with "Cleartext HTTP traffic not permitted" even though the app's own connection validation already restricts HTTP to localhost/`.local`/RFC1918/link-local/ULA addresses and requires HTTPS everywhere else
- Enabled WAL mode (with a busy timeout) on the local budget database so the background sync worker's connection no longer contends with the UI's connection, reducing SQLITE_BUSY stalls and the app feeling unresponsive while syncing
- Fixed the Accounts view's status filter chips: selecting the "Reconciled" chip (or any status chip) while "hide reconciled transactions" was on left the list empty, because the client-side hide-reconciled filter re-stripped the reconciled transactions the server-side query had already returned; the selected status chip now supersedes the hide-reconciled preference, and the empty-state message reflects whichever chip is active instead of always blaming hide-reconciled
- Fixed HTTPS connections to self-hosted Actual servers using a self-signed or private-CA certificate: the network security config only trusted the system CA store, so a manually installed user CA — the only option for a private certificate on a non-rooted device, including GrapheneOS — was rejected with `Trust anchor for certification path not found`; user-installed CAs are now trusted too
- Fixed `formatDate`/`parseStoredDate` allocating a fresh date formatter and regexes on every call (once per transaction row, per recomposition, while scrolling); hoisted to module-level constants
- Fixed toggling one transaction's checkbox in multi-select recomposing every visible row instead of just the row(s) whose selection state actually changed
- Fixed `ReconcileAccountScreen`'s uncleared-transaction filter re-running unmemoized on every recomposition, including every keystroke of the reconciliation calculator
- Fixed several single-destination reads (Reports' report snapshot, Rules' rule list/support/schedule-ownership lookups, and Reorder Groups' category-group ordering) recomputing on every data change regardless of which screen was visible, instead of only when their own destination is on screen

## [1.0.0-beta.31] - 2026-09-18

### Added

- Added support for custom HTTP headers (e.g. `CF-Access-Client-Id`/`Secret`) sent with every request to the Actual server, configurable from the Connection screen for servers behind an auth proxy like Cloudflare Access
- Added a type picker to the new-account dialog and a "Change account type" action to the account menu; every account previously showed as "Checking" regardless of its actual type
- Release builds now ship an AOT-compiled baseline profile covering startup, tab switching, and core Budget/Accounts/Transactions scroll paths, reducing reliance on JIT warm-up

### Fixed

- Fixed the Transactions search/filter list and its date-grouping, Budget's category-visibility filtering and a category's recent-transactions list, Accounts' section filtering and per-row credit-card lookups, and the Add/Edit Transaction payee/category/account picker all recomputing on every recomposition (e.g. every keystroke or row-selection tap) instead of only when the underlying data actually changes
- Fixed the Budget list re-diffing incompatible row layouts while scrolling between income/Plan/Table sections, reallocating a copy of each group on every header recomposition, and animating the whole Table-view sticky header instead of just the totals row that resizes; money amounts no longer construct a new number formatter on every render
- Fixed loading a budget month's or the accounts list's notes with one synchronous database round-trip per category/account (plus a repeated table-existence check on each one) instead of a single batched query

## [1.0.0-beta.30] - 2026-09-18

### Changed

- Brightened the default theme with a higher-chroma violet/orchid palette (light and dark) and matching success/warning accents
- Replaced the budget month stepper's uneven left/right step arrows with a single YNAB-style control: the month label plus a small down-chevron opening the existing month/year picker, making the whole row one large tap target
- Redesigned the transaction row layout to match Actual Budget's PWA: the cleared/selection indicator moves to the left of the payee and top-aligns with it; the category chip and notes sit directly under the payee flush left; the right side is a single right-aligned column holding the amount, then running balance (or, in the cross-account Transactions tab, the account name in the category chip's color), then the date; added spacing between the amount and cleared tick in the transaction details sheet header
- Moved the running balance onto the category row instead of stacking it under the payee's amount, so enabling it no longer adds extra row height
- Realigned the account summary card's Cleared/Balance/Uncleared values to left/center/right instead of all centered
- Used the theme's primary color for the cleared checkmark instead of a fixed green

### Added

- Added the current billing cycle's date range and a "Statement history" link on a credit card account's Billing cycle card
- Added a per-account "Show credit card section" toggle in the account dropdown menu to hide the billing cycle/statement history section
- Added a global "Notes" toggle under Display settings to hide the notes field on accounts and budget categories

### Fixed

- Fixed progress bars still reading as fully funded for non-monthly "Cover scheduled transaction" and "Save by a date" goals: a locally-known target definition now always wins over Actual's server-synced goal cell, since that cell can be left over from before an automation was last (re-)applied and represent only the current month's installment rather than the true end goal; "Cover scheduled transaction" goals also now resolve their full occurrence amount from the linked schedule directly, instead of only falling back to month-to-month spend progress when no server goal was synced
- Fixed the system back button skipping past credit card statement screens instead of returning to the account page
- Fixed the budget month selector's tap ripple stretching across the full toolbar width instead of hugging the month label
- Fixed budget action sheet transitions (Move to Category/Hold for Next Month, Auto-Assign/Move Money) snapping height instantly while content faded; the whole switch now animates together

## [1.0.0-beta.29] - 2026-09-17

### Changed

- Modernized the entire app onto a shared design system: a new brand color palette (light/dark) derived from Actua's launcher icon, a consistent spacing/shape/typography scale, and shared components (screen headers, list rows, monetary text, bottom-sheet titles) applied across Accounts, Settings, Budget, Transactions, the transaction editor, Reports, Manage/Settings sub-screens, dialogs, sheets, and a final sweep of remaining shape and hardcoded status-color inconsistencies
- Added a "Material You colors" toggle in Settings > Display (Android 12+) to opt into wallpaper-derived dynamic color instead of Actua's own palette, which remains the default
- Removed "New category"/"New category group" from the Budget tab's + menu and the "Manage Categories" section from the Manage tab (both already reachable via Manage Categories); removed "Hide cleared transactions"/"Hide reconciled transactions" from the transactions dropdown menu, redundant with the existing status filter chips

### Added

- Added credit card statement history: a history icon on each credit card row opens the last 3 closed statements with their due amounts, and each statement opens to its own transaction list

### Fixed

- Fixed schedule-owned rules never matching through `RulesEngine`: a posted or edited schedule transaction now always applies its own linked rule's actions and never another schedule's rule
- Fixed the scheduled-transaction catch-up dedup check only bounding by a lower date, which could let an out-of-order or future-dated linked transaction mask and skip an earlier due/missed occurrence
- Fixed transaction row alignment: the running balance line now has proper spacing from the amount, and the amount column top-aligns with the payee text instead of centering against the taller stack

## [1.0.0-beta.28] - 2026-09-17

### Changed

- Restructured the account detail balance header into an always-visible three-column Cleared / Balance / Uncleared row, matching the Actual PWA layout; only Reconciled (and, for credit cards, Available credit / Credit limit, with Credit limit now under Available credit) stays behind the collapsible toggle
- Cover schedule and From history (average) budget automations now support Actual's signed increase/decrease adjustment modifier, and % of income now offers a real picker over available funds, all income, and the budget's actual income categories instead of a fixed "available funds" text note

### Added

- Added a "Running balance" toggle to an account's transaction register overflow menu; when enabled, each transaction row shows the account's balance after that transaction as a smaller line under its amount, computed from the account's full transaction history (including transfers, splits and the opening balance) rather than only the currently visible/filtered rows, and persists across sessions
- Added a "Copy last month's budget" action to the Budget screen's overflow menu, which copies the previous month's budgeted amounts for visible expense categories (plus visible income categories for tracking budgets) into the selected month; hidden categories and groups are left unchanged

### Fixed

- Fixed a percentage budget automation's selected income category being silently discarded and replaced with the default on decode

## [1.0.0-beta.27] - 2026-09-17

### Changed

- Redesigned Budget Automation as a dedicated full-page editor matching upstream Actual Budget's mobile structure: a separate "Automations" list (Fixed amount, Cover schedule, Save by date, % of income, From history, Refill to cap, Whatever is left) and "Options" section (Balance cap, Long-term goal), each capped at one per category; Refill to cap no longer carries its own amount and instead ties to the sibling Balance cap automation, as it does upstream
- Replaced the Reorder Categories screen with a full Manage Categories page (create/rename/hide groups and categories, delete a category, move a category to another group) and a separate Reorder Groups screen for group-only ordering; category drag now only mutates local state during the drag and persists once on drop (reverting on failure), starts immediately from a large handle instead of waiting for a long-press, and auto-scrolls near the list edges
- Tapping Ready to Budget / To Budget now opens the Budget Summary sheet already expanded in its "Move to Category" state instead of the collapsed action-selection state that needed a second tap
- Restyled the Plan-view "Ready to Budget" overview card from a 28dp to a 14dp corner radius to match the other cards in that view

### Fixed

- Fixed budget progress bars ignoring "Have amount by a date" and "% of income"/goal-based (`BY_DATE`/`GOAL`) targets until an unrelated whole-budget "Apply Templates" run synced the goal amount from the server; the goal is now computed locally from the target definition so progress reflects it immediately
- Fixed the target editor excluding "Cover schedule" targets from its type picker and silently dropping the linked schedule when saving an existing schedule-linked target; added a schedule picker to select, change, or clear the link, and fixed the details card to show the linked schedule's name/amount instead of $0.00

## [1.0.0-beta.26] - 2026-09-16

### Changed

- Reworked the To Budget summary into a single flat sheet: tapping the Ready/To Budget amount now shows "Move to Category" and "Hold for Next Month" as direct tiles instead of behind a "⋮" overflow menu; a "Reset Hold" tile appears directly once an amount is held
- Enabled R8 code shrinking and resource shrinking for release builds to reduce APK size

### Fixed

- Fixed the "Syncing budget… Showing local data." banner appearing on every local edit (adding/editing transactions, budgeting a category, toggling cleared, etc.); it now only appears for app-open and background syncs, which can bring in changes the screen doesn't have yet, not for the upload triggered by an edit you just made

## [1.0.0-beta.25] - 2026-09-16

### Added

- Added a "Duplicate" transaction action to the transaction bottom sheet, transaction details sheet, and the multi-select bulk-actions menu; duplicating creates an immediate, unlinked copy of the transaction(s) rather than opening the editor
- Long-pressing a transaction now offers a "Select" action that enters multi-select mode with that transaction pre-selected, in addition to the existing app-bar select toggle
- Added Budget category view filters (Overspent, Underfunded, Overfunded, Money Available) as a FilterChip row in the Budget toolbar, alongside the existing hide-fully-spent and show-hidden filters; the selection persists across sessions
- Added query-level transaction status filters (Uncategorized, Uncleared, Cleared, Reconciled) as a FilterChip row on the Transactions screen, filtering directly at the database layer
- Added drag-to-reorder for budget category groups and categories via a new Reorder Categories screen (Manage → Categories), with drag handles and up/down buttons as a non-drag accessible alternative; categories can be moved between expense groups, while the income group's position stays fixed
- Added a "Hold for next month" / "Reset next month's buffer" action to the To Budget row menu (Plan and classic budget views) for envelope budgets, letting part or all of a month's To Budget amount carry to next month instead of being budgeted immediately

### Changed

- A category explicitly chosen in the transaction form (or typed before a payee-triggered rule preview) now survives a matching rule instead of being overwritten; an empty or inferred category is still filled in by rules as before

### Fixed

- Fixed "View schedule" (from the Transactions multi-select bulk menu) not returning to the Transactions screen after back/save/delete on the opened schedule; it previously always dropped you onto the Schedules list or Bills calendar
- Fixed the budget progress bar for "Have amount by a date" and "Cover scheduled transaction" targets tracking only this month's installment instead of the overall goal; a category funded 10% toward a multi-month target now shows 10% progress instead of 100% once that month's slice is met

## [1.0.0-beta.24] - 2026-09-15

### Added

- Added multi-select mode to Transactions: a select toggle enables checkbox-based multi-selection with a bulk-actions menu for mark cleared/uncleared, delete, link to schedule, unlink schedule, and (for a single linked selection) view schedule

### Fixed

- Fixed a spurious error banner (e.g. a raw `CertPathValidatorException`) appearing on cold app launch, most noticeably via the Add Expense/Income/Transfer/Search home-screen shortcuts; the automatic app-open sync is opportunistic and no longer surfaces transient network failures as a Snackbar, since they're already recorded for the Connection screen and resolved by the next sync attempt
- Fixed the transaction notes `#` tag autocomplete dropdown dismissing and reopening the on-screen keyboard on every keystroke while a tag token is active; the suggestion popup no longer takes window focus, so the keyboard stays open while suggestions update live underneath

### Safety

- Bulk delete requires an explicit confirmation naming the number of transactions before any deletion occurs, and merging selected transactions is intentionally not offered in this release since its semantics (which fields win, how splits reconcile) still need a separate design discussion

## [1.0.0-beta.23] - 2026-09-15

### Added

- Added a read-only Upcoming Schedules home-screen widget: overdue-first ordering, relative due labels (Today/Tomorrow/In N days/N days overdue), a per-instance configurable 7/14/30-day period, a compact layout for smaller sizes, and a tap-through deep link to Scheduled Transactions
- Added a tappable `#` trailing icon to the transaction Notes field, matching Actual's mobile UI, for starting a tag without typing `#`

### Fixed

- Fixed transaction notes `#` tag autocomplete never appearing in Add/Edit Transaction; typing `#` in the main Notes field or a split note now shows matching-tag suggestions and an inline "Create #tag" option, as already documented

### Safety

- The Upcoming Schedules widget is read-only; no financial mutation (post, skip, complete) is reachable from it, and it respects hide-balances, currency, and decimal-place display preferences

## [1.0.0-beta.22] - 2026-09-15

### Added

- Added full Actual Budget tag support: colored tag rendering consistent across every transaction list, `#` autocomplete with inline tag creation in transaction notes, a native Manage Tags screen for create/edit/delete/color/hidden state, and tap-a-tag transaction discovery/filtering, all backed by Actual's canonical tag dataset and refreshed after sync
- Added a README hero image and a mobile-friendly screenshot gallery

### Changed

- Budget category progress bars now fill toward an active budget-automation goal instead of always reading as spend-down, so a long-term funding goal shows real progress before anything is spent

### Fixed

- Fixed colored transaction-note tags rendering inconsistently across transaction lists, including tag metadata schema handling and cache invalidation after sync
- Fixed transfer transactions not applying matching Actual rules; selecting a destination account now triggers rule evaluation and applies supported actions such as Notes and Cleared

### Safety

- Tag rendering remains display-only and never mutates transaction notes or synchronized tag metadata, with unknown or malformed tag metadata falling back to normal text
- Categories without an active goal keep their existing spend-down progress behavior unchanged, and transfer rule matching preserves Actual's canonical transfer-payee representation and linked transfer persistence

## [1.0.0-beta.21] - 2026-09-14

### Added

- Added full standalone Balance Cap editing with daily, weekly, and monthly cadence, weekly start dates, and retain-overflow controls while preserving Actual's stored automation semantics
- Added Actual Budget tag-colour rendering for recognized transaction-note tags using synced tag metadata, with readable foreground colours and safe fallback styling

### Changed

- Simplified the project README around installation links, core features, screenshots, demo access, credits, community, and licensing
- Balance Cap automations now participate as a distinct supported category automation instead of being exposed through refill-style editing

### Fixed

- Corrected ongoing sync-status layout on Connection & Data so the progress indicator remains visible, aligned, and properly spaced on narrow screens

### Safety

- Advanced or malformed Balance Cap definitions remain protected from silent rewriting, and tag rendering is display-only and does not modify transaction notes or tag metadata

## [1.0.0-beta.20] - 2026-09-14

### Added

- Added schedule-driven budget funding for resolved active schedules, including recurring occurrence counts, amount-range midpoints, priority ordering, and available-funds clamping
- Added current-month percentage budget contributions from available funds, all income, or a specific income category, plus exact previous-month copy targets
- Added notes-managed month-end cleanup groups with deterministic cent-preserving sink distribution, overspend filling, preview, and atomic application

### Changed

- Expanded budget automation compatibility to support schedule, percentage, remainder-limit, and notes-managed templates while preserving Actual's priority and source-validation rules
- Updated the app for Android 17/API 37 readiness and aligned the audited Actual-compatible backend schema with Actual Budget v26.9.0

### Fixed

- Hardened encrypted budget download and open recovery so invalid or incomplete local state is detected before it can be restored or used

### Safety

- Unresolved, malformed, completed, unsupported, mismatched, and prior-month percentage automations remain read-only rather than being approximated or rewritten
- Cleanup and whole-budget automation changes remain preview-first, stale-checked, integer-cent calculations written as atomic CRDT batches

## [1.0.0-beta.19] - 2026-09-13

### Fixed

- Account monthly Income, Expenses, and Net now use Actual category classification, exclude transfers and uncategorized cash flow, and count categorized split portions without double-counting their parent
- Ongoing synchronization status now stays below the Android system status bar, and nearby payee results have clear compact spacing below the refresh action
- Matching Actual rules now populate supported account, category, cleared, notes, date, amount, and payee fields immediately in the new-transaction form

## [1.0.0-beta.18] - 2026-09-13

### Added

- Added automatic nearby-payee suggestions and inline actions to save or remove synchronized payee locations
- Added review-first XLSX and text-based PDF statement imports alongside CSV
- Added reusable CSV import profiles with configurable columns, delimiters, date formats, and amount-sign handling
- Added review-first pasted or shared SMS and email transaction alerts
- Added optional, per-app on-device capture of incoming financial notifications for later review

### Changed

- App-open synchronization now refreshes immediately when returning from the background, while scheduled WorkManager refreshes run independently and expose distinct status timestamps
- Import review now explains duplicate warnings, preserves import history, and shows confidence and account hints for notification candidates

### Safety

- Notification access remains disabled until explicitly enabled, limits capture to selected apps, parses alerts on-device, and never creates transactions without review and confirmation
- Payee location requests remain foreground-only, and automatic location recording remains separately opt-in
- Statement and notification imports exclude malformed candidates and require an explicit review step before writing to the budget

## [1.0.0-beta.17] - 2026-09-13

### Changed

- Nearby payee lookup now accepts a recent valid location and requests all suitable enabled Android providers concurrently, so a slow indoor GPS fix no longer blocks faster fused or network results
- The **Manage** bottom tab now uses a dedicated Tune icon instead of the overflow-style three-dot icon
- Forward and Back animations inside **Manage → Settings** now consistently follow the page hierarchy

### Safety

- Nearby location requests remain one-shot and foreground-only, retain the 500-metre accuracy and matching limits, and cancel every outstanding provider request after success or timeout

## [1.0.0-beta.16] - 2026-09-12

### Added

- Added review-first CSV bank-statement import with editable candidates, malformed-row exclusion, duplicate warnings, and batched transaction creation
- Added opt-in foreground-only location-aware payee suggestions, eligible transaction recording, 500-metre deduplication, and synchronized location management
- Added a dedicated **Bills & Calendar** destination for scheduled transactions and configured credit-card due dates
- Added weighted remainder category automations that distribute Ready to Budget after higher-priority targets

### Changed

- Replaced the **More** bottom tab with a task-focused **Manage** hub for Automation, transaction/data tools, and financial setup
- Moved general preferences behind the Settings gear in Manage and automatically migrate an existing **More** start-page preference to **Manage**

### Fixed

- Blank transaction amount fields no longer shift when focused or while their caret blinks

### Safety

- Location recording is disabled by default, requests foreground permission only after explicit use, never tracks in the background, and stores coordinates in the synchronized Actual budget
- CSV imports require review and approval before writing transactions and warn about likely same-account duplicates

## [1.0.0-beta.15] - 2026-09-12

### Fixed

- Successful OpenID Connect sign-in now returns directly from the browser to Actua instead of leaving the user on a stale or expired OIDC interaction page

### Safety

- The Actual session token remains confined to the temporary localhost callback and is never included in the Actua deep link
- Browsers that do not follow the automatic app redirect still receive a safe fallback page with a manual **Return to Actua** link

## [1.0.0-beta.14] - 2026-09-12

### Added

- Added OpenID Connect (OIDC) sign-in for Actual servers that use OpenID authentication
- Added browser-based provider authorization with a temporary localhost callback that captures the resulting Actual session token

### Changed

- Password login now explicitly requests Actual's password login method so password and OpenID configurations remain compatible where the server permits both
- Compact icon-only bottom navigation now uses one consistent background through the Android system bottom inset

### Fixed

- Blank transaction and split amount fields no longer shift layout when focused or when the custom cursor starts blinking

### Safety

- Identity-provider client IDs and secrets remain configured on the Actual server; Actua only receives the resulting Actual session token
- OpenID callback listening is limited to localhost rather than being exposed to the LAN

## [1.0.0-beta.13] - 2026-09-12

### Added

- Added multi-automation category editing with explicit priority ordering for
  supported UI-managed target definitions
- Added goal-only balance targets that track a desired balance without
  automatically assigning money
- Added configurable date and number formatting under **More → Display**

### Changed

- Category automation evaluation now preserves priority order when several
  supported definitions apply to the same category
- Bottom navigation labels now remain on one line and automatically resize when
  horizontal space or display scaling is constrained
- Hiding bottom-navigation labels now switches to a compact 64 dp icon-only
  content area while preserving the system bottom inset and accessible controls

### Safety

- Unknown and notes-managed automation definitions remain read-only and are not
  overwritten by the category automation editor
- Goal-only targets do not assign money during Auto-Assign or whole-budget
  template application

## [1.0.0-beta.12] - 2026-09-11

### Added

- Added an Android document picker for importing exported Actua backup archives into the managed Backups list
- Added preview-first whole-budget application for supported category targets, including per-category changes, net budget impact, unchanged-target counts, and explicit unsupported-automation disclosure
- Added safe category and category-group deletion, ordinary-payee deletion and merge, and category/group reordering through Actual-compatible CRDT mutations

### Changed

- Imported backups now pass the existing archive, metadata, database, size, and unsafe-entry validation before they become available to restore
- Whole-budget target application writes confirmed changes as one synchronized CRDT batch, rejects stale previews, and performs no writes when reapplied without changes
- Expanded backend parity documentation with pinned Actual and Actuali automation references and clear boundaries for advanced automation and cleanup work
- Added representative Android 9 and Android 17 compatibility checks and broader financial data-integrity regression coverage

### Safety

- Importing a backup never replaces the active budget; restore remains a separate confirmed action with the existing one-tap pre-restore revert
- Backups belonging to another budget are rejected rather than silently installed
- Unsupported schedule, percentage, remainder, long-term-goal, notes-managed, and cleanup automations remain untouched rather than being approximated

## [1.0.0-beta.11] - 2026-09-11

### Added

- Added a manual **Build Test APK** GitHub Actions workflow for device testing without publishing a GitHub release
- Test APKs install as a separate **Actua Test** app using `com.azimulkabir.actua.test`, with separate Android app data from normal Actua

### Changed

- Redesigned Budget Snapshot and Quick Transaction widgets with responsive **2x2** layouts and compact **3x1/4x1** resize variants
- Reduced Account Balances and Favourite Categories to practical **2x2** layouts with tighter Material You spacing
- Improved Favourite Categories progress bars and shared widget spacing while preserving dynamic color, dark mode, privacy formatting, and deep links
- Added immediate widget layout refresh when Budget Snapshot or Quick Transaction is resized on the launcher

### Fixed

- Replaced the Add expense launcher shortcut artwork with an unambiguous minus icon so it no longer appears like Add income on Pixel Launcher

## [1.0.0-beta.10] - 2026-09-11

### Added

- Added Material You home-screen widgets for the monthly budget snapshot,
  configurable favourite categories, quick transaction entry, and configurable
  account balances
- Added app-icon long-press shortcuts for Expense, Income, Transfer, and Search
- Added direct widget navigation to the matching budget, category, account,
  transaction-entry, and search screens

### Changed

- Widgets refresh after local changes, successful synchronization, relevant
  display-preference changes, app foregrounding, and Android periodic updates
- Widget amounts respect Actua's balance privacy, currency, decimal, light/dark,
  and supported Material You dynamic-color settings
- Documented the required issue-first, linked-pull-request development workflow

## [1.0.0-beta.9] - 2026-09-11

### Changed

- Removed opaque dependency metadata from APK and app bundle signing blocks for
  F-Droid-compatible source and binary verification
- Added version 26 store metadata and F-Droid submission documentation

## [1.0.0-beta.8] - 2026-09-10

### Changed

- Made manual Disconnect & Reset perform a final synchronization before clearing
  server-bound local data, with an explicit force-disconnect choice if sync fails
- Preserved device preferences and the local demo budget during disconnect cleanup

### Fixed

- Made transaction and split amount fields consistently follow the selected
  display currency, including Use Remaining and split remainder text
- Added currency-formatting regression coverage for supported display modes

## [1.0.0-beta.7] - 2026-09-10

### Fixed

- Fixed a startup crash when creating or opening the built-in demo budget

## [1.0.0-beta.6] - 2026-09-10

### Added

- Added a built-in local-only demo budget that can be opened without an Actual server and reset from Connection & Data
- Added realistic demo data covering checking, savings, a credit card, an off-budget investment account, six months of transactions, paired card-payment transfers, cleared/uncleared/reconciled states, category targets, rules, scheduled transactions, notes, and report dashboard data
- Added explicit sync protection for the reserved `demo` budget so it cannot be uploaded even when server credentials are configured

## [1.0.0-beta.5] - 2026-09-10

### Added

- Added schedule creation from Android with account, payee, amount, date,
  recurrence, and automatic-posting controls
- Added a dedicated repeat editor covering daily, weekly, monthly and yearly
  patterns, weekend handling, bounded endings, and occurrence previews
- Added linked transaction history to schedule details with safe unlinking
- Added recurring-transaction discovery with ranked suggestions and selectable
  schedule creation
- Added a monthly Bills calendar with recurring and card-bill views, due-date
  projection, status totals, date filters, and schedule actions

### Changed

- Refined Bills and schedule navigation so adding, editing, posting, skipping,
  and deleting return to the correct screen and refresh immediately
- Documented YNAB as a mobile UI design reference while clarifying that Actua
  independently implements its interface and contains no YNAB code or assets

### Fixed

- Moved Bills calendar database loading off the UI thread
- Added confirmation before deleting a schedule from the Bills calendar and
  limited Skip Next Date to applicable recurring schedules

## [1.0.0-beta.4] - 2026-09-09

### Changed

- Added consistent forward and back transitions to Reconcile and every nested
  Preferences page under More

### Fixed

- Removed the global Transaction button from the Reconcile workflow so it no
  longer covers the reconciliation keypad

## [1.0.0-beta.3] - 2026-09-09

### Added

- Added a complete scheduled transaction editor with recurrence, weekend handling,
  end conditions, upcoming dates, automatic posting, and lifecycle actions
- Added synced category targets for monthly spending, monthly saving, save-by-date,
  refill-to-cap, weekly spending, and recent-spending averages
- Added target-aware Auto-Assign and target summaries to category details
- Added mobile account reconciliation with exact bank-balance comparison, inline
  uncleared-transaction review, optional adjustments, and cleared-transaction locking

### Changed

- Made payee search update with every typed character and show ordinary payees and
  matching transfer accounts together in one alphabetical result list
- Kept transfer accounts in their own alphabetical section when the search is blank

### Fixed

- Kept split parents and children aligned when their cleared status changes and
  preserved reconciled transactions as locked records

## [1.0.0-beta.2] - 2026-09-09

### Changed

- Placed the full-width transaction Save button directly after the Cleared toggle
  so it scrolls naturally as part of the add and edit transaction form
- Replaced scheduled-row overflow actions with a single navigation chevron and
  aligned the amount and recurrence date to the same trailing edge
- Changed calculator keys from outlined buttons to filled Material surfaces

### Added

- Added a dedicated scheduled transaction editor with synchronized Save and Delete actions
- Added a tappable Actua GitHub repository link to the About page

## [1.0.0-beta.1] - 2026-09-09

First beta of the 1.0 release line.

### Added

- Ported every dashboard widget currently rendered by Actuali: Age of Money,
  Formula, Custom Report, Calendar, Crossover, Budget Analysis, Sankey, Balance
  Forecast, and Monte Carlo
- Added native Material cards, charts, calendar grids, category bars, comparison
  series, and forecast displays for the newly supported report widgets

### Changed

- Updated fresh-install defaults to use Plan view with overview and progress bars,
  enable transaction and account options, start on Budget, use no currency
  override, and hide decimal places
- Replaced the floating transaction Save action with a full-width bottom button
  and matched transaction keypad borders to the Budget keypad
- Grouped Accounts, Transactions, and Reports toolbar actions inside Material
  pill-shaped surfaces and added Accounts controls for monthly summary, expanding
  all groups, and collapsing all groups
- Promoted Rules and Scheduled Transactions to separate visible sections on More
- Expanded More with an About Actua page containing version, project purpose,
  developer information, compatibility, upstream credits, independence notice,
  and license information

### Fixed

- Aligned Scheduled Transaction names, status chips, amounts, recurrence details,
  accounts, and overflow actions into consistent responsive rows
- Corrected nested Settings navigation so Android Back returns directly to More
  instead of requiring an extra gesture

## [0.1.0-beta.3] - 2026-09-09

### Added

- Added an Actuali-style backup manager with automatic app-background backups,
  retention, restore, one-tap pre-restore revert, per-backup export, and optional
  mirroring to a user-selected device folder
- Added live sync status, last successful sync, last scheduled background refresh,
  and a manual Sync Now action to Connection & Data
- Added Actual-compatible blank-budget creation plus confirmed server-budget deletion
- Added synced Actual dashboard pages and widget ordering with native Summary,
  Net Worth, Cash Flow, Spending, and Markdown cards

### Changed

- Replaced the previous fixed Reports overview with the dashboard configured in
  Actual Budget, including synced names, ordering, timeframes, filters, spending
  comparisons, and budget-based comparison values
- Kept unsupported synced dashboard widget types visible with a clear availability
  notice instead of silently dropping them

### Fixed

- Kept report aggregation split-aware and excluded transfers, off-budget accounts,
  and income categories where required by Actual's report calculations
- Corrected backup restore and archive display handling for all backup item types

## [0.1.0-beta.2] - 2026-09-08

### Added

- Added a searchable Scheduled Transactions screen with due-state visibility and skip, complete, restart and delete actions
- Added opt-in Android credit-card payment reminders for 7, 5, 3 and 1 days before the calculated due date
- Added a persistent app-wide option to hide reconciled transactions from lists and searches

### Changed

- Sorted credit cards with unpaid balances first, followed by their upcoming payment due date
- Restored the transaction Save action to its fixed bottom-right position instead of moving or duplicating it around the keyboard
- Matched transaction amount entry to the existing compact Budget-tab keypad without an extra amount or Save row above the keys

### Fixed

- Made backspace visually remove every part of calculator expressions such as `100 + 100`, including the pending operator
- Revalidated notification permission at delivery time so revoked access cannot crash a due-date reminder

## [0.1.0-beta.1] - 2026-09-08

First public beta release.

### Added

- Added fixed day-of-month credit-card payment dates while retaining the existing days-after-statement option
- Added fixed-date handling for short months and a compatible fallback offset for older Actua and Actuali builds

### Changed

- Global transaction search now queries complete local history and matches split-child payees, notes, imported descriptions and categories
- Updated rule documentation to reflect the existing editor, supported standard actions and CRDT mutation support

### Fixed

- Recovered missing, malformed and epoch-like sync clocks from the local message-log high-water mark without discarding pending changes
- Removed categories from off-budget standard and split transactions during creation, editing, account changes and rule processing

## [0.1.0-alpha.14] - 2026-09-08

Fourteenth public testing release.

### Changed

- Signed GitHub release APKs with one persistent release key so alpha.14 and later builds can upgrade each other in place
- Added release-signature verification before publishing an APK

### Important

- Earlier releases used temporary GitHub runner debug keys. Back up and synchronize Actua, uninstall the older build once, then install alpha.14. Future persistently signed releases will install as normal updates.

## [0.1.0-alpha.13] - 2026-09-08

Thirteenth public testing release.

### Added

- Added predictable Android-style bottom navigation with per-tab state restoration and reselect-to-top behavior
- Added category-aware Back navigation from View all and Transactions this month to the originating Category Details page

### Changed

- Attached the transaction Save action directly above the amount and split keypads without relying on a fixed floating offset
- Matched the Save action color to the main Transaction action and hid bottom navigation during Add/Edit Transaction

### Fixed

- Kept Plan expense-category and account separators above opaque row backgrounds so they remain visible
- Removed excessive spacing between the transaction Save action and the amount keypad

## [0.1.0-alpha.12] - 2026-09-08

Twelfth public testing release.

### Changed

- Added clearly visible inset separators between Plan categories and between accounts while preserving clean group boundaries
- Kept the transaction Save button visible above both the built-in amount keypad and the Android system keyboard

### Fixed

- Removed the blocked focus overlay shown after tapping the Amount field in Add or Edit Transaction

## [0.1.0-alpha.11] - 2026-09-08

Eleventh public testing release.

### Added

- Added a category-specific Transaction button that preselects the category and returns to its details page after saving, cancelling or pressing Back
- Added direct Budget, Move Money and Auto-Assign actions to the redesigned category summary

### Changed

- Redesigned Category Details around a balance-focused summary card, compact category settings and a unified recent-activity card
- Moved category rename, visibility, deletion and current-month transactions into the top overflow menu
- Reused the expandable budget keypad for Category Details Budget, Move Money and Auto-Assign actions
- Made Plan category separators clearer while retaining the same first-, middle- and last-row behavior as Table view

### Fixed

- Opening a zero transaction or split amount now clears the displayed 0.00 before the first digit is entered

## [0.1.0-alpha.10] - 2026-09-08

Tenth public testing release.

### Added

- Added an account-specific Transaction button that preselects the open account and returns to that account after saving or cancelling
- Added persistent Icons only and Icons and names choices for the bottom navigation bar
- Added a global Current balance summary setting, synchronized with the account-page display menu
- Added account-page controls for showing the current balance summary and notes

### Changed

- Moved From, To and Available to move into the existing budget keypad instead of opening a separate Move Money page
- Made Auto-Assign suggestions expand inside the existing budget keypad
- Kept complete transaction calculator expressions visible while entering amounts, then replaced them with the final result on confirmation
- Renamed Working balance to Current balance throughout the interface
- Made Plan view progress bars, spending details and group totals independently configurable while leaving Table view unchanged

### Fixed

- Allowed large Plan group balances to use enough width instead of being clipped to a minus sign

## [0.1.0-alpha.9] - 2026-09-07

Ninth public testing release.

### Changed

- Rebuilt Move Money as a fixed full-page flow with From and To category selectors, balances, and a swap action
- Added an inline Move Money amount cursor and prevented confirmation when the amount exceeds the source balance
- Unified amount-entry keyboards around a compact equal-size grid with addition, subtraction, clear, decimal/sign, and backspace controls
- Removed multiplication, division, and separate equals controls from budgeting keypads
- Kept the compact Material key styling while standardizing the four-row keypad layout

## [0.1.0-alpha.8] - 2026-09-07

Bugfix testing release.

### Fixed

- Replaced the transaction editor's bottom action row with a fixed extended Save button
- Moved Cancel to the top-left close control and Edit-mode Delete to the top-right
- Kept transaction fields scrollable above the floating Save button

## [0.1.0-alpha.7] - 2026-09-07

Seventh public testing release.

### Changed

- Anchored the Plan Ready to Budget amount left and its label right
- Matched group-total amount typography to category balance amounts
- Kept the full category details page inside the app's safe content bounds
- Made recent category transactions open the shared view mode with Edit and Delete actions
- Slimmed every calculator key vertically and forced calculator sheets to open fully expanded
- Removed calculator drag handles, added compact close controls, and replaced budget action glyphs with Material icons
- Replaced the center Add tab with a dedicated Transactions tab and date-grouping controls
- Added an adaptive Material `+ Transaction` button to Budget, Accounts, Transactions, and Reports
- Replaced full-width transaction editor actions with compact Save, Delete, and Cancel buttons
- Hid the fixed Category field from transfer entry while preserving transfer behavior

## [0.1.0-alpha.6] - 2026-09-07

Sixth public testing release.

### Changed

- Replaced transaction cleared-state letters with compact green or gray check controls without changing amount alignment
- Transaction taps now open a read-only detail sheet with explicit Edit and Delete actions
- Global search transaction results now use the same detailed row and view flow as All Accounts
- Restored the compact single-line Ready to Budget overview in Plan view
- Aligned Table overview amounts and category balance pills to the same column anchors used by Plan view
- Unified transaction, Budget, To Budget, and Move Money calculators around one compact equal-size Material key grid
- Replaced Budget entry headings and separate amount labels with a focused inline amount and cursor
- Moved category details to a dedicated full-screen view with centered summaries and denser auto-assign choices
- Limited the To Budget pressed state to its rounded amount pill instead of the full overview cell

## [0.1.0-alpha.5] - 2026-09-07

Fifth public testing release.

### Added

- Global Material You search across transactions, accounts, payees, categories, notes, and transfer accounts
- Unified category budget sheet with Budget entry, Auto-Assign, Move Money, Details, recent transactions, notes, rollover, rename, hide, and deletion actions
- Screenshot-inspired Material You calculators for transaction amounts and category budgeting

### Changed

- Redesigned transaction rows with category chips, notes, cleared status, per-row dates when date grouping is disabled, and account context in All Accounts
- Transfers in All Accounts now identify both source and destination accounts, while individual account views omit the current account
- Saving a new transaction now opens All Accounts transactions
- Renamed Assigned to Budgeted and Available to Balance throughout the Budget views
- Made the complete category row open Budget entry in both Table and Plan views; amount fields no longer have separate tap actions
- Unified the Plan and Table overview layouts and added aligned pills to To Budget and Balance amounts
- Plan group Budgeted totals are shown only while the group is collapsed

## [0.1.0-alpha.4] - 2026-09-07

Fourth public testing release.

### Added

- Full-screen Material account, payee, and category selectors with immediate search, alphabetical sections, selected-item indicators, transfer-account grouping, new-payee creation, and account balances
- A persistent availability-focused Plan budget view alongside the existing table view
- Interactive Plan figures: Assigned opens assignment and money-moving actions, while Spent opens the category's transactions for the selected month
- Ready to Assign and To Budget funding flows for assigning money to categories or covering a negative To Budget balance
- Source of Fund/Income as the final Budget section, with Actual-backed received totals and income-safe actions

### Changed

- Adopted the original Actua Fold A as a fully scalable SVG and native Android vector icon
- Preserved the solid violet adaptive background with matching Android 13+ Material You vector geometry
- Aligned account working-balance values by moving the disclosure control beside the label
- Remembered collapsed account summaries and Budget category groups across navigation and app restarts
- Replaced always-open account and category note forms with compact tappable note rows and focused editors
- Added a tappable Budget month label with a Material month-and-year selector
- Ported Actuali's rule manager with searchable summaries, stage ordering, all/any conditions, typed values, entity pickers, and editable actions
- Added Actual-compatible CRDT rule creation, updates, deletion, schedule-owned rule protection, and native transaction execution
- Added editable primary and fallback Actual server URLs without disconnecting or replacing downloaded budgets, with automatic failover during connection and sync
- Allowed cleartext HTTP for the configured local Actual server at `192.168.68.109` while retaining Android's cleartext block for other destinations
- Renamed the independent Android client from Actuali for Android to Actua
- Changed the application ID and Kotlin namespace from `com.azimulkabir.actuali`
  to `com.azimulkabir.actua`
- Added an original Material You-ready adaptive launcher icon with a monochrome
  themed-icon layer
- Updated project documentation while preserving credit to Actuali for iOS and
  Actual Budget
- Transaction notes now use a compact single-line field
- Budget groups, categories, account sections, and account rows have clearer Material hierarchy
- Availability pills in Plan view use tighter corners and aligned amount text
- Saving or cancelling an edited transaction returns to its originating account

### Migration

- Android treats Actua as a separate app from earlier Actuali for Android alpha
  builds. Synchronize and back up local changes before removing an older build.

## [0.1.0-alpha.3] - 2026-09-06

Third public testing release.

### Changed

- The working-balance summary in account details can now be collapsed while keeping the current balance visible
- Account balance details and notes now use the same compact typography scale as the Budget tab
- Added restrained Material motion for main-tab changes, detail navigation, search fields, and expandable account summaries

## [0.1.0-alpha.2] - 2026-09-05

Second public testing release.

### Added

- Account details now show working, cleared, uncleared, and reconciled balances
- Synced notes for accounts and budget categories using Actual's native notes data
- Credit-card account details with available credit, limit, current billing cycle, cycle spend, and payment due date
- Category rollover-overspending control and history-based quick assign suggestions
- Full split transaction entry and editing with per-line category, amount, optional payee, note, direction, remaining amount, line addition/removal, and collapse back to a normal transaction

### Fixed

- Transaction forms now scroll through fields and actions within the available screen and keyboard space
- Expense, Income, and Transfer selector labels are centered consistently
- Add mode now has an explicit Cancel action; Edit mode has working Save, Delete, and Cancel actions
- Split edits retain existing child transaction identities instead of unnecessarily replacing every line
- Existing split transactions can be safely converted back to standard transactions

## [0.1.0-alpha.1] - 2026-09-05

Initial public testing release.

### Added

- Native Jetpack Compose interface for Budget, Accounts, Add, Reports, and More
- Password connection to self-hosted Actual servers
- Remote budget selection, download, local SQLite storage, and offline access
- Actual-compatible encrypted CRDT synchronization with manual and background sync
- Budget overview, month navigation, category groups, collapsible rows, totals, progress bars, and editable budget amounts
- Persistent category and group hiding with hidden-category management
- Account balances, on-budget/off-budget grouping, monthly income/expense/net summary, and transaction browsing
- Full local transaction history with search and optional date grouping
- Expense, income, and transfer entry with searchable account, payee, and category fields
- Transaction editing, clearing, deletion, splitting, date pickers, notes, and calculator amount entry
- New-payee creation from transaction entry
- Long-press actions for accounts, groups, categories, and transactions
- Local backup, restore, retention, and pre-restore revert support
- Rules and scheduled-transaction backend processing
- Credit-card limits, statement cycles, due dates, cycle spend, and available-credit display
- Basic reports backed by local budget data
- Light, dark, and system appearance modes
- Configurable start page, decimal visibility, balance privacy, transaction grouping, and account summary
- Currency display options for None, BDT, USD, EUR, GBP, CAD, AUD, JPY, INR, CNY, SGD, AED, and SAR
- Optional symbol-only currency formatting
- App name and icon matching the original Actuali visual identity

### Known limitations

- This build is alpha software and should be used with tested backups
- The APK is debug-signed for sideload testing, not Play Store distribution
- OpenID Connect, custom proxy headers, advanced dashboards, bank-feed setup, and schedule-management UI are not yet included
- Some advanced entity merge, reorder, template, goal, and automation workflows remain incomplete
- Apple-only features from the iOS project are intentionally excluded

### Credits

- [Matt Farrell's Actuali for iOS](https://github.com/MattFaz/actuali) is the upstream behavioral and design reference
- [Actual Budget](https://github.com/actualbudget/actual) provides the underlying budgeting platform and source reference for CRDT behavior
- The Actuali icon was designed by [u/bdownz](https://www.reddit.com/user/bdownz/)

[0.1.0-alpha.1]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.1
[0.1.0-alpha.2]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.2
[0.1.0-alpha.3]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.3
[0.1.0-alpha.4]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.4
[0.1.0-alpha.5]: https://github.com/azimul-kabir/actua/releases/tag/v0.1.0-alpha.5