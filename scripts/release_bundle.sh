#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BUILD_FILE="app/build.gradle.kts"
OUTPUT_DIR="app/build/outputs/bundle/release"
METADATA_FILE="$OUTPUT_DIR/release-info.txt"
RELEASE_NOTES_FILE="$OUTPUT_DIR/release-notes.txt"
PLAY_RELEASE_NOTES_DIR="$OUTPUT_DIR/play-release-notes"
RELEASE_TAG_PREFIX="${RELEASE_TAG_PREFIX:-release-}"
RELEASE_COMMIT_PREFIX="${RELEASE_COMMIT_PREFIX:-릴리즈 }"

if [[ ! -f "$BUILD_FILE" ]]; then
  printf 'error: build file not found: %s\n' "$BUILD_FILE" >&2
  exit 1
fi

today="${RELEASE_DATE:-$(date +%Y%m%d)}"
if [[ ! "$today" =~ ^[0-9]{8}$ ]]; then
  printf 'error: RELEASE_DATE must be YYYYMMDD, got %s\n' "$today" >&2
  exit 1
fi

current_version_code="$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]{10})$/\1/p' "$BUILD_FILE")"
current_version_name="$(sed -nE 's/^[[:space:]]*versionName = "([0-9]{10})"$/\1/p' "$BUILD_FILE")"
version_updated=0

if [[ -z "$current_version_code" || -z "$current_version_name" ]]; then
  printf 'error: failed to parse versionCode/versionName from %s\n' "$BUILD_FILE" >&2
  exit 1
fi

if [[ "$current_version_code" != "$current_version_name" ]]; then
  printf 'error: versionCode (%s) and versionName (%s) do not match\n' \
    "$current_version_code" "$current_version_name" >&2
  exit 1
fi

if ! git diff --quiet -- "$BUILD_FILE" || ! git diff --cached --quiet -- "$BUILD_FILE"; then
  printf 'error: %s has uncommitted changes before release\n' "$BUILD_FILE" >&2
  exit 1
fi

current_date="${current_version_code:0:8}"
current_seq="${current_version_code:8:2}"
notes_head_commit="$(git rev-parse HEAD)"

