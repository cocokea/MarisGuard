# MarisGuard

MarisGuard is a modular guard plugin for Paper and Folia.

Current modules:

- RayTrace AntiXray
- AntiEsp
- Esper
- AntiFreeCam
- HideEntity
- Player Raytrace

## Wiki

Detailed usage and config notes live here:

- [docs/WIKI.md](docs/WIKI.md)

## Project layout

- `core/` - shared plugin code
- `api/` - common interfaces and bridges
- `nms-v1_20/` - Paper 1.20.x specific adapter layer
- `nms-v1_21/` - Paper 1.21.x specific adapter layer
- `nms-v26_1_2/` - Paper 26.1.2 specific adapter layer
- `gradle/targets/` - per-target build configuration

## Build targets

Use the `marisTarget` Gradle property:

```powershell
./gradlew :core:build "-PmarisTarget=paper-1.20"
./gradlew :core:build "-PmarisTarget=paper-1.21"
./gradlew :core:build "-PmarisTarget=paper-26_1_2"
```

## 2.0 update

- Separate the `config.yml` file into modules
- Optimized configuration for simpler, more user-friendly access
- Improved AntiESP and AntiXray
- Added new modules have been AntiFreeCam and HideEntity
