#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [ -x /opt/hivetech/runtime/java8/bin/java ]; then
    export JAVA_HOME=/opt/hivetech/runtime/java8
elif [ -x /usr/lib/jvm/java-8-openjdk-amd64/bin/java ]; then
    export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
fi

if [ -n "${JAVA_HOME:-}" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "[HiveTechModeration] Java:"
java -version 2>&1 | head -n 1

JAVA_VERSION="$(java -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
case "$JAVA_VERSION" in
    1.8.*) ;;
    *)
        echo "ERROR: Для Forge 1.12.2 используем Java 8, сейчас: $JAVA_VERSION" >&2
        exit 1
        ;;
esac

GRADLE_VERSION="4.10.3"
TOOLS_ROOT="${HIVETECH_BUILD_TOOLS:-/opt/hivetech/build/.tools}"
GRADLE_HOME="$TOOLS_ROOT/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$TOOLS_ROOT"
    ZIP="$TOOLS_ROOT/gradle-$GRADLE_VERSION-bin.zip"
    URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

    echo "[HiveTechModeration] Gradle $GRADLE_VERSION не найден, устанавливаю в $TOOLS_ROOT"

    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 --connect-timeout 15 -o "$ZIP" "$URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ZIP" "$URL"
    else
        echo "ERROR: Нужен curl или wget для загрузки Gradle $GRADLE_VERSION." >&2
        exit 1
    fi

    if ! command -v unzip >/dev/null 2>&1; then
        echo "ERROR: Не найден unzip. Установи пакет unzip." >&2
        exit 1
    fi

    rm -rf "$GRADLE_HOME"
    unzip -q "$ZIP" -d "$TOOLS_ROOT"
    rm -f "$ZIP"
fi

echo "[HiveTechModeration] Gradle:"
"$GRADLE_BIN" --version | sed -n '1,8p'

"$GRADLE_BIN" --no-daemon clean build

echo
echo "Готово:"
ls -lh "$ROOT"/build/libs/HiveTechModeration-*.jar
