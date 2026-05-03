#!/usr/bin/env bash
set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MARKETING_DIR="/Users/dveamer/workspace/baby-sitter/marketing"
DEFAULT_DEVTOOLS_PROFILE="$HOME/.cache/chrome-devtools-mcp/chrome-profile"
LOG_FILE="${LOG_FILE:-$SCRIPT_DIR/../.openchrome/kill-openchrome-devtools.log}"
GRACE_SECONDS="${GRACE_SECONDS:-5}"
DRY_RUN=0
FORCE=1

usage() {
  cat <<'EOF'
Usage: kill-openchrome-devtools.sh [--dry-run] [--no-force]

Find and stop Chrome / Chrome for Testing processes that belong to openchrome
or Chrome DevTools MCP automation profiles under:
  /Users/dveamer/workspace/baby-sitter/marketing

Normal user Chrome windows are not targeted unless their command line contains
one of the automation profile paths under that marketing directory.

Environment:
  LOG_FILE       Log path. Default: <repo>/.openchrome/kill-openchrome-devtools.log
  GRACE_SECONDS Seconds to wait after SIGTERM before SIGKILL. Default: 5
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --dry-run)
      DRY_RUN=1
      ;;
    --no-force)
      FORCE=0
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

mkdir -p "$(dirname "$LOG_FILE")"

log() {
  printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')" "$*" | tee -a "$LOG_FILE"
}

is_pid() {
  case "${1:-}" in
    ''|*[!0-9]*)
      return 1
      ;;
    *)
      return 0
      ;;
  esac
}

add_pid() {
  local pid="${1:-}"
  local reason="${2:-matched}"
  local i

  is_pid "$pid" || return 0
  [ "$pid" -ne "$$" ] || return 0

  for i in "${!PIDS[@]}"; do
    if [ "${PIDS[$i]}" = "$pid" ]; then
      PID_REASONS[$i]="${PID_REASONS[$i]}; $reason"
      return 0
    fi
  done

  PIDS+=("$pid")
  PID_REASONS+=("$reason")
}

read_singleton_pid() {
  local profile_dir="$1"
  local lock_path="$profile_dir/SingletonLock"
  local target

  [ -L "$lock_path" ] || return 0
  target="$(readlink "$lock_path" 2>/dev/null || true)"
  case "$target" in
    *-*[0-9])
      printf '%s\n' "${target##*-}"
      ;;
  esac
}

is_marketing_automation_path() {
  local path="${1:-}"

  case "$path" in
    "$DEFAULT_DEVTOOLS_PROFILE")
      return 0
      ;;
    "$MARKETING_DIR"*/.openchrome*|"$MARKETING_DIR"*/.chrome-*|"$MARKETING_DIR"*/.manual-chrome-*|"$MARKETING_DIR"*/.naver*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

append_profile_dir() {
  local profile="${1:-}"

  [ -n "$profile" ] || return 0
  [ -e "$profile" ] || return 0
  is_marketing_automation_path "$profile" || return 0
  PROFILE_DIRS+=("$profile")
}

declare -a PROFILE_DIRS=()
append_profile_dir "$DEFAULT_DEVTOOLS_PROFILE"

if [ -d "$MARKETING_DIR" ]; then
  while IFS= read -r profile; do
    append_profile_dir "$profile"
  done < <(
    find "$MARKETING_DIR" -mindepth 2 -type d \( \
      -name '.openchrome*' -o \
      -name '.chrome-*' -o \
      -name '.manual-chrome-*' -o \
      -name '.naver*' \
    \) 2>/dev/null
  )
fi

declare -a PIDS=()
declare -a PID_REASONS=()

for profile in "${PROFILE_DIRS[@]}"; do
  [ -e "$profile" ] || continue
  singleton_pid="$(read_singleton_pid "$profile")"
  if is_pid "$singleton_pid"; then
    add_pid "$singleton_pid" "SingletonLock at $profile"
  fi
done

while IFS= read -r line; do
  pid="${line%% *}"
  command="${line#* }"

  case "$command" in
    *chrome-devtools-mcp/chrome-profile*|*".cache/chrome-devtools-mcp/chrome-profile"*|*"$MARKETING_DIR"*/.openchrome*|*"$MARKETING_DIR"*/.chrome-*|*"$MARKETING_DIR"*/.manual-chrome-*|*"$MARKETING_DIR"*/.naver*)
      add_pid "$pid" "command: $command"
      ;;
  esac
done < <(ps -axo pid=,command= 2>/dev/null || true)

if [ "${#PIDS[@]}" -eq 0 ]; then
  log "No openchrome / Chrome DevTools MCP processes found."
  exit 0
fi

log "Found ${#PIDS[@]} openchrome / Chrome DevTools MCP process(es): ${PIDS[*]}"
for i in "${!PIDS[@]}"; do
  log "Target pid=${PIDS[$i]} reason=${PID_REASONS[$i]}"
done

if [ "$DRY_RUN" -eq 1 ]; then
  log "Dry run only; no processes killed."
  exit 0
fi

for pid in "${PIDS[@]}"; do
  if kill -0 "$pid" 2>/dev/null; then
    log "Sending SIGTERM to pid=$pid"
    kill -TERM "$pid" 2>/dev/null || log "Failed to SIGTERM pid=$pid"
  fi
done

sleep "$GRACE_SECONDS"

remaining=()
for pid in "${PIDS[@]}"; do
  if kill -0 "$pid" 2>/dev/null; then
    remaining+=("$pid")
  fi
done

if [ "${#remaining[@]}" -gt 0 ] && [ "$FORCE" -eq 1 ]; then
  for pid in "${remaining[@]}"; do
    log "Sending SIGKILL to pid=$pid"
    kill -KILL "$pid" 2>/dev/null || log "Failed to SIGKILL pid=$pid"
  done
fi

sleep 1

failed=()
for pid in "${PIDS[@]}"; do
  if kill -0 "$pid" 2>/dev/null; then
    failed+=("$pid")
  fi
done

if [ "${#failed[@]}" -gt 0 ]; then
  log "Still running after kill attempts: ${failed[*]}"
  exit 1
fi

log "All targeted openchrome / Chrome DevTools MCP processes stopped."
