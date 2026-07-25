# Onfall

Onfall is a combat and encounter tracker compatible with 5.5e/SRD. It is offline first and has a
video game style interface. Desktop and Android share the same engine, data, and screens.

> **Read only repository.** This code is published solely for review and technical evaluation, that
> is, for analysis of the code and of the author's technical ability. It is not meant to be used, run
> as a game, forked, or reused in any part. See [`LICENSE.md`](LICENSE.md).

## What it is

Three products living on the same engine:

1. an **archive** of characters, creatures, and abilities (the Compendium);
2. a **combat engine** that is independent from the interface, deterministic, and undoable;
3. a **game interface** that presents the fight as a turn based battle.

## Interface

On the desktop the battle screen keeps three areas visible together: the **party** on the left, the
**battle scene** in the center, the **enemies** on the right. The turn order runs across the top, and
the event log stays below the enemies. The side columns **resize by dragging their edge**, and when
they get narrow each combatant's information folds into a vertical list instead of being truncated.

On the phone the same screen becomes one surface at a time (Stage, Party, Enemies, Log), with the
controls always within thumb reach.

The tactical map is a grid. You **zoom with the mouse wheel**, drag the tokens to move them, and
change the scale (feet per square) and the size on screen. With **Edit mode** on, the table composes
the scene freely: it corrects name, AC, HP, and initiative directly on the cards, reorders the turns
and picks the current one, drags characters from the side bars onto the map, and moves tokens
ignoring the movement limits. Outside Edit mode those shortcuts disappear, so a session is not
altered by mistake.

All portraits are drawn as vectors from code. There is no imported image, so every creature added
has an immediate visual representation and there are no licensing constraints on the graphics.

## Architecture

| Module | Language | Role |
|---|---|---|
| `engine/domain-model` | Java 17 | actors, abilities, conditions, state, campaigns. Immutable, zero dependencies |
| `engine/core-engine` | Java 17 | seeded dice, state machine, append only audit, XP budget |
| `engine/persistence-json` | Java 17 | atomic saves, backups, import and export |
| `engine/sheet-model` | Kotlin | 2024 character sheet and 2025 monster stat block |
| `shared-ui` | Kotlin + Compose MP | theme, components, screens, presentation state |
| `desktop-app` | Kotlin | JVM window, dense shell |
| `android-app` | Kotlin | Activity, touch shell |

The engine is Java and stays usable from both platforms because Android and desktop both run on JVM
bytecode. The shared UI lives in `jvmSharedMain` rather than `commonMain`, which lets it use the
engine's Java classes directly.

`core-engine` knows nothing about texts, classes, or monsters: the rules live in the engine, the
content in separate packages.


## Run


### Desktop

```bash
# standard run
./gradlew :desktop-app:run

# with Compose Hot Reload, useful during development
# (the main class must be passed explicitly from the command line)
./gradlew :desktop-app:hotRun --mainClass=app.d6d.desktop.MainKt

# with a custom data directory
./gradlew :desktop-app:run -Donfall.dataDir=/path/to/dir
```

On first run Gradle downloads the dependencies and compiles the engine and the
shared UI, so the window can take a minute or two to appear. The `run` task
stays attached to the running application: close the window (or press `Ctrl+C`)
to stop it.

### Android

With a device or emulator connected:

```bash
./gradlew :android-app:installDebug
```

Then launch Onfall from the device's or emulator's app launcher.

## License

**All rights reserved.** The code may be consulted **only for analysis and technical evaluation**. It
may not be run for use, forked, redistributed, or reused in part, not even single fragments, in other
projects. The full terms are in [`LICENSE.md`](LICENSE.md).


## Status

See `docs/STATO.md` for the honest map between the project brief and what actually runs.
