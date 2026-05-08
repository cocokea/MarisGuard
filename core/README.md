# core

Shared module for plugin bootstrap and common logic.

Current temporary state:

- Uses `rootProject/src/main/java` as the active source set.
- Still contains direct NMS usage that must be migrated into `nms-v*`.
- Serves as the main shaded plugin artifact producer for now.
