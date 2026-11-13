#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

REPO="Khayom1/VEYROST-Winter-Live"
ASSET_URL="https://github.com/${REPO}/releases/download/nightly/app-debug.apk"
DOWNLOAD_DIR="$HOME/storage/downloads"
APK="$DOWNLOAD_DIR/VEYROST-Winter-Live.apk"

echo "== VEYROST Winter Live installer =="
command -v curl >/dev/null 2>&1 || { echo "Install curl: pkg install curl"; exit 1; }
command -v termux-open >/dev/null 2>&1 || { echo "Install termux-tools: pkg install termux-tools"; exit 1; }
mkdir -p "$DOWNLOAD_DIR"

echo "[1/2] Downloading latest nightly APK..."
curl -fL --retry 3 --connect-timeout 20 "$ASSET_URL" -o "$APK"
test -s "$APK" || { echo "Download failed: APK is empty."; exit 1; }

echo "[2/2] Opening Android package installer..."
echo "APK saved at: $APK"
termux-open "$APK"
