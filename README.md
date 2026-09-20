# Actua

**A native Android client for [Actual Budget](https://actualbudget.org/), built with Kotlin and Jetpack Compose.**

[Download APK](https://github.com/azimul-kabir/actua/releases/latest) • [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/azimul-kabir/actua) • [Discord](https://discord.gg/FyGxRjmhw) • [Report an issue](https://github.com/azimul-kabir/actua/issues/new/choose)

![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?logo=kotlin&logoColor=white)
![Beta](https://img.shields.io/badge/status-beta-F0A44B)
![License](https://img.shields.io/badge/License-MIT-48506A)

<p align="center">
  <img src="artwork/actua-hero.png" alt="Actua for Android showing budget, category, transaction and bills screens" width="900">
</p>

## About

Actua brings Actual Budget to Android with a native Material You interface.

It connects directly to your self-hosted Actual Budget server, keeps a local copy of your budget for offline use, and synchronizes changes with Actual.

Actua is an independent community project and is not affiliated with or endorsed by Actual Budget.

## Features

- Native Material You Android interface
- Home dashboard with an at-a-glance financial overview (ready to budget, favorite categories/accounts, upcoming bills, this month, reports and recent activity), customizable via show/hide and drag-to-reorder
- App-wide favorites for categories, accounts and reports, shared by Home, the Budget favorites filter and the home-screen widget
- Password and OpenID/OIDC login
- Offline budgets with encrypted Actual sync
- Budgeting, categories and money movement
- Transactions, splits, transfers and reconciliation
- Category targets and budget automations
- Scheduled transactions and Bills calendar
- Accounts, credit cards and payment reminders
- Actual dashboard and reports
- Rules and automatic categorization
- CSV, XLSX, PDF, SMS and notification imports
- Location-aware payee suggestions
- Global search
- Automatic local backups and restore
- Home-screen widgets and launcher shortcuts
- Configurable currency, dates, numbers and appearance, with an optional Material You dynamic-color mode (Android 12+)
- Actual tag management, with colored `#tag` rendering, notes autocomplete, a Manage Tags screen, and tap-a-tag transaction filtering
- Built-in local demo budget

See [BACKEND_PARITY.md](BACKEND_PARITY.md) for detailed compatibility and implementation status.

## Screenshots

<table>
  <tr>
    <td align="center" width="50%">
      <a href="artwork/screenshots/budget-plan.jpg">
        <img src="artwork/screenshots/budget-plan.jpg" width="340" alt="Actua budget plan view">
      </a><br>
      <strong>Budget Plan</strong><br>
      <sub>Targets, progress and balances</sub>
    </td>
    <td align="center" width="50%">
      <a href="artwork/screenshots/category-details.jpg">
        <img src="artwork/screenshots/category-details.jpg" width="340" alt="Actua category details">
      </a><br>
      <strong>Category Details</strong><br>
      <sub>Budget, move money and auto-assign</sub>
    </td>
  </tr>

  <tr>
    <td align="center" width="50%">
      <a href="artwork/screenshots/accounts.jpg">
        <img src="artwork/screenshots/accounts.jpg" width="340" alt="Actua accounts overview">
      </a><br>
      <strong>Accounts</strong><br>
      <sub>On-budget, off-budget and cards</sub>
    </td>
    <td align="center" width="50%">
      <a href="artwork/screenshots/transactions.jpg">
        <img src="artwork/screenshots/transactions.jpg" width="340" alt="Actua transaction list">
      </a><br>
      <strong>Transactions</strong><br>
      <sub>Searchable, grouped activity</sub>
    </td>
  </tr>

  <tr>
    <td align="center" width="50%">
      <a href="artwork/screenshots/reconciliation.jpg">
        <img src="artwork/screenshots/reconciliation.jpg" width="340" alt="Actua account reconciliation">
      </a><br>
      <strong>Reconciliation</strong><br>
      <sub>Match Actua with your bank</sub>
    </td>
    <td align="center" width="50%">
      <a href="artwork/screenshots/bills-calendar.jpg">
        <img src="artwork/screenshots/bills-calendar.jpg" width="340" alt="Actua bills calendar">
      </a><br>
      <strong>Bills Calendar</strong><br>
      <sub>Recurring schedules and card bills</sub>
    </td>
  </tr>
</table>

## Try Actua

Actua requires **Android 9 or newer**.

Download the latest APK from [GitHub Releases](https://github.com/azimul-kabir/actua/releases), or add Actua to Obtainium for update notifications and in-place upgrades.

Don't have an Actual server handy? Open **Manage → Connection & Data → Try demo budget** to explore Actua using a completely local sample budget.

> [!CAUTION]
> Actua is currently beta software and can write changes to your synchronized Actual budget. Back up important budgets before testing.

## Credits

Actua was originally based on and informed by [Actuali](https://github.com/MattFaz/actuali), the open-source iOS client by Matt Farrell.

The project aims for compatibility with [Actual Budget](https://actualbudget.org/) while providing an independently implemented native Android experience.

## Community

Found a bug or have an idea? [Open an issue](https://github.com/azimul-kabir/actua/issues).

Want to discuss Actua, test beta builds, or help with development? [Join the Discord server](https://discord.gg/FyGxRjmhw).

## License

Actua is available under the [MIT License](LICENSE).
