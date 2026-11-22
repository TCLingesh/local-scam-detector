#!/bin/bash
# usage: bash snapshot.sh v2_pooled "short note about this version"
set -e
NAME="$1"
NOTE="$2"
if [ -z "$NAME" ]; then echo "usage: bash snapshot.sh <name> \"note\""; exit 1; fi
DEST="snapshots/$NAME"
if [ -e "$DEST" ]; then echo "ERROR: $DEST already exists. Pick a new name."; exit 1; fi

mkdir -p "$DEST/data"
cp -r models results train export "$DEST/"
cp data/train.csv data/val.csv data/test.csv "$DEST/data/"
[ -f data/ext_raw.csv ] && cp data/ext_raw.csv "$DEST/data/"
cp VERSIONS.md "$DEST/" 2>/dev/null || true
echo "$NOTE" > "$DEST/NOTE.txt"
date >> "$DEST/NOTE.txt"

echo "Saved $DEST:"
ls "$DEST"
du -sh "$DEST"