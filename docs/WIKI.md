# MarisGuard Wiki

This page is written as an actual usage reference. The goal is to explain what each part of the plugin does, where the important config lives, and which settings matter first in real server use.

## What MarisGuard currently includes

MarisGuard currently bundles four parts:

- `LoadingScreenRemover`
- `RayTraceAntiXray`
- `MarisEsp`
- `Player Visibility Raytrace`

`AntiFreeCam` is no longer part of the plugin. It was removed from both code and config.

## What each part is for

### 1. LoadingScreenRemover

This part reduces client-side loading terrain issues when players change worlds or when the server sends extra `RESPAWN` packets in sensitive cases.

Relevant config:

```yml
debug: false
track-ticks: 80
death-bypass-ticks: 200
aggressive-same-environment: true
```

Short meaning:

- `debug`: enables debug logging. Only useful while diagnosing issues.
- `track-ticks`: how long world-change tracking stays active.
- `death-bypass-ticks`: keeps death respawn bypass active longer.
- `aggressive-same-environment`: handles same-environment world changes more aggressively.

If the plugin is already stable on the server, these values usually do not need frequent changes.

### 2. RayTraceAntiXray

This is the raytrace-based anti-xray system. It does not work like a basic block obfuscator. Instead, it decides which blocks should be visible to the client based on visibility and line-of-sight logic.

Global config:

```yml
settings:
  anti-xray:
    update-ticks: 4
    ms-per-ray-trace-tick: 75
    ray-trace-threads: 2
```

Meaning:

- `update-ticks`: update interval.
- `ms-per-ray-trace-tick`: spacing between raytrace passes.
- `ray-trace-threads`: worker count for raytrace tasks.

If server smoothness matters more than aggressive checking, avoid raising `ray-trace-threads` without profiling first. More threads are not automatically better on Folia.

Per-world config:

```yml
world-settings:
  default:
    anti-xray:
      ray-trace: true
      ray-trace-third-person: false
      ray-trace-distance: 32.0
      rehide-blocks: false
      rehide-distance: .inf
      max-ray-trace-block-count-per-chunk: 24
      ray-trace-blocks: []
```

Main lines to care about:

- `ray-trace`: enables or disables anti-xray in that world.
- `ray-trace-distance`: how far raytrace visibility checks go.
- `ray-trace-third-person`: whether third-person view points are included.
- `max-ray-trace-block-count-per-chunk`: how many blocks are processed per chunk.

If ore or hidden blocks feel too slow to reveal, the first places to inspect are:

- `ray-trace-distance`
- `update-ticks`
- `ms-per-ray-trace-tick`

### 3. MarisEsp

This handles masked-block reveal logic and the bait-check system used to catch ESP users.

Main config:

```yml
reveal-radius: 30
refresh-period-ticks: 10
mask-material: AIR
```

Meaning:

- `reveal-radius`: reveal radius around the player.
- `refresh-period-ticks`: how often the reveal area refreshes.
- `mask-material`: client-side replacement material. It is currently `AIR`.

If blocks should reveal faster when players get close:

- increase `reveal-radius`
- reduce `refresh-period-ticks`

Blacklist worlds:

```yml
blacklist-worlds:
  - world_the_end
  - spawn
  - afk
  - duels
```

Worlds listed here are excluded from the anti-ESP / anti-xray logic covered by the current implementation.

### 4. ESP auto-check

This part runs bait sessions used to catch players who are actually using ESP.

Config:

```yml
esper:
  enabled: true
  punishable: true
  punishment-delay-in-seconds: 0
  max-violations: 7
  punishment-commands:
    - 'tempban %player% 30m use esp'
  duration-seconds: 15
  spawn-distance: 10.5
  forward-offset: 2.0
  trigger-distance: 2.0
  auto-check:
    enabled: true
    interval-ticks: 60
    max-players-per-cycle: 2
    cooldown-seconds: 90
    max-active-sessions: 4
    require-survival: true
    max-y: 29
```

