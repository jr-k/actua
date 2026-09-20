# Actua release smoke-test checklist

Use this checklist for release candidates before promoting a beta or stable build. CI remains the automated gate; this checklist covers device, integration, and user-flow behavior that repository tests cannot fully prove.

## Release identity and install

- [ ] Android CI is green for the exact release commit.
- [ ] Release APK is produced from the intended commit/tag and has the expected version name/code.
- [ ] Since release builds are now R8-minified: exercise PDF statement import/export (pdfbox-android), background sync (WorkManager), and general Compose navigation on the installed release APK, and confirm no `ClassNotFoundException`/`NoSuchMethodException` crashes from shrinking or obfuscation.
- [ ] Release builds now embed a baseline profile: confirm the release APK's cold start and initial navigation feel at least as fast as the previous release's on the primary test device (no regression); a real-device Macrobenchmark run per `docs/PERFORMANCE_BASELINE.md` is preferred where available.
- [ ] APK installs as an update over the previous production-signed Actua build without removing app data.
- [ ] `Actua Test` still installs side-by-side with production Actua and keeps separate app data.
- [ ] Fresh install launches successfully on Android 9+ and the current target Android version.
- [ ] Upgrade install launches successfully with an existing downloaded budget.

## Safety and data

- [ ] Create an independent Actual backup before testing against a real budget.
- [ ] Add a custom HTTP header (e.g. `CF-Access-Client-Id`/`Secret`) on the Connection screen and verify sync/login succeeds against a server that requires it; verify removing the header still works against a server that doesn't.
- [ ] Connect to a self-hosted server over plain HTTP on a private/local address (e.g. `192.168.x.x`, `10.x.x.x`, `localhost`) other than the old hardcoded test IP and verify it succeeds instead of "Cleartext HTTP traffic not permitted".
- [ ] Install a self-signed CA as a user certificate (Settings > Security > Encryption & credentials > Install a certificate, including on GrapheneOS) and connect to a self-hosted server using a certificate issued by it over HTTPS; verify the connection succeeds instead of `Trust anchor for certification path not found`.
- [ ] Connect to a self-hosted HTTPS server whose certificate isn't trusted by Android and verify the "Server certificate isn't trusted" dialog shows host, issuer, validity and SHA-256 fingerprint; trust it and confirm the connection succeeds, then swap the server's certificate and confirm the "Server certificate changed" warning appears before anything is re-trusted.
- [ ] On Connection & data, tap the Budgets section header and verify the budget list collapses/expands with the chevron animating, and other sections (Backups, etc.) are unaffected.
- [ ] Download/select a real self-hosted Actual budget and verify opening balances/category values against Actual.
- [ ] Manual Sync Now completes and the sync status/last-success state updates.
- [ ] Make one harmless edit in Actua, sync, and confirm it appears correctly in Actual.
- [ ] Make one harmless edit in Actual, sync Actua, and confirm it appears correctly in Actua.
- [ ] Verify a transfer remains a paired transfer and balances stay symmetric after sync.
- [ ] Verify split transaction create/edit and sync on a disposable/test transaction.
- [ ] Confirm demo budget never attempts server sync and can be reset.
- [ ] Create a local backup, export it, restore it, and verify the pre-restore revert path.

## Budget and category flows

