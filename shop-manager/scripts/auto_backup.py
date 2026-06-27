#!/usr/bin/env python3
"""
Google Drive Auto-Backup for Shop Manager
Uploads backup JSON to Google Drive folder on every data change.
"""

import os
import sys
import json
import subprocess
from datetime import datetime
from pathlib import Path

# Configuration
BACKUP_DIR = "/root/shop-manager/backups"
DB_PATH = "/root/shop-manager/backend/shop.db"
GAPI = "python3 /root/.hermes/skills/productivity/google-workspace/scripts/google_api.py"
DRIVE_CONFIG = os.path.join(BACKUP_DIR, ".drive_config.json")
FOLDER_NAME = "Shop-Manager-Backups"
MAX_LOCAL_BACKUPS = 10  # Keep last 10 local backups

os.makedirs(BACKUP_DIR, exist_ok=True)

# Default folder ID (fallback if no config exists)
GDRIVE_FOLDER_ID = "1Weo9kErWVbTvcscEURVrG6y3syeWm-mQ"

def load_drive_config():
    """Load saved Drive config or fall back to defaults."""
    global GDRIVE_FOLDER_ID
    try:
        if os.path.exists(DRIVE_CONFIG):
            with open(DRIVE_CONFIG) as f:
                cfg = json.load(f)
            if cfg.get("folder_id"):
                GDRIVE_FOLDER_ID = cfg["folder_id"]
                print(f"Loaded Drive folder from config: {cfg.get('folder_name')} ({GDRIVE_FOLDER_ID})")
    except Exception as e:
        print(f"Warning: Could not load Drive config: {e}")

def save_drive_config(folder_id, folder_name):
    """Persist the working Drive folder ID so all scripts stay in sync."""
    global GDRIVE_FOLDER_ID
    GDRIVE_FOLDER_ID = folder_id
    try:
        with open(DRIVE_CONFIG, "w") as f:
            json.dump({"folder_id": folder_id, "folder_name": folder_name, "updated_at": datetime.now().isoformat()}, f)
        print(f"Saved Drive folder config: {folder_name} ({folder_id})")
    except Exception as e:
        print(f"Warning: Could not save Drive config: {e}")

def ensure_backup_folder():
    """
    Verify the backup folder exists on Drive. If deleted, create a new one.
    This prevents the 'folder not found' issue from breaking backups.
    """
    # Try the configured folder ID first
    probe = subprocess.run(
        f'{GAPI} drive search "{FOLDER_NAME}"',
        shell=True, capture_output=True, text=True, timeout=15
    )
    if probe.returncode == 0:
        try:
            existing = json.loads(probe.stdout)
            if existing:
                folder_id = existing[0]["id"]
                if folder_id != GDRIVE_FOLDER_ID:
                    print(f"Folder ID changed: {GDRIVE_FOLDER_ID} → {folder_id}")
                    save_drive_config(folder_id, FOLDER_NAME)
                else:
                    print(f"Backup folder verified: {FOLDER_NAME} ({folder_id})")
                return folder_id
        except (json.JSONDecodeError, KeyError, IndexError):
            pass

    # Folder not found or error — create a new one
    print(f"Backup folder '{FOLDER_NAME}' not found on Drive. Creating...")
    create = subprocess.run(
        f'{GAPI} drive create-folder "{FOLDER_NAME}"',
        shell=True, capture_output=True, text=True, timeout=15
    )
    if create.returncode == 0:
        try:
            result = json.loads(create.stdout)
            new_id = result.get("id")
            if new_id:
                save_drive_config(new_id, FOLDER_NAME)
                print(f"Created new backup folder: {FOLDER_NAME} ({new_id})")
                return new_id
        except (json.JSONDecodeError, KeyError):
            print(f"Failed to parse folder creation response: {create.stdout[:200]}")
    else:
        print(f"Failed to create folder: {create.stderr[:200]}")

    # Last resort: fall back to current ID
    print(f"Using existing folder ID: {GDRIVE_FOLDER_ID}")
    return GDRIVE_FOLDER_ID


