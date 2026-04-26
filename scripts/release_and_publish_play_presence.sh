#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

metadata_file="app/build/outputs/bundle/release/release-info.txt"
release_notes_file_arg="${1:-}"
skip_release_bundle="${PLAY_SKIP_RELEASE_BUNDLE:-false}"

normalize_bool() {
  local value="$1"
  case "${value,,}" in
    1|true|yes|y|on) printf 'true\n' ;;
    0|false|no|n|off) printf 'false\n' ;;
    *)
      printf 'error: expected boolean value, got %s\n' "$value" >&2
      exit 1
      ;;
  esac
}

metadata_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "$metadata_file" | head -n 1
}

if [[ "$(normalize_bool "$skip_release_bundle")" != "true" ]]; then
  bash scripts/release_bundle.sh
fi

if [[ ! -f "$metadata_file" ]]; then
  printf 'error: release metadata file not found: %s\n' "$metadata_file" >&2
  exit 1
fi

release_notes_file="${release_notes_file_arg:-$(metadata_value "release_notes_file")}"

if [[ -z "$release_notes_file" ]]; then
  printf 'error: release_notes_file is missing in %s\n' "$metadata_file" >&2
  exit 1
fi

if [[ ! -f "$release_notes_file" ]]; then
  printf 'error: release notes file not found: %s\n' "$release_notes_file" >&2
  printf 'hint: write release notes in <en-US>/<ko-KR> format, then rerun this command or use PLAY_SKIP_RELEASE_BUNDLE=true.\n' >&2
  exit 1
fi

PLAY_INCLUDE_LISTING="${PLAY_INCLUDE_LISTING:-true}" \
PLAY_INCLUDE_RELEASE="${PLAY_INCLUDE_RELEASE:-true}" \
bash scripts/publish_play_presence.sh "$release_notes_file"

printf 'release and Play presence flow complete\n'