- [ ] Budget opens in the configured default Plan/Table view and month navigation works.
- [ ] Ready to Budget/To Budget, Budgeted and Balance values agree with Actual for the test month.
- [ ] Verify ordinary category progress bars show spending (including carryover) and match Actual/Actuali for a category with carryover, and that goal-only/save-by-date targets still show balance-funded progress.
- [ ] Rapidly switch bottom-navigation tabs and confirm the selection indicator updates immediately, each tab keeps its scroll position, and Reports shows a loading state then data without freezing the UI.
- [ ] Edit a category budget amount with the keypad and verify exact-cent persistence.
- [ ] Move money category-to-category and category-to-budget and verify both sides.
- [ ] Category details show notes, recent activity, rollover setting and target information correctly.
- [ ] Auto-Assign works for a supported target and produces the expected amount.
- [ ] Hide/unhide category/group and verify the state survives refresh/relaunch.
- [ ] A category with an active budget-automation goal shows its progress bar filling toward the goal as it's funded, and dropping when the goal amount is spent; a category without a goal still shows plain spend-down progress.
- [ ] Fund a multi-month "Have amount by a date" or "Cover scheduled transaction" target partway and verify the progress bar reflects overall goal progress, not just the current month's installment; verify the progress bar reflects the goal immediately after saving, without needing a whole-budget "Apply Templates" run first.
- [ ] Budget toolbar filter chips (Overspent, Underfunded, Overfunded, Money Available) narrow the category list correctly and the selection persists after navigating away and back.
- [ ] Star a category from Budget/category details and an account from Accounts; verify both appear in Home's Favorite Categories/Accounts sections and the category also appears when Budget's new Favorites filter chip is enabled (composed with an existing filter chip, not replacing it); unstar and verify both disappear; verify the existing Favourite Categories widget reflects the same category favorites.
- [ ] The budget month selector shows the month label with a small down-chevron (no separate step arrows); tapping anywhere on the row opens the month/year picker, and the tap ripple hugs the label instead of stretching across the toolbar.
- [ ] Switching between "Move to Category"/"Hold for Next Month" and between "Auto-Assign"/"Move Money" in the budget action sheet animates height and content together with no flicker or instant snap; tapping "Details" plays the sheet's hide animation before opening category details.
- [ ] Tap the Ready/To Budget amount (Plan or classic view) and verify the Budget Summary sheet opens already expanded showing "Move to Category" (category selector, amount field, calculator) with no extra tap needed; "Hold for Next Month" and "Reset Hold" remain reachable; on an envelope budget, use "Hold for Next Month" to buffer part or all of the amount, verify it carries to next month, then "Reset Hold" and confirm it's cleared; the hold/reset tiles are unavailable on non-envelope budgets.
- [ ] Manage → Categories → Manage Categories: create/rename/hide a group and a category, delete a category, and move a category to another group; drag a category within its group using the handle (no long-press needed) and verify it persists once on drop (not on every row crossed) and auto-scrolls near the list edges; verify a failed persist reverts the drag.
- [ ] Manage → Categories → Reorder Groups: drag a group to a new position with the up/down buttons as a non-drag alternative; verify the new order persists and syncs, and the income group's position stays fixed.
- [ ] Manage → Categories → automation editor: open a category's Budget Automation page and verify the "Automations" list (Fixed amount, Cover schedule, Save by date, % of income, From history, Refill to cap, Whatever is left) and "Options" section (Balance cap, Long-term goal) each allow at most one entry; set a Balance cap and a Refill to cap together and verify Refill to cap tracks the Balance cap amount instead of taking its own; verify Fixed amount's period (day/week/month/year), Save by date's repeat/early-spending options, and Cover schedule's savings mode and schedule picker (select/change/clear) all save and reload correctly, including the note field on each automation.
- [ ] Set a Cover schedule or From history (average) automation's signed increase/decrease adjustment, save, and verify it reloads correctly; set % of income to a specific income category (not just available funds/all income), save, reopen the editor and verify the selected category round-trips instead of reverting to the default.
- [ ] Budget screen overflow menu → "Copy last month's budget": verify it copies the previous month's budgeted amounts into visible expense categories (and visible income categories on a tracking budget) for the selected month, and leaves hidden categories/groups unchanged.

## Transactions and accounts

