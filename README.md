# RebuyTracker

![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

**RebuyTracker** is an Android app for settling up home poker games. It logs buy-ins and rebuys while you play, takes everyone's chip count at the end, and tells each player exactly who to pay and how much — using the smallest possible number of transfers.

> The app UI is in Spanish, built for games settled via Bizum (Spain's instant payment system), but the logic works with any currency or payment method.

## The problem

At the end of a home game, someone always ends up doing maths on a napkin:

- Rebuys come from the bank's chips, but when the bank runs dry a player has to sell chips to another player — sometimes half and half.
- Those player-to-player debts pile up during the night.
- Then everyone counts their chips, and you still need to work out who sends money to whom.

RebuyTracker records every movement as it happens and does the whole settlement in one tap.

## Features

- **Game setup** — buy-in price, chips per buy-in, total chips in the bank, players.
- **Rebuys with automatic source split** — chips are taken from the bank first; if the bank can't cover the full amount, the app asks which player provides the rest (bank-only, player-only or mixed rebuys).
- **Live table summary** — money on the table, chips left in the bank, rebuy count and a full movement log with undo.
- **Late joiners** — add players mid-game.
- **Chip count with validation** — flags when the counted chips don't match the chips in play, with an option to apply a proportional correction.
- **Results** — net win/loss per player and the minimal list of transfers (`Ana → Luis · €12.50`).
- **Share** — send the summary straight to your group chat.
- **Persistence & history** — the game in progress survives app restarts; closed games are kept in a history.

## How the settlement works

For each player:

```
net = value of final chips − money put in (buy-in + rebuys) + money owed for chips sold to others
```

- Player-to-player rebuy debts are folded into each net balance, so nobody has to pay mid-game.
- All amounts are handled in integer cents; rounding is distributed so that balances always sum to exactly zero.
- If the chip count is off, chip values are rescaled proportionally (`chips in play / chips counted`) so the books still balance.

**Transfer minimisation:** debtors and creditors are first paired by identical amounts (one transfer settles both), then the largest debt is matched against the largest credit until everything is cleared. This guarantees at most `n − 1` transfers for `n` players and usually far fewer. (Finding the absolute minimum is NP-hard; this greedy approach is optimal for the vast majority of real games.)

The logic lives in [`Settlement.kt`](app/src/main/java/dev/rebuytracker/logic/Settlement.kt) and is covered by unit tests.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose, Material 3 (custom dark theme) |
| Architecture | Single-activity, MVVM with `StateFlow` |
| Persistence | `kotlinx.serialization` JSON in `SharedPreferences` |
| Testing | JUnit 4 |
| CI | GitHub Actions (unit tests + debug build) |

## Project structure

```
app/src/main/java/dev/rebuytracker/
├── data/          # Game model, rebuy split rules, local persistence
├── logic/         # Settlement: net balances and transfer minimisation
├── ui/
│   ├── screens/   # Home, Setup, Game, Count, Results
│   ├── components/# Shared Compose components
│   ├── theme/     # Colours and typography
│   └── GameViewModel.kt
└── util/          # Currency / chip formatting and parsing
```

## Getting started

Requirements: Android Studio (Ladybug or newer), JDK 17, a device or emulator running Android 8.0+.

```bash
git clone https://github.com/<your-username>/RebuyTracker.git
```

Open the folder in Android Studio, let Gradle sync, and run the `app` configuration.

### Run the tests

```bash
./gradlew testDebugUnitTest
```

## Roadmap

- [ ] Screenshots and a short demo GIF
- [ ] Light theme
- [ ] Per-player stats across the game history
- [ ] Export history to CSV
- [ ] Support for cash buy-ins paid upfront to a banker

## License

Released under the [MIT License](LICENSE).
