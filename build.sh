#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [ -x /usr/lib/jvm/java-8-openjdk-amd64/bin/java ]; then
    export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
    export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "[HiveTechModeration] Java:"
java -version 2>&1 | head -n 1 || true

if [ -x "$ROOT/gradlew" ] && [ -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then
    GRADLE=("$ROOT/gradlew")
elif command -v gradle >/dev/null 2>&1; then
    GRADLE=(gradle)
else
    WRAPPER="$(find "$(dirname "$ROOT")" -maxdepth 3 -type f -name gradlew -perm -111 2>/dev/null | grep -v "^$ROOT/gradlew$" | head -n 1 || true)"
    if [ -z "$WRAPPER" ]; then
        echo "ERROR: Gradle/gradlew не найден." >&2
        echo "Положи проект в /opt/hivetech/build/ рядом со старым Forge-проектом или установи Gradle 4.x." >&2
        exit 1
    fi
    echo "[HiveTechModeration] Использую Gradle wrapper: $WRAPPER"
    GRADLE=("$WRAPPER" -p "$ROOT")
fi

"${GRADLE[@]}" clean build

echo
echo "Готово:"
ls -lh "$ROOT"/build/libs/HiveTechModeration-*.jar