The most performance-sensitive lines here are:

- `max-players-per-cycle`
- `max-active-sessions`
- `interval-ticks`

If the server is busy and these checks feel too aggressive, reduce concurrent sessions before changing the punishment logic.

### 5. Player Visibility Raytrace

This part does not touch the tablist. It only hides player entity packets client-side when all sampled visibility rays are blocked.

Config:

```yml
player-visibility-raytrace:
  enabled: true
  worlds:
    - world
  max-distance: 24.0
  check-period-ticks: 20
  max-targets-per-tick: 2
  candidate-refresh-ticks: 10
```

Meaning:

- `worlds`: worlds where this system is active.
- `max-distance`: maximum range used for player visibility checks.
- `check-period-ticks`: main check interval.
- `max-targets-per-tick`: processing cap per tick.
- `candidate-refresh-ticks`: how often candidate lists refresh.

Example: `max-distance: 24.0` means only players within 24 blocks are considered by this visibility system.

This part also respects invisibility now. If the target is invisible or `viewer.canSee(target)` is false, MarisGuard does not force that player visible again through this path.

## Storage

ESP violation storage currently supports:

- `sqlite`
- `mysql`

Config:

```yml
storage:
  type: sqlite
  sqlite:
    file: violations.db
  mysql:
    host: 127.0.0.1
    port: 3306
    database: antiesp
    username: root
    password: ''
    properties: useSSL=false&characterEncoding=utf8&serverTimezone=UTC
  pool:
    maximum-pool-size: 4
    minimum-idle: 1
    connection-timeout-ms: 10000
```

For a small or medium setup, `sqlite` is usually enough. `mysql` only becomes necessary if external database control or shared storage is actually needed.

## Suggested tuning

### If server smoothness is the priority

- keep `ray-trace-threads: 2`
- keep `ms-per-ray-trace-tick` relatively conservative
- avoid pushing `max-active-sessions` too high
- limit the worlds using `player-visibility-raytrace`

### If faster detection is the priority

- increase `reveal-radius`
- reduce `refresh-period-ticks`
- increase `player-visibility-raytrace.max-distance`
- reduce `check-period-ticks`

That comes with a clear CPU and packet cost. There is no configuration that is both aggressive and free.

## Version check on startup

After the plugin enables successfully, MarisGuard checks the latest GitHub release:

- if the running version is current:
  - `You are currently using the latest version (x.x.x)`
- if a newer release exists:
  - `The new version has been released (x.x.x)`
- if the check fails:
  - `Unable to check for the new version.`

This check is intentionally quiet. It does not print long stack traces.

## Build targets

The repository is being split into a version-aware structure:

- `api/`
- `core/`
- `nms-v1_20/`
- `nms-v1_21/`
- `nms-v26_1_2/`

Build commands:

```powershell
./gradlew :core:build "-PmarisTarget=paper-1.20"
./gradlew :core:build "-PmarisTarget=paper-1.21"
./gradlew :core:build "-PmarisTarget=paper-26_1_2"
```

Current release artifact name:

```text
MarisGuard-1.0.jar
```

## Current source state

The repository already has version-specific bridges for part of the packet handling, but the migration is not finished yet. `playertrace` and part of `raytrace packet sending` already use dedicated bridges. Larger NMS-heavy sections are still being moved out of `core`.

In short: the repository is on the right path, but it is not yet a fully clean multi-version split across every subsystem.

## Practical config order

If config tuning has to start somewhere, the most useful order is:

1. `blacklist-worlds`
2. `world-settings.<world>.anti-xray.ray-trace`
3. `ray-trace-distance`
4. `reveal-radius`
5. `refresh-period-ticks`
6. `player-visibility-raytrace.max-distance`
7. `esper.auto-check.*`

Those are the settings that usually change runtime behavior the most.
