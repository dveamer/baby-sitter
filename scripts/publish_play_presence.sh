#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

include_listing="${PLAY_INCLUDE_LISTING:-true}"
include_release="${PLAY_INCLUDE_RELEASE:-false}"
release_notes_file="${1:-}"

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

if [[ "$(normalize_bool "$include_listing")" == "true" ]]; then
  bash scripts/publish_play_listing.sh
fi

if [[ "$(normalize_bool "$include_release")" == "true" ]]; then
  if [[ -n "$release_notes_file" ]]; then
    bash scripts/publish_play_release.sh "$release_notes_file"
  else
    bash scripts/publish_play_release.sh
  fi
fi

printf 'play presence flow complete (listing=%s, release=%s)\n' "$include_listing" "$include_release"
