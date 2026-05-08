#!/usr/bin/env sh

set -eu
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo "gradle-wrapper.jar is not bundled in this export. Run a local Gradle installation once with: gradle wrapper --gradle-version 9.1" >&2
  exit 1
fi
exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
