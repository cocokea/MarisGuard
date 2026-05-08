# MarisGuard

MarisGuard is a multi-feature Paper/Folia guard plugin that currently bundles:

- Loading screen remover
- RayTrace anti-xray
- MarisEsp
- Player visibility raytrace protection

## Project layout

This repository is being migrated toward a public-source multi-module layout:

- `core/` - shared plugin entrypoint and common logic
- `nms-v1_20/` - Paper 1.20.x specific adapter layer
- `nms-v1_21/` - Paper 1.21.x specific adapter layer
- `nms-v26_1_2/` - Paper 26.1.2 specific adapter layer
- `gradle/targets/` - per-target build configuration

At the moment, the root `src/` tree is still the active source set for `core`, and version-specific NMS classes have not yet been fully extracted into the `nms-v*` modules.

## Build targets

Use the `marisTarget` Gradle property:

```powershell
./gradlew :core:build "-PmarisTarget=paper-1.20"
./gradlew :core:build "-PmarisTarget=paper-1.21"
./gradlew :core:build "-PmarisTarget=paper-26_1_2"
```

## Current status

- `paper-1.21` can compile with the current local toolchain.
- `paper-1.20` requires `libs/paper-server-1.20.6-R0.1-SNAPSHOT-mojang-mapped.jar`.
- `paper-26_1_2` requires a Java 25-capable toolchain because Paper 26.1.2 publishes Java 25 metadata.

## Roadmap

1. Extract direct NMS usage from `core` into `nms-v*` modules.
2. Introduce shared adapter interfaces inside `core`.
3. Make `core` depend only on interfaces instead of direct CraftBukkit/NMS classes.
4. Produce version-specific shaded jars from the appropriate adapter module.
