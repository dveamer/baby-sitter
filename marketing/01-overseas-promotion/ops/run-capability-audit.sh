#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OPS_DIR="$ROOT_DIR/marketing/01-overseas-promotion/ops"
SKILL_SCRIPT="$ROOT_DIR/.codex/skills/baby-sitter-overseas-promotion-ops/scripts/audit_promotion_ops.py"

config_path="${1:-$OPS_DIR/promotion-ops.config.local.json}"

if [[ ! -f "$config_path" ]]; then
  if [[ -z "${1:-}" && -f "$OPS_DIR/promotion-ops.config.template.json" ]]; then
    config_path="$OPS_DIR/promotion-ops.config.template.json"
    printf 'info: local config not found, using template for a dry capability audit: %s\n' "$config_path"
  else
    printf 'error: config file not found: %s\n' "$config_path" >&2
    exit 1
  fi
fi

if [[ ! -f "$SKILL_SCRIPT" ]]; then
  printf 'error: capability audit script not found: %s\n' "$SKILL_SCRIPT" >&2
  exit 1
fi

report_dir="$OPS_DIR/reports"
mkdir -p "$report_dir"

timestamp="$(date +%Y%m%d-%H%M%S)"
json_report="$report_dir/capability-report-$timestamp.json"
md_report="$report_dir/capability-report-$timestamp.md"
latest_json="$report_dir/capability-report-latest.json"
latest_md="$report_dir/capability-report-latest.md"

python3 -B "$SKILL_SCRIPT" \
  --config "$config_path" \
  --output-json "$json_report" \
  --output-md "$md_report"

cp "$json_report" "$latest_json"
cp "$md_report" "$latest_md"

printf 'capability audit complete\n'
printf 'config: %s\n' "$config_path"
printf 'report (markdown): %s\n' "$md_report"
printf 'report (json): %s\n' "$json_report"
printf 'latest markdown report: %s\n' "$latest_md"
