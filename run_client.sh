#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
    echo "Lỗi: chưa cài JDK hoặc java/javac chưa có trong PATH." >&2
    exit 1
fi

mkdir -p build
javac -d build timeclient.java

if (( $# == 0 )); then
    echo "Mở hộp thoại nhập IP server..."
    exec java -cp build timeclient
fi

SERVER_IP="$1"
echo "Kết nối tới $SERVER_IP:7000..."
exec java -cp build timeclient "$SERVER_IP"
