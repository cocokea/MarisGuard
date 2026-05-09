# MarisGuard Wiki

MarisGuard is split into separate modules now. The idea is simple: root config stays small, module config stays where the feature actually belongs.

This page focuses on real usage. It explains which file controls what, which commands exist, and where to tune things first.

## Files

### `config.yml`

This file now only contains:

- `debug`
- `storage`

That is intentional. It keeps the root config clean and avoids mixing unrelated systems together.

### `message.yml`

This file contains plugin messages.

### `guis.yml`

This file contains the `/esper` GUI layout text:

- title
- previous page item
- next page item

### `modules/antixray.yml`

This file contains the raytrace anti-xray module:

- anti-xray scheduler timing
- worker thread count
- world blacklist for raytrace anti-xray
- per-world anti-xray settings

### `modules/antifreecam.yml`

This file contains the AntiFreeCam module:

- enabled state
- whitelist worlds
- chunk refresh radius
- chunk refresh budget

### `modules/antiesp.yml`

This file contains:

- AntiEsp reveal settings
- AntiEsp blacklist worlds
- sensitive block config
- Esper check settings
- Esper auto-check settings

### `modules/player-raytrace.yml`

This file contains the player visibility raytrace module:

- enabled state
- world list
- max distance
- period
- per-tick target limit

### `modules/hideEntity.yml`

This file contains the non-player entity hiding module:

- enabled state
- hide distance
- refresh ticks
- world list

## Commands

### `/esper`

Main Esper command.

Usage:

```text
/esper
/esper check <player>
/esper reset <player>
/esper alerts
```

What it does:

- `/esper` opens the GUI
- `/esper check <player>` starts a manual bait check
- `/esper reset <player>` clears stored violations
- `/esper alerts` toggles alerts for the sender

Permissions:

```text
antiesp.esper
esper.alerts
```

### `/marisguard reload`

Alias:

```text
/ms reload
```

What it reloads:

- `config.yml`
- `message.yml`
- `guis.yml`
- `modules/antixray.yml`
- `modules/antifreecam.yml`
- `modules/antiesp.yml`
- `modules/player-raytrace.yml`
- `modules/hideEntity.yml`

Permission:

```text
marisguard.admin
```

## RayTrace AntiXray

Main config file:

```text
modules/antixray.yml
```

Important sections:

- `settings.anti-xray`
- `world-settings`

Important Paper requirement:

Paper anti-xray still needs to be enabled server-side with:

```yml
engine-mode: 1
```

If Paper anti-xray is disabled, this module will not behave correctly.

## AntiEsp

Main config file:

```text
modules/antiesp.yml
```

Important keys:

- `reveal-radius`
- `refresh-period-ticks`
- `mask-material`
- `blacklist-worlds`
- `antiesp.mask-block-entity-packets`

AntiEsp is the block masking side. Esper is the bait-check side. They live in the same file now because they belong to the same detection flow.

## Esper

Esper settings are inside:

```text
modules/antiesp.yml
```

Main section:

```yml
esper:
```

Important parts:

- punishable
- max violations
- punishment commands
- duration
- spawn distance
- trigger distance
- auto-check

The auto-check logic also uses recent mining checks to avoid flagging random cave movement too aggressively.

## AntiFreeCam

Main config file:

```text
modules/antifreecam.yml
```

This module hides lower Y bands by forcing chunk refreshes when the viewer crosses the configured thresholds.

Important notes:

- It only runs in allowed worlds
- It uses whitelist worlds
- It is separate from AntiEsp
- It is separate from player raytrace

## HideEntity

Main config file:

```text
modules/hideEntity.yml
```

This module hides non-player entities when they are too far away from the viewer.

It does not touch players. Player handling is left to the raytrace module to avoid overlap.

## Player Raytrace

Main config file:

```text
modules/player-raytrace.yml
```

This module hides player entity packets client-side when the target is fully blocked.

It also respects invisibility and `viewer.canSee(target)`.

Important keys:

- `worlds`
- `max-distance`
- `check-period-ticks`
- `max-targets-per-tick`
- `candidate-refresh-ticks`

## Storage

Storage stays in:

```text
config.yml
```

Supported backends:

- sqlite
- mysql

SQLite is enough for most setups. MySQL only makes sense if the data needs to live outside the server box.

## Updating configs

MarisGuard merges missing default keys into:

- `config.yml`
- `message.yml`
- `guis.yml`
- `modules/antixray.yml`
- `modules/antifreecam.yml`
- `modules/antiesp.yml`
- `modules/player-raytrace.yml`
- `modules/hideEntity.yml`

That means updates do not need a full wipe every time a new key is added.

There is also migration logic for older layouts, so older mixed configs can move into the new module structure automatically.

## Build targets

Current build targets:

- `paper-1.20`
- `paper-1.21`
- `paper-26_1_2`

Build examples:

```powershell
./gradlew :core:build "-PmarisTarget=paper-1.20"
./gradlew :core:build "-PmarisTarget=paper-1.21"
./gradlew :core:build "-PmarisTarget=paper-26_1_2"
```

Current release artifact:

```text
MarisGuard-2.0.jar
```

## Where to tune first

If the plugin needs practical tuning, start here:

1. `modules/antixray.yml` -> blacklist worlds and per-world anti-xray toggle
2. `modules/antiesp.yml` -> reveal radius and refresh period
3. `modules/antiesp.yml` -> esper auto-check
4. `modules/antifreecam.yml` -> whitelist worlds
5. `modules/player-raytrace.yml` -> max distance and period
6. `modules/hideEntity.yml` -> distance and refresh ticks
