# AGENTS.md

## What this is

CatchRect is a single-activity Android arcade game (paddle catches falling squares). Built with Kotlin and the AGP Kotlin-DSL build. There is **no Jetpack Compose and no XML layouts** — the entire UI (menu, dialogs, leaderboard) is constructed programmatically in `MainActivity.kt`, and the game itself is drawn on a custom `SurfaceView`.

## Build & test

```bash
./gradlew assembleDebug          # build debug APK
./gradlew build                  # full build incl. lint
./gradlew lint                   # Android lint
./gradlew test                   # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest   # instrumented tests (app/src/androidTest, needs device/emulator)
./gradlew test --tests "com.helltar.catchrect.SomeTest.some method name"   # single test
```

`local.properties` must define the Android SDK location and `leaderboard.base.url=<url>`. That URL is injected at build time into `BuildConfig.LEADERBOARD_BASE_URL`; without it leaderboard/submit requests have no host.

## Deterministic replay (constrains gameplay changes)

The client never uploads a raw score. The engine records a `GameReplay` (seed + sparse per-tick paddle positions) which the leaderboard backend re-simulates and verifies before accepting; a mismatch is rejected. This means the engine must stay **deterministic** and its gameplay constants are effectively a shared contract — do not casually change `CatchRectGameConfig` values, RNG draw order, the fixed timestep, spawn logic, or catch detection, since that invalidates verification. Coordinate any such change with the backend before shipping.

## Game architecture

The game lives under `app/src/main/java/com/helltar/catchrect/game/`, split into three layers:

- **`engine/`** — `CatchRectGameEngine` is the deterministic simulation: pure gameplay state advanced at a **fixed `FIXED_DT = 1/120s`** timestep, seeded RNG, paddle/cube/score/lives. It records sparse `ReplayInput`s (only when rounded paddle X changes) into a `GameReplay`. **This is the verified part** (see Deterministic replay above). Do not introduce nondeterminism (wall-clock time, unseeded random, float-order changes) here.

- **`view/`** — `CatchRectSurfaceView` owns a dedicated render thread (`run()`), runs the fixed-timestep `engine.update()`, then draws via `CatchRectGameRenderer`. `CatchRectGameRenderer` is **purely cosmetic** (gradient background, glowing/rounded cubes, particle bursts, screen flash, score pulse) and reads engine state read-only. Changing the renderer never affects scoring or replays — it's the safe place for visual work. Also here: `GameSoundPlayer` (SFX), `BackgroundMusicPlayer` (music that speeds up with score).

- **`model/`** — plain data (`CubeType`, `FallingCube`).

### Threading model

The render thread is the single owner of the engine and renderer after init. The UI thread (touch/keys/insets from `MainActivity`) communicates **only** through `@Volatile` fields on `CatchRectSurfaceView` (`pendingTouchX`, `pendingRestart`, `keyDirection`, insets, button-visibility flags). Callbacks back to the UI thread (`onGameOver`, `onSubmitScore`, `onLeaderboardClick`) are posted via `mainHandler`. Keep this discipline: never touch the engine directly from the UI thread, and never touch Views from the render thread.

Cosmetic catch effects flow engine → view: `engine.drainCaughtCubeEvents { type, x, y -> ... }` delivers the caught cube's type and landing position so sound and particles fire at the right spot.

## Networking

`network/LeaderboardApi.kt` is a Ktor CIO client (singleton `LeaderboardApi.instance`) hitting `GET /leaderboard?playerId=` and `POST /submit` (gzip-compressed JSON body). `LeaderboardDtos.kt` holds the wire DTOs and the `GameReplay.toSubmitScoreRequest()` mapping; these field names form the backend contract and must not drift. The local player is identified by a generated `UUID` persisted in SharedPreferences (`local_player_id`); name/best-score/settings also live in SharedPreferences.

## Conventions

- Git commit subjects start with a **lowercase** letter (matches existing history, e.g. `add ...`, not `Add ...`).
