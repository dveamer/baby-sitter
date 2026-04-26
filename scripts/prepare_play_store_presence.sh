#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

python3 -B scripts/write_play_store_listings.py
python3 -B scripts/write_play_custom_store_listing_guide.py

printf 'prepared default Play listing metadata and custom store listing kit\n'