- [ ] Add expense, income and transfer transactions.
- [ ] Create a new account and pick a non-default type from the picker; verify it syncs with the correct type. Use "Change account type" on an existing account and verify the change persists and syncs.
- [ ] Add and edit a split transaction.
- [ ] Typing `#` (or deleting characters) in a transaction note while suggestions are showing keeps the on-screen keyboard open without flicker.
- [ ] In Transactions, enable multi-select, select several transactions, and verify bulk mark cleared/uncleared, delete (with confirmation naming the count), link to schedule, unlink schedule, and (single selection) view schedule.
- [ ] Long-press a transaction to enter multi-select mode with it pre-selected; use "Duplicate" from the transaction sheet, details sheet, and bulk-actions menu and verify an unlinked copy is created immediately without opening the editor.
- [ ] From the multi-select bulk menu, open "View schedule" on a linked transaction, then back/save/delete on the schedule and verify you return to Transactions rather than Schedules or Bills calendar.
- [ ] Transactions screen status filter chips (Uncategorized, Uncleared, Cleared, Reconciled) each narrow the list correctly and can be combined/cleared.
- [ ] Enable "hide reconciled transactions", then select the Reconciled status chip in an account's transaction list and verify reconciled transactions appear (not an empty "No unreconciled transactions" state); verify the empty-state message matches whichever status chip is active.
- [ ] Pick an explicit category in Add Transaction before typing a payee that matches a rule setting a different category, and verify the explicit choice is preserved; leave the category empty and verify the rule still fills it in.
- [ ] Payee search filters character-by-character across normal payees and transfer accounts.
- [ ] Find nearby payees requests foreground permission only after explicit use and normal search remains available on denial/failure.
- [ ] Indoors, Find nearby payees succeeds from a recent valid fix or an enabled network/fused source when GPS alone cannot obtain a fix.
- [ ] Payee locations created in Actual Budget sync into Actua and appear within 500 metres without being recorded again in Actua.
- [ ] With recording enabled, save an ordinary transaction and verify its location appears under Manage → Settings → Privacy → Payee Locations.
- [ ] Verify transfer and blank payees never record a location and same-payee samples within 500 metres are deduplicated.
- [ ] Delete one saved location, then clear all for a payee, sync, and verify the tombstones are reflected in Actual.
- [ ] Global search finds transactions, accounts, payees, categories, notes and transfers.
- [ ] `#tag` renders with its configured Actual color consistently across the main Transactions tab, account lists, transaction detail, search results and category recent-activity.
- [ ] Typing `#` in a transaction note offers matching/creatable tag suggestions; selecting one inserts it correctly and syncs.
- [ ] Manage → Tags: create, edit (color/hidden), rename and delete a tag; renaming updates matching transaction-note hashtags.
- [ ] Tapping a managed tag opens its matching transactions, including parent/split notes, with the active filter clearly shown and clearable.
- [ ] Cleared/uncleared/reconciled balances agree with the source budget; account detail shows an always-visible Cleared / Balance / Uncleared row (left/center/right aligned), with Reconciled (and, for credit cards, Available credit / Credit limit) behind the collapsible toggle.
- [ ] Enable "Running balance" from an account's transaction register overflow menu and verify each row shows the correct balance after that transaction (now on the category row, not stacked under the amount), including across transfers, splits and the opening balance, and that the setting persists after navigating away and relaunching.
- [ ] In a transaction row: the cleared/selection indicator sits left of the payee and top-aligns with it; the category chip and notes sit flush left under the payee; the right side shows amount, then running balance (or, in the cross-account Transactions tab, the account name colored like the category chip), then date; the cleared checkmark uses the theme's primary color.
- [ ] Open a transaction's details sheet and verify spacing between the amount and the cleared tick in the header.
- [ ] Reconcile an account using a known bank balance and verify the resulting locked/reconciled rows.
- [ ] Reconciled-transaction filtering works in account and all-transactions views.
- [ ] Credit-card limit, cycle spending and due-date information render correctly when configured; the account's Billing cycle card shows the current cycle's date range and a "Statement history" link.
- [ ] Tap a credit card's history icon (or the Billing cycle card's "Statement history" link) and verify the last 3 closed statements list with correct due amounts; opening a statement shows the correct transactions for that billing cycle; use the system back gesture/button from the statements list and from a statement's transaction list and verify it returns to the account page instead of skipping to Main.
- [ ] Open an account's dropdown menu and toggle "Show credit card section" off/on; verify the billing cycle/statement history section hides/shows accordingly.
- [ ] Toggle the global "Notes" setting under Display off and verify the notes field is hidden on accounts and budget categories; toggle it back on and verify notes reappear unchanged.

## Rules, schedules and reports

- [ ] Existing supported Actual rules load and can be edited without corrupting their JSON/conditions.
- [ ] Create/edit/delete a supported rule and confirm it syncs correctly.
- [ ] Create a transfer with a rule configured to match its destination account; selecting the destination applies the rule's Notes/Cleared/etc. in the editor, and the saved transfer stays correctly linked.
- [ ] Post or edit a schedule's linked transaction: its own schedule-linked rule still applies, and an unrelated rule linked to a different schedule does not; a rule with a recurring-date condition matching the schedule's own recurrence evaluates correctly instead of never matching.
- [ ] Create a schedule occurrence out of order or with a future-dated linked transaction, then let the catch-up loop run; verify an earlier due/missed occurrence still posts instead of being masked.
- [ ] Scheduled Transactions list opens and status/date/amount alignment is correct.
- [ ] Create/edit a recurring schedule and verify recurrence preview.
- [ ] Exercise Post, Post today, Skip next date and linked-history/unlink flows on test data.
- [ ] Manage → Automation exposes separate Bills & Calendar and Scheduled Transactions entries.
- [ ] Bills & Calendar loads scheduled/card-bill entries and filters/actions work; Back returns to the correct origin.
- [ ] Reports dashboard loads its saved ordering and representative report cards render without crashes.
- [ ] Check Summary, Net Worth, Cash Flow, Spending and at least one advanced report card against known data.

## Android integrations