if (( 10#$current_date > 10#$today )); then
  printf 'error: current version date %s is later than today %s\n' \
    "$current_date" "$today" >&2
  exit 1
fi

if [[ "$current_date" == "$today" ]]; then
  next_seq_value=$((10#$current_seq + 1))
  if (( next_seq_value > 99 )); then
    printf 'error: daily release sequence exceeded 99 for %s\n' "$today" >&2
    exit 1
  fi
  next_version="$(printf '%s%02d' "$today" "$next_seq_value")"
else
  next_version="${today}00"
fi

release_tag="${RELEASE_TAG_PREFIX}${next_version}"
if git rev-parse -q --verify "refs/tags/${release_tag}" >/dev/null; then
  printf 'error: release tag already exists: %s\n' "$release_tag" >&2
  exit 1
fi

release_tags=()
while IFS= read -r tag; do
  release_tags+=("$tag")
done < <(git tag --list "${RELEASE_TAG_PREFIX}*" --sort=-v:refname)

previous_release_tag="${release_tags[0]:-}"
previous_release_ref=""
previous_version_commit=""
previous_version_subject=""
previous_version_time=""
previous_release_version=""
release_notes_base_tag=""
release_notes_base_ref=""
release_notes_base_version=""
release_notes_base_source=""

if [[ -n "$previous_release_tag" ]]; then
  previous_release_ref="$previous_release_tag"
  previous_version_commit="$(git rev-list -n 1 "$previous_release_tag")"
  previous_release_version="${previous_release_tag#${RELEASE_TAG_PREFIX}}"
else
  previous_version_commit="$(git log -G 'version(Code|Name)[[:space:]]*=' --format='%H' -- "$BUILD_FILE" | head -n 1 || true)"
  previous_release_ref="$previous_version_commit"
  previous_release_version="$current_version_code"
fi

if [[ -n "$previous_version_commit" ]]; then
  previous_version_subject="$(git show -s --format='%s' "$previous_version_commit")"
  previous_version_time="$(git show -s --format='%ci' "$previous_version_commit")"
fi

if [[ -n "$previous_release_tag" && "${previous_release_version:0:8}" == "$today" ]]; then
  previous_release_note_meta="$(git for-each-ref --format='%(contents)' "refs/tags/${previous_release_tag}")"
  release_notes_base_tag="$(printf '%s\n' "$previous_release_note_meta" | sed -n 's/^release_notes_base_tag=//p' | head -n 1)"
  release_notes_base_ref="$(printf '%s\n' "$previous_release_note_meta" | sed -n 's/^release_notes_base_ref=//p' | head -n 1)"
  release_notes_base_version="$(printf '%s\n' "$previous_release_note_meta" | sed -n 's/^release_notes_base_version=//p' | head -n 1)"
  if [[ -n "$release_notes_base_ref" ]]; then
    release_notes_base_source="previous_same_day_tag"
  fi
fi

if [[ -z "$release_notes_base_ref" ]]; then
  for tag in "${release_tags[@]}"; do
    [[ -z "$tag" ]] && continue
    tag_version="${tag#${RELEASE_TAG_PREFIX}}"
    tag_date="${tag_version:0:8}"
    if [[ "$tag_date" =~ ^[0-9]{8}$ ]] && (( 10#$tag_date < 10#$today )); then
      release_notes_base_tag="$tag"
      release_notes_base_ref="$tag"
      release_notes_base_version="$tag_version"
      release_notes_base_source="last_prior_day_tag"
      break
    fi
  done
fi

if [[ -z "$release_notes_base_ref" ]]; then
  release_notes_base_tag="$previous_release_tag"
  release_notes_base_ref="$previous_release_ref"
  release_notes_base_version="$previous_release_version"
  if [[ -n "$previous_release_ref" ]]; then
    release_notes_base_source="latest_release_fallback"
  else
    release_notes_base_source="version_commit_fallback"
  fi
fi

restore_version_on_error() {
  if [[ "$version_updated" -eq 1 ]]; then
    perl -0pi -e "s/versionCode = \d{10}/versionCode = ${current_version_code}/; s/versionName = \"\d{10}\"/versionName = \"${current_version_name}\"/;" "$BUILD_FILE"
    printf 'restored version after failure: %s\n' "$current_version_code" >&2
  fi
}

trap restore_version_on_error ERR

perl -0pi -e "s/versionCode = \d{10}/versionCode = ${next_version}/; s/versionName = \"\d{10}\"/versionName = \"${next_version}\"/;" "$BUILD_FILE"
version_updated=1

if [[ -z "${JAVA_HOME:-}" && -d "/opt/homebrew/opt/openjdk@21" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
fi

./gradlew :app:bundleRelease
trap - ERR

git add "$BUILD_FILE"
git commit --only -m "${RELEASE_COMMIT_PREFIX}${next_version}" -- "$BUILD_FILE"
release_commit="$(git rev-parse HEAD)"
git tag -a "$release_tag" \
  -m "Release ${next_version}" \
  -m "release_notes_base_tag=${release_notes_base_tag}" \
  -m "release_notes_base_ref=${release_notes_base_ref}" \
  -m "release_notes_base_version=${release_notes_base_version}" \
  -m "release_notes_base_source=${release_notes_base_source}" \
  -m "release_notes_end=${notes_head_commit}"

mkdir -p "$OUTPUT_DIR"
bundle_file="$(find "$OUTPUT_DIR" -maxdepth 1 -name '*.aab' -print | head -n 1 || true)"

cat > "$METADATA_FILE" <<EOF
version=$next_version
previous_version=$current_version_code
previous_release_version=$previous_release_version
previous_release_tag=$previous_release_tag
previous_release_ref=$previous_release_ref
previous_version_commit=$previous_version_commit
previous_version_commit_subject=$previous_version_subject
previous_version_commit_time=$previous_version_time
release_commit=$release_commit
release_tag=$release_tag
release_notes_base_tag=$release_notes_base_tag
release_notes_base_ref=$release_notes_base_ref
release_notes_base_version=$release_notes_base_version
release_notes_base_source=$release_notes_base_source
release_notes_end=$notes_head_commit
release_notes_range=${release_notes_base_ref}..${notes_head_commit}
release_notes_file=$RELEASE_NOTES_FILE
play_release_notes_dir=$PLAY_RELEASE_NOTES_DIR
bundle_file=$bundle_file
EOF

printf 'release version: %s -> %s\n' "$current_version_code" "$next_version"
printf 'release commit: %s\n' "$release_commit"
printf 'release tag: %s\n' "$release_tag"
if [[ -n "$previous_release_tag" ]]; then
  printf 'previous release tag: %s\n' "$previous_release_tag"
fi
if [[ -n "$release_notes_base_ref" ]]; then
  printf 'release notes base: %s (%s)\n' "$release_notes_base_ref" "$release_notes_base_source"
fi
if [[ -n "$previous_version_commit" ]]; then
  printf 'previous version commit: %s (%s)\n' "$previous_version_commit" "$previous_version_subject"
  printf 'release notes range: %s..%s\n' "$release_notes_base_ref" "$notes_head_commit"
fi
if [[ -n "$bundle_file" ]]; then
  printf 'bundle file: %s\n' "$bundle_file"
fi
printf 'metadata file: %s\n' "$METADATA_FILE"

if command -v open >/dev/null 2>&1; then
  if ! open "$OUTPUT_DIR"; then
    printf 'warning: failed to open %s\n' "$OUTPUT_DIR" >&2
  fi
fi
