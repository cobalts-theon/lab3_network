#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
    echo "Lỗi: chưa cài JDK hoặc java/javac chưa có trong PATH." >&2
    exit 1
fi

mkdir -p build
javac -d build timeserver.java
exec java -cp build timeserver