def create_backup_json(data=None):
    """Create a backup JSON file from provided data or from the database."""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"shop-manager-backup-{timestamp}.json"
    filepath = os.path.join(BACKUP_DIR, filename)

    if data is None:
        # Read from the database directly
        data = export_from_database()

    backup = {
        "version": "3.0",
        "exported_at": datetime.now().isoformat(),
        "app": "Shop Manager",
        "data": data,
        "stats": {
            "total_items": len(data.get("items", [])),
            "total_sales": len(data.get("sales", []))
        }
    }

    with open(filepath, "w") as f:
        json.dump(backup, f, indent=2, default=str)

    print(f"Backup saved locally: {filepath}")
    return filepath, filename


def export_from_database():
    """Export data from the SQLite database."""
    import sqlite3

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row

    items = [dict(row) for row in conn.execute("SELECT * FROM items").fetchall()]
    sales = [dict(row) for row in conn.execute("SELECT * FROM daily_sales").fetchall()]

    conn.close()

    return {"items": items, "sales": sales, "settings": {}}


def upload_to_drive(filepath, filename, max_retries=3):
    """Upload a file to Google Drive backup folder with retry."""
    for attempt in range(1, max_retries + 1):
        try:
            result = subprocess.run(
                f'{GAPI} drive upload "{filepath}" --name "{filename}" --parent "{GDRIVE_FOLDER_ID}"',
                shell=True, capture_output=True, text=True, timeout=60
            )
            if result.returncode == 0:
                response = json.loads(result.stdout)
                print(f"Uploaded to Google Drive: {response.get('id')}")
                print(f"Link: {response.get('webViewLink')}")
                return response
            else:
                print(f"Upload attempt {attempt} failed: {result.stderr[:200]}")
        except subprocess.TimeoutExpired:
            print(f"Upload attempt {attempt} timed out")
        except Exception as e:
            print(f"Upload attempt {attempt} error: {e}")
        
        if attempt < max_retries:
            import time
            time.sleep(2 * attempt)  # Exponential backoff
    
    print(f"Upload failed after {max_retries} attempts")
    return None


def upload_db_to_drive():
    """Upload the raw SQLite database file to Google Drive with a fixed name."""
    if not os.path.exists(DB_PATH):
        print(f"Database not found: {DB_PATH}")
        return None
    filename = "shop-manager-live.db"
    print(f"Uploading raw database as '{filename}'...")
    return upload_to_drive(DB_PATH, filename)


def cleanup_old_backups():
    """Remove old local backups, keeping only the most recent ones."""
    backups = sorted(Path(BACKUP_DIR).glob("shop-manager-backup-*.json"))
    if len(backups) > MAX_LOCAL_BACKUPS:
        for old_backup in backups[:-MAX_LOCAL_BACKUPS]:
            old_backup.unlink()
            print(f"Cleaned up old backup: {old_backup.name}")


def auto_backup(data=None):
    """Full backup flow: create JSON, upload to Drive, cleanup old files."""
    print(f"\n{'='*50}")
    print(f"Auto-Backup started at {datetime.now().isoformat()}")
    print(f"{'='*50}")

    # Ensure backup folder exists (recover if deleted)
    ensure_backup_folder()

    # Step 1: Create local backup
    filepath, filename = create_backup_json(data)

    # Step 2: Upload to Google Drive
    result = upload_to_drive(filepath, filename)

    # Step 3: Upload raw database for remote read-only access
    upload_db_to_drive()

    # Step 4: Cleanup old local backups
    cleanup_old_backups()

    if result:
        print(f"\n✅ Backup complete: {filename}")
        print(f"   Google Drive: {result.get('webViewLink', 'N/A')}")
    else:
        print(f"\n⚠️ Local backup saved but Drive upload failed: {filename}")

    return result


# Load saved Drive config on import
load_drive_config()

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--test":
        # Test mode: just create and upload a test backup
        auto_backup()
    elif len(sys.argv) > 1 and sys.argv[1] == "--data":
        # Read JSON data from stdin
        data = json.loads(sys.stdin.read())
        auto_backup(data)
    else:
        # Default: export from database and backup
        auto_backup()
