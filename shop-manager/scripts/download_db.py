#!/usr/bin/env python3
"""
Download the latest shop.db from Google Drive for read-only access.
Run this on your remote web app periodically (e.g., via cron every N minutes).
"""
import os
import sys
import json
import subprocess

GDRIVE_FOLDER_ID = "1Weo9kErWVbTvcscEURVrG6y3syeWm-mQ"
GAPI = "python3 /root/.hermes/skills/productivity/google-workspace/scripts/google_api.py"
DB_FILENAME = "shop-manager-live.db"
OUTPUT_PATH = os.path.join(os.path.dirname(__file__), "shop.db")


def download_db():
    """Download the latest database file from Google Drive."""
    result = subprocess.run(
        f'{GAPI} drive search "shop-manager-live"',
        shell=True, capture_output=True, text=True, timeout=30
    )
    if result.returncode != 0:
        print(f"Error searching files: {result.stderr}")
        return False

    files = json.loads(result.stdout)
    if not files:
        print(f"No file named '{DB_FILENAME}' found in Drive")
        return False

    file_id = files[0]["id"]
    result = subprocess.run(
        f'{GAPI} drive download "{file_id}" --output "{OUTPUT_PATH}"',
        shell=True, capture_output=True, text=True, timeout=60
    )
    if result.returncode != 0:
        print(f"Error downloading: {result.stderr}")
        return False

    print(f"Database saved to {OUTPUT_PATH}")
    return True


if __name__ == "__main__":
    success = download_db()
    sys.exit(0 if success else 1)
