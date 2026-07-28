# Onfall

Onfall is an offline-first combat and encounter tracker compatible with 5e and 5.5e (D&D 2024).
Multiplayer support is planned for the future. The application includes a separate Italian SRD
5.2.1 content pack for guided character creation, class progression, feats, actions and spells.
Its attribution and licence notice are in [`NOTICE-SRD.md`](NOTICE-SRD.md). The engine, user
interface and bundled encounter examples are original, and desktop and Android share the same
engine, data, and screens.

> **Read only repository.** This code is published solely for review and technical evaluation, that
> is, for analysis of the code and of the author's technical ability. It is not meant to be used, run
> as a game, forked, or reused in any part. See [`LICENSE.md`](LICENSE.md).

## What it is

Four parts living on the same engine:

1. the **Compendium**, an editable archive of characters, creatures, and reusable abilities;
2. an **encounter builder** that turns Compendium templates into an independent game session;
3. a **combat engine** that is independent from the interface, deterministic, audited, and undoable;
4. a **game interface** that presents the fight as a turn based battle.

## Features

- Build an encounter from saved templates, choose each participant's faction and quantity, configure
  the grid and its scale, then start in either a pre-arranged Fight mode or a free-placement mode.
  A combatant is copied into the session, so changes to HP, conditions, turns, and position never
  alter its Compendium template.
- Create and edit character sheets, creature stat blocks, and a shared ability catalog. The
  Compendium also keeps a local library of portraits and map backgrounds that can be reused across
  sessions.
- Create SRD characters through guided, validated choices for all 12 classes and their SRD
  subclasses, then advance them from level 1 to 20 at the official XP thresholds. Class resources,
  proficiencies, feats, cantrips, prepared spells, spellbooks, always-prepared spells and derived
  Extra Attacks remain attached to the sheet and available to the Compendium and combat screens.
- Run attacks and area effects from the active combatant's abilities, with digital d20 rolls,
  advantage/disadvantage, damage types, saving throws, conditions, concentration, death saves,
  healing, temporary HP, and exhaustion. Table tools cover the same state changes when they need
  to be entered manually.
- Save named sessions and reopen them in independent tabs. A saved session includes the combat
  state, map and token placements, event log, and dice state, so its subsequent digital rolls stay
  reproducible. Named sessions are autosaved after changes and unsaved work is guarded on close.
- Keep an append-only event log and undo successful engine commands without advancing the random
  number generator.

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

Every creature has an immediate vector portrait drawn from code, so the default visual set has no
external image-licensing dependency. A local portrait library and map archive can also use images
chosen by the player.

## Screenshots

<table>
<tr>
<td align="center">
<img src="sample/Screenshot%202026-07-26%20at%2001.25.43.png" width="420"/><br/>
<sub>Starting a session — templates, blank Compendium, or a saved session</sub>
</td>
<td align="center">
<img src="sample/Screenshot%202026-07-26%20at%2001.26.02.png" width="420"/><br/>
<sub>Compendium, quick view — ability scores, speed, size, weapons and abilities</sub>
</td>
</tr>
<tr>
<td align="center">
<img src="sample/Screenshot%202026-07-26%20at%2001.26.08.png" width="420"/><br/>
<sub>Compendium, character sheet — background, class, AC calculation, hit points</sub>
</td>
<td align="center">
<img src="sample/Screenshot%202026-07-26%20at%2018.24.55.png" width="420"/><br/>
<sub>Battle screen — party, tactical map, enemies, turn order, event log</sub>
</td>
</tr>
</table>

## Architecture

| Module | Language | Role |
|---|---|---|
| `engine/domain-model` | Java 17 | actors, abilities, conditions, state, campaigns. Immutable, zero dependencies |
| `engine/core-engine` | Java 17 | seeded dice, state machine, append only audit, XP budget |
| `engine/persistence-json` | Java 17 | atomic saves, backups, import and export |
| `engine/character-rules` | Kotlin | versioned class choices, XP progression and class resources |
| `engine/sheet-model` | Kotlin | 2024 character sheet and 2025 monster stat block |
| `content/srd-5.2.1-it` | Kotlin/JSON | Italian SRD classes, feats, actions and spells (CC BY 4.0) |
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
