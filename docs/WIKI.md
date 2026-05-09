# MarisGuard Wiki

MarisGuard is a guard plugin for Paper and Folia. It combines a few different systems in one jar:

- loading screen handling
- raytrace anti-xray
- AntiEsp block masking and bait checks
- player visibility raytrace

This page stays on the practical side. It is meant to explain what the plugin does, which files matter, and what can be changed safely.

## Files

MarisGuard now uses three main config files:

- `config.yml`
- `checks.yml`
- `message.yml`

### `config.yml`

This is the main runtime config.

It contains:

- loading screen remover settings
- raytrace anti-xray settings
- AntiEsp reveal settings
- blacklist worlds
- storage settings
- player visibility raytrace settings

### `checks.yml`

This file holds the AntiEsp check logic.

Right now that mainly means the `esper` section:

- punishment settings
- bait session timing
- auto-check rules
- mining-related auto-check filters

### `message.yml`

This file holds the plugin messages.

It is safe to edit message text here without touching the code.

## Commands

MarisGuard now exposes two commands:

### `/esper`

Main AntiEsp command.

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

Permission:

```text
antiesp.esper
```

Alerts permission:

```text
esper.alerts
```

### `/marisguard reload`

Short alias:

```text
/ms reload
```

What it reloads:

- `config.yml`
- `checks.yml`
- `message.yml`

It also reapplies default keys if new entries were added in an update and reloads the in-memory AntiEsp base template.

Permission:

```text
marisguard.admin
```

## Loading screen remover

This part is there to reduce bad world-change behavior on the client side, especially loading terrain issues around world transfers and extra respawn packets.

Main settings:

```yml
debug: false
track-ticks: 80
death-bypass-ticks: 200
aggressive-same-environment: true
```

In normal use these usually do not need much adjustment.

## RayTraceAntiXray

This is not a basic block obfuscator. It works by deciding whether hidden blocks should be revealed to the client based on visibility logic.

Main settings:

```yml
settings:
  anti-xray:
    update-ticks: 4
    ms-per-ray-trace-tick: 75
    ray-trace-threads: 2
```

Per-world settings live under:

```yml
world-settings:
```

The most important values there are:

- `ray-trace`
- `ray-trace-distance`
- `ray-trace-third-person`
- `max-ray-trace-block-count-per-chunk`

### Important Paper requirement

Paper anti-xray still needs to be enabled on the server side.

If Paper anti-xray is disabled, this part of MarisGuard will not work correctly.

The important part is:

```yml
engine-mode: 1
```

Depending on the server fork, the exact config path may differ a little, but `engine-mode` still needs to be `1`.

## AntiEsp

AntiEsp does two separate things:

1. masks sensitive blocks from the client
2. runs bait checks for suspected ESP users

### Sensitive block masking

Main settings:

```yml
reveal-radius: 64
refresh-period-ticks: 10
mask-material: AIR
```

The old behavior was basically a radius reveal. That is no longer how it works.

Now a block must satisfy both conditions:

- it must be within the configured reveal range
- it must pass a clear raytrace path from the player's eye position

That makes it behave much closer to the player visibility raytrace system.

### Block entity handling

This can now be configured:

```yml
antiesp:
  mask-block-entity-packets: true
```

If this is enabled, block entity packets for currently masked blocks are cancelled.

### Sensitive block list

This is now configurable too:

```yml
antiesp:
  sensitive-blocks:
  sensitive-suffixes:
```

That means the set of blocks treated as sensitive is no longer locked in code only.

### Blacklist worlds

Blacklist worlds still live in `config.yml`:

```yml
blacklist-worlds:
```

Those worlds are skipped by AntiEsp and the related masking logic.

## Esper checks

Esper is the bait-check system used to catch players who are actually using ESP.

Its settings now live in `checks.yml`.

### Main options

```yml
esper:
  enabled: true
  punishable: true
  max-violations: 7
  punishment-commands:
```

### Auto-check behavior

The important part is:

```yml
esper:
  auto-check:
```

That section controls:

- interval
- parallel session limits
- cooldowns
- survival-only checks
- Y-level gating
- recent mining gating

### Recent mining filter

Auto-checks are no longer triggered just because somebody is low in the world.

The plugin now keeps a recent-mining window and only considers players for auto-check if they have actually been mining recently.

Relevant keys:

```yml
require-recent-mining: true
recent-mining-window-seconds: 45
```

This is there to reduce false flags for players who are just walking through caves under `Y 29`.

## Storage

Violation storage supports:

- `sqlite`
- `mysql`

Relevant section:

```yml
storage:
```

The Hikari pool name is now `Esper`.

For most smaller setups, SQLite is fine. MySQL only becomes necessary when external database control is actually useful.

## Player visibility raytrace

This system hides player entity packets client-side when players are fully blocked from view.

It does not touch the tablist and it does not call Bukkit hide-player methods.

Config:

```yml
player-visibility-raytrace:
```

Main values:

- `worlds`
- `max-distance`
- `check-period-ticks`
- `max-targets-per-tick`
- `candidate-refresh-ticks`

It also respects invisibility now. If the target is invisible or `viewer.canSee(target)` is false, MarisGuard does not force that player visible through this system.

## Startup version check

On a successful startup, MarisGuard checks the latest GitHub release and logs one of these:

- `You are currently using the latest version (x.x.x)`
- `The new version has been released (x.x.x)`
- `Unable to check for the new version.`

The message is intentionally short. It does not dump a long exception unless the code is changed to do so.

## Updating config files

MarisGuard now tries to merge new default keys into:

- `config.yml`
- `checks.yml`
- `message.yml`

That means when a new plugin version adds settings, existing files should pick up missing keys automatically instead of forcing a full reset.

There is also a legacy migration path for old `esper` settings that were previously stored inside `config.yml`.

## Build targets

The repository is split toward a multi-version layout:

- `api/`
- `core/`
- `nms-v1_20/`
- `nms-v1_21/`
- `nms-v26_1_2/`

Build examples:

```powershell
./gradlew :core:build "-PmarisTarget=paper-1.20"
./gradlew :core:build "-PmarisTarget=paper-1.21"
./gradlew :core:build "-PmarisTarget=paper-26_1_2"
```

Current release artifact:

```text
MarisGuard-1.1.jar
```

## Where to tune first

If the plugin needs practical tuning, these are usually the first places to look:

1. `blacklist-worlds`
2. `world-settings.<world>.anti-xray.ray-trace`
3. `ray-trace-distance`
4. `reveal-radius`
5. `refresh-period-ticks`
6. `player-visibility-raytrace.max-distance`
7. `esper.auto-check.*`

That is where most noticeable behavior changes come from.
