#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source_file="${1:-${PLAY_LISTING_SOURCE_FILE:-marketing/01-overseas-promotion/play-store-listings.json}}"
metadata_root="${PLAY_METADATA_DIR:-app/src/main/play}"
service_account_json_path="${PLAY_SERVICE_ACCOUNT_JSON_PATH:-}"
publish_info_file="${PLAY_LISTING_PUBLISH_INFO_FILE:-app/build/outputs/play/listing-publish-info.txt}"

usage() {
  cat <<'EOF'
usage: bash scripts/publish_play_listing.sh [listing-source-json]

Generates Google Play listing metadata and publishes it with Gradle Play Publisher.

Required:
  - listing source JSON
  - ANDROID_PUBLISHER_CREDENTIALS or PLAY_SERVICE_ACCOUNT_JSON_PATH

Optional environment variables:
  PLAY_LISTING_SOURCE_FILE          Default: marketing/01-overseas-promotion/play-store-listings.json
  PLAY_METADATA_DIR                 Default: app/src/main/play
  PLAY_SERVICE_ACCOUNT_JSON_PATH    Service account JSON file path
  PLAY_LISTING_PUBLISH_INFO_FILE    Default: app/build/outputs/play/listing-publish-info.txt
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ ! -f "$source_file" ]]; then
  printf 'error: listing source file not found: %s\n' "$source_file" >&2
  exit 1
fi

python3 -B scripts/write_play_store_listings.py \
  --source "$source_file" \
  --output-dir "$metadata_root"

if [[ -z "${ANDROID_PUBLISHER_CREDENTIALS:-}" ]]; then
  if [[ -z "$service_account_json_path" ]]; then
    printf 'error: set ANDROID_PUBLISHER_CREDENTIALS or PLAY_SERVICE_ACCOUNT_JSON_PATH before publishing listings\n' >&2
    exit 1
  fi

  if [[ ! -f "$service_account_json_path" ]]; then
    printf 'error: service account json not found: %s\n' "$service_account_json_path" >&2
    exit 1
  fi

  export ANDROID_PUBLISHER_CREDENTIALS="$(cat "$service_account_json_path")"
fi

./gradlew :app:publishListing --rerun-tasks

mkdir -p "$(dirname "$publish_info_file")"
cat > "$publish_info_file" <<EOF
listing_source_file=$source_file
metadata_root=$metadata_root
published_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF

printf 'published Google Play listings from %s\n' "$source_file"
printf 'publish info: %s\n' "$publish_info_file"