- [ ] Foreground/manual sync works with network available and reports failures visibly.
- [ ] While a manual sync is in progress, scrolling Transactions/Budget and saving a new transaction remain responsive with no visible stall or SQLITE_BUSY-style hang.
- [ ] Periodic/background sync is scheduled and does not create duplicate schedule postings.
- [ ] Backup WorkManager job remains configured after relaunch.
- [ ] Credit-card reminder permission flow behaves correctly and reminders can be enabled/disabled.
- [ ] Launcher long-press shortcuts open Expense, Income, Transfer and Search in the correct destination.
- [ ] Budget Snapshot widget works at 2x2 and compact 3x1/4x1 sizes.
- [ ] Quick Transaction widget works at 2x2 and compact 3x1/4x1 sizes and all three actions open correctly.
- [ ] Favourite Categories and Account Balances widgets configure, refresh and deep-link correctly.
- [ ] Upcoming Schedules widget shows overdue-first ordering, relative due labels (Today/Tomorrow/In N days/N days overdue), respects hide-balances, refreshes after schedule mutations and sync, and tapping it opens Scheduled Transactions.
- [ ] Upcoming Schedules widget switches to its compact layout at the smallest resize size without clipped or overlapping content, and back to the full layout when resized larger.
- [ ] Upcoming Schedules widget's 7/14/30-day period is chosen when the widget is added, persists per widget instance, and can be changed later through the reconfigure entry point.
- [ ] Widget amounts respect hide-balances, currency and decimal display preferences.

## UI and navigation

- [ ] Bottom navigation labels the operational hub **Manage** with the Tune icon in labeled and Icons only modes; its separate Settings gear opens general preferences.
- [ ] Manage → Settings and Settings → preference pages animate forward; header Back and Android Back animate in reverse through the same hierarchy.
- [ ] An upgrade with **More** stored as the start page opens **Manage** and persists the migrated value.
- [ ] Bottom navigation preserves per-tab state and root reselect/scroll-to-top behavior.
- [ ] Bottom navigation is Home | Budget | Transactions | Accounts | Manage (Reports is not a bottom tab); Home shows Ready to Budget, Favorite Categories, Favorite Accounts, Upcoming, This Month, Reports and Recent Activity, each tapping through to its full screen; tapping a favorited report shortcut opens Reports with that report selected; the Add Transaction button is available from Home; a device previously set to start on Reports opens Home instead after upgrading.
- [ ] From Home's app bar, open Customize Home: hide/show an optional section, reorder sections via drag or the up/down arrows, and Restore Defaults; verify the layout survives navigating away and an app relaunch.
- [ ] Android Back behaves correctly from details, search, preferences and transaction flows.
- [ ] Add/edit transaction Save button, keypad and selectors remain usable with the software keyboard open.
- [ ] Light/dark/system appearance and Material You rendering remain legible.
- [ ] On Android 12+, Settings > Display "Material You colors" toggle switches between the app's own brand palette and wallpaper-derived dynamic color, in both light and dark mode; the setting persists across relaunch and the toggle is hidden/inert below Android 12.
- [ ] Spot-check Budget, Transactions, Reports, Manage/Settings sub-screens, and common dialogs/bottom sheets after the design-system pass: consistent corner rounding and spacing, no visual regressions from the previous release, and paid/cleared/due-soon status colors remain legible in both themes.
- [ ] Confirm the default (non-Material You) theme shows the brighter violet/orchid palette in both light and dark mode, with success/warning accent colors still legible against it.
- [ ] No obvious clipping, blank space, overlapping text or inaccessible actions on the primary test device.
- [ ] Typing in the Transactions search field and toggling row selection feel responsive with no visible lag on a large transaction list; switching Budget between Plan/Table view and scrolling through many category groups feels smooth with no stutter at income/Plan/Table section boundaries.
- [ ] Budget category details, Accounts list scrolling (including a budget with many credit-card accounts), and opening Add/Edit Transaction's payee/category/account pickers all remain responsive while typing.

## Release/distribution

- [ ] README version/download badges and release notes match the release being published.
- [ ] CHANGELOG contains the release changes and user-facing safety notes where relevant.
- [ ] GitHub release contains the expected signed APK and checksum.
- [ ] Obtainium can discover the new prerelease/release as intended.
- [ ] F-Droid verification workflow runs only after the Android Release workflow succeeds.
- [ ] Discord release notification/changelog is posted once.

## Sign-off

Record the release candidate, commit SHA, device/Android version, Actual server version, tester, date, failures found, and whether each failure blocks release.

A release should not be promoted to stable while any safety/data-integrity item is unresolved. Beta releases may carry known non-critical UI issues only when they are documented and do not risk budget data.