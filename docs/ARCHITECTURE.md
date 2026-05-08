# Architecture

## Goal

MarisGuard is moving from a single-source monolith to a version-aware layout.

## Intended split

- `core`
  - plugin bootstrap
  - config loading
  - shared anti-ESP rules
  - shared service interfaces
- `nms-v1_20`
  - 1.20.x CraftBukkit and NMS bridge
- `nms-v1_21`
  - 1.21.x CraftBukkit and NMS bridge
- `nms-v26_1_2`
  - 26.1.2 CraftBukkit and NMS bridge

## Known blockers

The following areas still directly reference NMS from the shared source tree:

- `MarisGuard.java`
- `playertrace/PlayerVisibilityRaytraceService.java`
- `raytraceantixray/*`

These must be extracted behind interfaces before the repository becomes truly multi-version.
