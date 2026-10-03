#!/usr/bin/env bash
# Start Shizuku over USB (no root). Requires: USB debugging on, device connected,
# and the Shizuku app installed on the phone.
set -e
adb get-state >/dev/null 2>&1 || { echo "No device. Enable USB debugging + connect."; exit 1; }
echo "Starting Shizuku..."
adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
echo "Done. Open the app and tap 'Request access'."
