# Actua backend parity

The original [Actuali for iOS project by Matt Farrell](https://github.com/MattFaz/actuali)
is the upstream behavioral reference for this independent Android port. A local
checkout may be available at `../actuali-ios/Actuali/Actuali` during development,
but must not be assumed by builds or tests. Android platform integrations
replace Apple-only APIs; portable financial semantics and Actual
protocol/database behavior should remain equivalent.

This is a reimplementation in Kotlin and Jetpack Compose, not a shared-code
build of the Swift application. See [README.md](README.md), [NOTICE.md](NOTICE.md),
and [LICENSE](LICENSE) for project scope and attribution.

## Version 1 boundary

Version 1 is a solid, usable Android budgeting client: password and OpenID/OIDC
server authentication, budget download/local storage, automatic and manual sync,
backup/restore, accounts/categories/payees, budget amounts and transfers,
transactions/transfers/splits, imported rules, scheduled transactions, category
targets, mobile reconciliation, synced report dashboards, Android credit-card
payment reminders, home-screen widgets, and launcher actions. Every action
displayed in the release UI must work.

Features that still require substantial new financial semantics or external
integrations remain outside the current version boundary, including full
budget/cleanup automation authoring, bank-feed setup, general transaction
notifications/new-transaction detection, and background place monitoring.
Apple-only integrations are not Android backlog items and will never be ported;
where an Android equivalent exists, Actua uses the native Android integration.

## Ported and tested

Backend compatibility is audited against Actual Budget v26.9.0 at commit
`59fe126f637d858c061e1eeedbef5436c8f2225a`; see
[the detailed schema and protocol audit](docs/ACTUAL_26_9_COMPATIBILITY.md).

- Budget archive validation, import, download, active selection, and export
- Password and OpenID/OIDC login through Actual's `/account/login` flow, including explicit
  login-method selection, browser authorization, a localhost-only callback listener,
  Actual session-token capture, and preservation of password login on mixed-mode servers
- Server file lifecycle endpoints, including Actual-compatible blank-budget creation/upload
  and exact-name confirmed server deletion with local cleanup
- Local-only demo-budget lifecycle with fixed `demo` identity, current-schema recreation,
  no cloud registration, explicit sync rejection, and Connection & Data launch/reset flow
- Demo seed coverage for checking, savings, credit-card and off-budget investment accounts;
  six months of transactions; paired card-payment transfers; cleared/uncleared/reconciled
  states; category targets; rules; scheduled transactions; notes; and dashboard report data
- Editable primary/fallback server addresses with explicitly scoped private-LAN HTTP support and automatic failover without replacing local budgets; HTTPS connections trust manually installed user CAs (not just the system store), so a self-signed/private-CA certificate on a self-hosted server works without rooting the device; alternatively a single server certificate can be explicitly trusted per host in Actua after fingerprint review, with hostname verification still enforced
- User-defined custom HTTP headers (e.g. `CF-Access-Client-Id`/`Secret`) sent with every
  request to the Actual server, configurable from the Connection screen for servers behind
  an auth proxy
- HLC, CRDT values/messages, protobuf sync protocol, Merkle tree, encryption
- Sync convergence loop and Android Keystore-backed credentials/keys
- Stored sync clock validation and legacy/epoch recovery from the message-log
  high-water mark, preserving pending edits and Merkle-guided restart recovery
- Actual schema migrations required by current Android reads
- Accounts, payees, category groups/categories, transactions, transfers, splits
- Actual-compatible payee-location schema migration, complete-row reads, nearby ranking,
  500-metre deduplication, CRDT create/delete mutations, Android foreground permission,
  automatic picker suggestions, inline Nearby/Save actions, transaction recording, and
  synchronized picker/management deletion
- Source-independent transaction-import candidates with local CSV, XLSX, and text-based PDF sources,
  configurable delimiter/column/date/sign mapping, reusable local profiles, bounded import
  history, review/edit/reject/bulk approval, normalized payee matching, malformed-row exclusion,
  explained existing/within-file duplicate warnings, and batched Actual-compatible writes
- On-device financial-message parsing for explicitly pasted/shared text and opt-in future
  notifications from an explicit allowed-app list, with configurable debit/credit terms, amount/date/reference/payee extraction,
  confidence labels, last-digit account hints, bounded normalized-candidate storage, and deletion
- Transaction form planning and atomic transaction mutations, including split
  creation, child-preserving edits, opposite-direction lines, and collapse to a
  standard transaction
- Multi-select mode in Transactions with bulk mark cleared/uncleared, delete,
  link to schedule, unlink schedule, and single-selection view schedule
- Query-level transaction status filters (Uncategorized, Uncleared, Cleared,
  Reconciled) mapped to database-layer SQL and surfaced as a Transactions FilterChip row
- Zero/reflect budget month calculations, carryover, To Budget, buffered
  hold-for-next-month writes, and exact-cent writes
- Synced account/category notes, per-account working/cleared/uncleared/reconciled
  balances, and category rollover-overspending preferences
- Shared compact calculator-style amount entry for budget and transaction writes,
  including complete expression display and predictable operator backspace editing
- Local backup snapshots, CRDT stripping, retention, validated document-picker import,
  restore, and one-shot revert
- Rule JSON parsing, schema translation, ranking, condition/action evaluation,
  named-payee resolution, live form previews, and rule application for incoming transactions,
  including transfer drafts matched through the destination account's canonical transfer payee;
  ports Actual's `shouldApplyRuleChange` precedence so an explicit user-picked category survives
  a matching rule while an empty/inferred category is still filled in
- Rule list/search/editor UI and Actual-compatible CRDT create, update, and
  delete mutations for supported condition and action schemas, with protection
  for schedule-owned rules; see [docs/RULES_PARITY.md](docs/RULES_PARITY.md) for the audited
  condition/action/ranking behavior and its implementation boundary
- Actual-compatible canonical tag data model, colored `#tag` rendering consistent across
  transaction list/detail surfaces, notes autocomplete, native tag create/edit/delete/color/hidden
  management, and tap-a-tag transaction discovery, synced from Actual's tag dataset; see
  [docs/tags.md](docs/tags.md)
- Timezone-free schedule day math, upcoming windows, lifecycle status, and
  transaction occurrence matching; see
  [docs/SCHEDULED_TRANSACTIONS_PARITY.md](docs/SCHEDULED_TRANSACTIONS_PARITY.md) for the
  itemized upstream parity audit
- Searchable Scheduled Transactions UI with new-schedule creation,
  paid/due/upcoming/missed/completed status, completed-history visibility,
  recurrence skipping, restart/completion, deletion, and linked transaction
  history/unlinking through the existing CRDT write path
- Daily/weekly/monthly/yearly schedule recurrence, monthly day/nth-weekday
  patterns, bounded endings, weekend solving, skipping, and previews, with a
  dedicated Android repeat editor for all supported options
- Schedule-owned condition extraction/build/merge with custom-rule preservation,
  amount-action synchronization, JSON paths, and value conversion
- Postable/forecast schedule database projection, effective next-date selection,
  payee mapping, closed-account filtering, duplicate-row defense, and payment dedup query
- Automatic schedule posting, catch-up loop, linked-transaction deduplication,
  recurring next-date CRDT advancement, daily per-budget gate, and dirty-pass retry
- Inclusive schedule list projection (including broken/completed/manual rows),
  custom-rule detection, paid-state lookup, and unique-name checks
- Schedule create/update/delete/next-date/complete write planning and generic
  CRDT persistence, including repair of missing rule and next-date rows and local JSON paths
- Schedule discovery transaction filtering, recurrence sweeps, matching, ranking,
  payee deduplication, create-form projection, and selectable Find Schedules UI
- Dedicated **Bills & Calendar** Automation destination, reusing the monthly recurring/card-bill
  calendar, due-date projection, paid-transaction matching, status totals, filters and safe schedule actions
- **Manage** bottom-tab hub for Automation, transaction/data tools and financial setup, with
  general preferences behind a Settings gear and automatic migration of legacy More start-page values
- **Home** first-class root destination (`Home | Budget | Transactions | Accounts | Manage`) replacing
  Reports' former bottom-tab slot; a financial command center with Ready to Budget, Favorite
  Categories, Favorite Accounts, Upcoming, This Month, Reports and Recent Activity sections, each
  routing into the existing authoritative screen/calculation rather than duplicating it. Reports
  remains a full destination, reachable from Home and Manage
- Home section customization (show/hide optional sections, drag-to-reorder, restore defaults) as a
  versioned, migration-safe local UI preference; reordering mutates in-memory state during the
  gesture and persists once on drop, not per row crossed
- App-wide device-local favorites for categories, accounts and report dashboards (`FavoritePreferences`,
  scoped per budget), shared by the Budget Favorites filter (composes with existing Budget filters),
  Home's Favorite Categories/Accounts/Reports sections, and the home-screen Favorites widget. This is
  intentionally local UI preference state, not a synced Actual field, and hidden/deleted/closed
  entities are filtered out of every surface that reads it
- Account, category, and category-group rename/close/hide long-press actions
  wired through CRDT mutations and immediate UI refresh
- Category deletion through Actual-compatible tombstone mutations, with existing
  transactions safely falling back to uncategorized
- Account/category/group creation with Actual transfer-payee, opening-balance,
  mapping, duplicate-name, and sort-order behavior, including a type picker on
  account creation and a "Change account type" action allowing `type` in the
  accounts CRDT field allowlist
- Entity mutation core completed for account deletion, category-group deletion,
  ordinary-payee deletion/merge, category reorder, and category-group reorder. All
  writes use synced CRDT messages; payee merges redirect `payee_mapping` before
  tombstoning source payees; account deletion tombstones its owned transfer payee;
  transfer payees cannot be independently deleted or merged; group deletion
  tombstones its categories before the group; and reorder uses Actual-compatible
  shove sort orders. Destructive UI remains opt-in only where a safe confirmation
  flow is present.
- Dedicated **Reorder Categories** screen (Manage > Categories) surfaces the category
  and category-group reorder mutation core with drag handles plus up/down buttons as
  a non-drag accessible alternative; categories can be dragged or moved between
  expense groups (adopting the destination group's income/hidden flags, mirroring
  category creation), while the income category group's position stays fixed
  per Actual semantics. Hidden groups/categories remain visible and reorderable
  in the list so ordering cannot be corrupted while hidden.
- Category context actions for budget editing, month/all transaction lists,
  paired budget transfers/overspending coverage, and reversible hide/show
- Android system-back integration for detail screens and bottom-tab history;
  canonical iOS settings hub entries are visible with incomplete destinations disabled
- Android WorkManager replacement for iOS lifecycle sync: network-constrained
  foreground, post-mutation, and periodic jobs; encrypted budgets; bounded retry;
  post-sync schedule posting/re-push; periodic local backup; five-second same-budget
  foreground/background completion coalescing; and active-screen refresh signaling
- Android home-screen widget snapshot generation from the selected local budget, including
  budget overview, configurable category and account rows, privacy-aware amount formatting,
  post-write/post-sync refresh, and transaction-entry deep links
- Read-only upcoming-schedules widget reusing the existing schedule status/effective-date
  pipeline, with overdue-first ordering, a per-instance configurable 7/14/30-day period,
  privacy-aware amount formatting, a compact layout for small sizes, and a Scheduled
  Transactions deep link; no financial mutation is reachable from the widget
- Android launcher long-press actions for preselected expense, income, and transfer entry plus search
- Manual Sync Now plus live idle/running/error, trigger, duration, last-success,
  last-app-open-refresh, and last-background-refresh status in Connection & Data
- Dedicated backup manager with private archives, independent app-background creation,
  retention, archive export, optional Storage Access Framework folder mirroring,
  restore, and one-shot pre-restore revert
- Foreground sync refresh and visible mutation failure reporting through Android snackbars
- Persistent app-wide decimal-place display preference
- Per-budget device-local quick-access favorites for categories, accounts, and report dashboards;
  these are intentionally not synced because Actual has no favorite fields. Budget's Favorites
  filter composes with the other category filters, and the Favorites widget reads the same
  category pins while safely omitting hidden or deleted rows.
- Reversible category/group hiding with explicit unhide actions while hidden rows are shown
- Exact-cent account, category, transaction, summary, and transaction-entry presentation;
  hiding decimals never changes stored values
- Real database-backed Budget overview and category-aware Accounts monthly income/expense/net
  totals that exclude transfers and uncategorized cash flow and classify split portions individually
- Actual income/source-of-funds categories rendered as the final Budget section,
  with received totals and income-safe contextual actions
- Persistent table and availability-focused Plan budget presentations
- Ordinary category bars show spent / (absolute spending + positive available balance),
  including carryover, with shared status colors and accessible spending labels across Plan,
  table, and details. This follows [Actuali `ffd527a6`](https://github.com/MattFaz/actuali/blob/ffd527a6c27c3dbc7a8e32f55ec1650106f0dba5/Actuali/Actuali/Models/Budget.swift#L154-L185).
  Actua deliberately retains balance-funded progress for goal-only, save-by-date, cover-schedule,
  and synced long-goal targets; monthly goal cells do not change ordinary spending progress.
- Budget category view filters (Overspent, Underfunded, Overfunded, Money Available) as a
  persisted FilterChip row alongside the existing hide-fully-spent and show-hidden filters
- Working previous/next budget month navigation, with reads and budget writes scoped to the selected month
- "Copy last month's budget" Budget screen action that copies the previous month's budgeted
  amounts for visible expense categories (and visible income categories for tracking budgets)
  into the selected month, leaving hidden categories and groups unchanged
- App-wide display currency selection (including no currency), symbol-only mode,
  and decimal-place presentation
- Category Spent amounts open the matching category transactions for the selected month
- Category details with notes, rollover overspending, and six-month history-based quick assign
- Actual-compatible UI-managed category targets for monthly spending, fixed monthly saving,
  save-by-date, refill-to-cap, weekly spending, recent-spending averages, and exact prior-month
  copy targets; target-aware
  Auto Assign; preview-first whole-budget application of supported targets in upstream priority
  order with multi-contribution, refill-cap and available-funds handling, safe Apply versus
  explicit Overwrite behavior, and one CRDT mutation batch;
  multi-automation list editing and atomic `goal_def` replacement for fully supported UI-managed
  definitions; goal-only balance targets with atomic budget/goal writes; weighted remainder
  distribution after ordinary priorities; current-month available-funds, all-income, and exact
  income-category percentage contributions selected from a real picker over the budget's actual
  income categories (round-tripped without being silently discarded on decode); exact prior-month
  copy targets; signed increase/decrease adjustment modifiers for Cover schedule and From history
  (average) automations; note-managed refresh with source preservation, loss-aware parse failure
  handling, and safe read-only disclosure of advanced templates
- Notes-managed month-end cleanup source/sink/overspend groups: cleanup-group identity
  resolution and orphan tombstoning, group-scoped and global weighted sink distribution with
  deterministic cent-preserving allocation, general overspend auto-fill, a preview-first
  "Month-end cleanup" whole-budget action, one atomic stale-checked CRDT budget/goal batch on
  confirmation, idempotent reapplication, and explicit disclosure of unparseable cleanup
  definitions; see [the audited behavior and staged boundary](docs/BUDGET_AUTOMATION_PARITY.md)
- Exact schedule-template reference codec (`scheduleId`/`name`) with explicit read-only
  disclosure for unresolved rows; resolved schedule funding now uses the existing recurrence
  projection, completed/past handling, amount-range midpoint, priority validation, Ready to Budget
  clamping, preview-first review, and atomic confirmation path
- Account details with notes (hideable app-wide, along with category notes, via a global
  "Notes" toggle under Display settings) and an always-visible three-column Cleared / Balance / Uncleared
  balance header matching the Actual PWA layout, with Reconciled (and, for credit cards,
  Available credit / Credit limit) behind a collapsible toggle
- Optional per-account "Running balance" register display computed from the account's full
  transaction history, including transfers, splits, and the opening balance
- Full mobile account reconciliation with bank-balance comparison, difference display,
  uncleared-transaction review, optional cleared adjustment, and atomic CRDT locking
  of every cleared stored row including split parents and children
- Collapsible account balance details with compact Budget-tab typography
- Credit-card account details with limit, available credit, current billing cycle
  (including its date range), cycle spending, and calculated payment due date using
  either a fixed due day or a legacy days-after-statement offset
- Credit card statement history: a "Statement history" link on the Billing cycle card
  opens the last 3 closed billing-cycle statements with their due amounts, each opening
  to its own transaction list, ported 1:1 from Actuali's `CreditCardCycle`; a per-account
  "Show credit card section" toggle in the account dropdown menu hides the billing
  cycle/statement history section entirely
- Opt-in Android credit-card payment reminders at 7, 5, 3, and 1 days before
  due, with permission handling, stale-work cancellation, delivery-time balance
  validation, and unpaid-first stable due-date sorting
- Database-backed complete-history transaction search, including live split-child
  payees, notes, imported descriptions and categories; stable database paging
- Character-by-character payee-picker filtering with one alphabetical result list
  across ordinary payees and matching transfer accounts
- Persisted app-wide reconciled-transaction filtering applied before database
  paging and search, shared by account and all-transaction lists
- Add/edit split transaction UI with per-line category, amount, direction, payee,
  notes, remaining allocation, and Actual-compatible child-row persistence
- Off-budget transaction category enforcement for standard and split create/edit
  flows, including clearing stale categories when an account changes
- Regression coverage for interrupted sync retries, transfer-pair symmetry,
  standard/split conversion, off-budget splits, rule JSON round trips, and
  credit-card due-date boundaries
- Synced Actual dashboard pages and ordered widget rows, Actuali-compatible
  widget time frames and shared rule conditions, and native Summary, Net Worth,
  Cash Flow, Spending, Markdown, Age of Money, Formula, Custom Report, Calendar,
  Crossover, Budget Analysis, Sankey, Balance Forecast, and Monte Carlo cards
  with unknown future widget-type disclosure

- Opt-in foreground-only location-aware payees: automatic picker lookup after permission using
  a recent valid cached fix or concurrent enabled Android providers; distance-labelled 500-metre
  ranking; inline closest-payee selection and per-payee Save actions; eligible new-transaction
  recording; Actual-compatible CRDT storage; schema-gated writes; and synchronized picker plus
  management deletion

## Remaining version 1 work

- Remaining advanced whole-budget template evaluation beyond the shipped UI-managed targets,
  notes-managed templates, and cleanup source/sink groups;
  see [the audited behavior and staged boundary](docs/BUDGET_AUTOMATION_PARITY.md)

## Post-v1 portable features

- Merging multiple selected transactions (field precedence and split reconciliation
  semantics are not yet defined; deliberately excluded from the multi-select bulk actions)
- Advanced split, formula, and template rule actions
- "Apply rule now" bulk re-application of a rule to existing transactions, a live
  matching-transaction preview in the rule editor, and automatic category-rule suggestion from
  repeated recategorization (see [docs/RULES_PARITY.md](docs/RULES_PARITY.md))
- Broader goal-template authoring beyond the category targets, target-aware Auto Assign, and
  cleanup source/sink groups already shipped
- SimpleFIN linking, download, reconciliation, and bank-feed pending-import approval
- Broader country/bank parser templates beyond configurable financial-message keywords
- General Android transaction notifications and new-transaction detection beyond
  the credit-card payment reminders already shipped
- Broader place metadata or background geofencing beyond the shipped foreground-only,
  opt-in 500-metre payee suggestions and synchronized location management

## Permanently excluded or replaced

- FinanceKit / Apple Wallet: excluded
- App Intents / Shortcuts: excluded; Android launcher actions provide the
  platform-native quick-entry/search equivalent where appropriate
- iCloud/Keychain/background-task APIs: replaced with Android storage, Keystore,
  and WorkManager equivalents

## Port maintenance

When upstream Actuali changes, compare the relevant Swift model, service, test,
and view behavior before changing Android. Port financial and synchronization
semantics with tests; adapt only platform presentation and lifecycle behavior.
Record deliberate exclusions here so the Android project never presents an
Apple-only feature as unfinished work.
