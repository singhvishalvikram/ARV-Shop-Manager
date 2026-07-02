#!/usr/bin/env bash
# Timestamped copy of the SQLite DB (+ WAL) with pruning of old backups.
# Intended to run via cron on the server (see 05-free-server-deployment.md §7).
set -euo pipefail

SRC_DIR="${SHOP_DB_DIR:-/data}"
DEST_DIR="${BACKUP_DIR:-/data/backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"

mkdir -p "$DEST_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"

if [ -f "$SRC_DIR/shop.db" ]; then
    cp "$SRC_DIR/shop.db" "$DEST_DIR/shop-$STAMP.db"
    [ -f "$SRC_DIR/shop.db-wal" ] && cp "$SRC_DIR/shop.db-wal" "$DEST_DIR/shop-$STAMP.db-wal"
    echo "Backed up $SRC_DIR/shop.db -> $DEST_DIR/shop-$STAMP.db"
else
    echo "No shop.db found at $SRC_DIR — nothing to back up." >&2
    exit 1
fi

find "$DEST_DIR" -name 'shop-*.db*' -mtime "+$KEEP_DAYS" -delete
