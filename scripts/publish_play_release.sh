#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

metadata_file="${PLAY_METADATA_FILE:-app/build/outputs/bundle/release/release-info.txt}"
notes_file="${1:-${PLAY_RELEASE_NOTES_FILE:-}}"
track="${PLAY_TRACK:-production}"
release_status="${PLAY_RELEASE_STATUS:-completed}"
user_fraction="${PLAY_USER_FRACTION:-}"
update_priority="${PLAY_UPDATE_PRIORITY:-}"
commit_value="${PLAY_COMMIT:-}"
service_account_json_path="${PLAY_SERVICE_ACCOUNT_JSON_PATH:-}"
publish_info_file="${PLAY_PUBLISH_INFO_FILE:-app/build/outputs/bundle/release/play-publish-info.txt}"
metadata_root="${PLAY_METADATA_DIR:-app/src/main/play}"
archive_dir="${PLAY_RELEASE_NOTES_ARCHIVE_DIR:-app/build/outputs/bundle/release/play-release-notes}"

usage() {
  cat <<'EOF'
usage: bash scripts/publish_play_release.sh [release-notes-file]

Publishes the current release bundle to Google Play with Gradle Play Publisher.

Required:
  - release-info.txt from scripts/release_bundle.sh
  - release notes file in <en-US>/<ko-KR> format
  - ANDROID_PUBLISHER_CREDENTIALS or PLAY_SERVICE_ACCOUNT_JSON_PATH

Optional environment variables:
  PLAY_TRACK                         Default: production
  PLAY_RELEASE_STATUS                Default: completed
  PLAY_USER_FRACTION                 Required only for staged rollout
  PLAY_UPDATE_PRIORITY               Optional integer 0-5
  PLAY_COMMIT                        true/false
  PLAY_METADATA_FILE                 Default: app/build/outputs/bundle/release/release-info.txt
  PLAY_RELEASE_NOTES_FILE            Default from release-info.txt or first positional arg
  PLAY_SERVICE_ACCOUNT_JSON_PATH     Service account JSON file path
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ ! -f "$metadata_file" ]]; then
  printf 'error: metadata file not found: %s\n' "$metadata_file" >&2
  exit 1
fi

metadata_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "$metadata_file" | head -n 1
}

normalize_bool() {
  local value="$1"
  if [[ -z "$value" ]]; then
    return 1
  fi

  case "${value,,}" in
    1|true|yes|y|on) printf 'true\n' ;;
    0|false|no|n|off) printf 'false\n' ;;
    *)
      printf 'error: expected boolean value, got %s\n' "$value" >&2
      exit 1
      ;;
  esac
}

version="$(metadata_value "version")"
bundle_file="$(metadata_value "bundle_file")"
default_notes_file="$(metadata_value "release_notes_file")"
notes_file="${notes_file:-$default_notes_file}"
artifact_dir="$(dirname "$bundle_file")"
normalized_commit="true"

if [[ -z "$version" ]]; then
  printf 'error: failed to read version from %s\n' "$metadata_file" >&2
  exit 1
fi

if [[ -z "$bundle_file" || ! -f "$bundle_file" ]]; then
  printf 'error: bundle file from metadata is missing: %s\n' "$bundle_file" >&2
  exit 1
fi

if [[ -z "$notes_file" || ! -f "$notes_file" ]]; then
  printf 'error: release notes file not found: %s\n' "$notes_file" >&2
  exit 1
fi

if [[ -z "${ANDROID_PUBLISHER_CREDENTIALS:-}" ]]; then
  if [[ -z "$service_account_json_path" ]]; then
    printf 'error: set ANDROID_PUBLISHER_CREDENTIALS or PLAY_SERVICE_ACCOUNT_JSON_PATH before publishing\n' >&2
    exit 1
  fi

  if [[ ! -f "$service_account_json_path" ]]; then
    printf 'error: service account json not found: %s\n' "$service_account_json_path" >&2
    exit 1
  fi

  export ANDROID_PUBLISHER_CREDENTIALS="$(cat "$service_account_json_path")"
fi

track_file_en="$metadata_root/release-notes/en-US/${track}.txt"
track_file_ko="$metadata_root/release-notes/ko-KR/${track}.txt"
restore_dir="$(mktemp -d)"

backup_file() {
  local source_file="$1"
  local backup_file="$2"

  if [[ -f "$source_file" ]]; then
    mkdir -p "$(dirname "$backup_file")"
    cp "$source_file" "$backup_file"
  fi
}

restore_or_remove() {
  local source_file="$1"
  local backup_file="$2"

  if [[ -f "$backup_file" ]]; then
    mkdir -p "$(dirname "$source_file")"
    cp "$backup_file" "$source_file"
  else
    rm -f "$source_file"
  fi
}

cleanup() {
  restore_or_remove "$track_file_en" "$restore_dir/en-US/${track}.txt"
  restore_or_remove "$track_file_ko" "$restore_dir/ko-KR/${track}.txt"
  rm -rf "$restore_dir"
}

trap cleanup EXIT

backup_file "$track_file_en" "$restore_dir/en-US/${track}.txt"
backup_file "$track_file_ko" "$restore_dir/ko-KR/${track}.txt"

PLAY_TRACK="$track" \
PLAY_METADATA_DIR="$metadata_root" \
PLAY_RELEASE_NOTES_ARCHIVE_DIR="$archive_dir" \
bash scripts/write_play_release_notes.sh "$notes_file"

gradle_args=(
  "./gradlew"
  ":app:publishReleaseBundle"
  "--artifact-dir=${artifact_dir}"
  "--track=${track}"
  "--release-status=${release_status}"
)

if [[ -n "$user_fraction" ]]; then
  gradle_args+=("--user-fraction=${user_fraction}")
fi

if [[ -n "$update_priority" ]]; then
  gradle_args+=("--update-priority=${update_priority}")
fi

if [[ -n "$commit_value" ]]; then
  normalized_commit="$(normalize_bool "$commit_value")"
  if [[ "$normalized_commit" == "false" ]]; then
    gradle_args+=("--no-commit")
  fi
fi

"${gradle_args[@]}"

mkdir -p "$(dirname "$publish_info_file")"
cat > "$publish_info_file" <<EOF
version=$version
bundle_file=$bundle_file
artifact_dir=$artifact_dir
track=$track
release_status=$release_status
user_fraction=$user_fraction
update_priority=$update_priority
commit=$normalized_commit
release_notes_file=$notes_file
play_release_notes_archive_dir=$archive_dir
published_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF

printf 'published version %s to Google Play track %s\n' "$version" "$track"
printf 'publish info: %s\n' "$publish_info_file"
