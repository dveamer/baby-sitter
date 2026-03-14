#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

input_file="${1:-${PLAY_RELEASE_NOTES_FILE:-app/build/outputs/bundle/release/release-notes.txt}}"
track="${PLAY_TRACK:-production}"
metadata_dir="${PLAY_METADATA_DIR:-app/src/main/play}"
archive_dir="${PLAY_RELEASE_NOTES_ARCHIVE_DIR:-app/build/outputs/bundle/release/play-release-notes}"

usage() {
  cat <<'EOF'
usage: bash scripts/write_play_release_notes.sh [release-notes-file]

Reads release notes in the existing skill format and writes Google Play metadata files.

Environment variables:
  PLAY_TRACK                       Track name to target. Default: production
  PLAY_METADATA_DIR                Metadata root for GPP. Default: app/src/main/play
  PLAY_RELEASE_NOTES_ARCHIVE_DIR   Archive directory. Default: app/build/outputs/bundle/release/play-release-notes
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ ! -f "$input_file" ]]; then
  printf 'error: release notes file not found: %s\n' "$input_file" >&2
  exit 1
fi

extract_locale_block() {
  local locale="$1"

  perl -0e '
    my ($locale, $path) = @ARGV;
    local $/;
    open my $fh, "<", $path or die "failed to open $path: $!";
    my $text = <$fh>;
    if ($text =~ m{<\Q$locale\E>\s*(.*?)\s*</\Q$locale\E>}s) {
      my $value = $1;
      $value =~ s/^\s+//;
      $value =~ s/\s+$//;
      print $value;
    }
  ' "$locale" "$input_file"
}

en_notes="$(extract_locale_block "en-US")"
ko_notes="$(extract_locale_block "ko-KR")"

if [[ -z "$en_notes" || -z "$ko_notes" ]]; then
  printf 'error: release notes file must include non-empty <en-US> and <ko-KR> blocks: %s\n' "$input_file" >&2
  exit 1
fi

write_locale_file() {
  local locale="$1"
  local content="$2"
  local metadata_file="$metadata_dir/release-notes/$locale/${track}.txt"
  local archive_file="$archive_dir/$locale/${track}.txt"

  mkdir -p "$(dirname "$metadata_file")" "$(dirname "$archive_file")"
  printf '%s\n' "$content" > "$metadata_file"
  printf '%s\n' "$content" > "$archive_file"

  printf '%s\n' "$metadata_file"
}

en_file="$(write_locale_file "en-US" "$en_notes")"
ko_file="$(write_locale_file "ko-KR" "$ko_notes")"

printf 'play release notes written for track %s\n' "$track"
printf 'source: %s\n' "$input_file"
printf 'en-US: %s\n' "$en_file"
printf 'ko-KR: %s\n' "$ko_file"
printf 'archive: %s\n' "$archive_dir"
