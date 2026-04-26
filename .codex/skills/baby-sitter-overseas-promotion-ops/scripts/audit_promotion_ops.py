#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Audit overseas promotion credentials and report what Codex can "
            "automate directly, with browser checkpoints, or not yet."
        )
    )
    parser.add_argument("--config", required=True, help="Path to the local JSON config file.")
    parser.add_argument("--output-json", required=True, help="Path to write the JSON report.")
    parser.add_argument("--output-md", required=True, help="Path to write the Markdown report.")
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SystemExit(f"config file not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise SystemExit(f"failed to parse JSON config {path}: {exc}") from exc


def resolve_path(config_path: Path, raw_value: str) -> Path | None:
    if not raw_value:
        return None
    raw_path = Path(raw_value).expanduser()
    if raw_path.is_absolute():
        return raw_path
    return (config_path.parent / raw_path).resolve()


def text_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    return str(value).strip()


def bool_status(value: bool, ok: str, bad: str) -> str:
    return ok if value else bad


def app_root() -> Path:
    return Path(__file__).resolve().parents[4]


def detect_repo_facts(root: Path) -> dict[str, Any]:
    build_gradle = root / "app/build.gradle.kts"
    facts: dict[str, Any] = {
        "root": str(root),
        "build_gradle": str(build_gradle),
        "application_id": "",
        "version_name": "",
        "has_gradle_play_publisher": False,
    }

    if not build_gradle.exists():
        return facts

    content = build_gradle.read_text(encoding="utf-8")
    application_id_match = re.search(r'applicationId\s*=\s*"([^"]+)"', content)
    version_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    facts["application_id"] = application_id_match.group(1) if application_id_match else ""
    facts["version_name"] = version_name_match.group(1) if version_name_match else ""
    facts["has_gradle_play_publisher"] = 'id("com.github.triplet.play")' in content
    return facts


def evaluate_secret(raw_value: str, env_name: str) -> dict[str, Any]:
    value_present = bool(text_value(raw_value))
    env_present = bool(env_name) and bool(os.getenv(env_name))
    ready = value_present or env_present
    source = "inline" if value_present else "env" if env_present else "missing"
    return {"ready": ready, "source": source, "env_name": env_name}


def evaluate_file_presence(config_path: Path, raw_path: str, required_keys: list[str] | None = None) -> dict[str, Any]:
    resolved = resolve_path(config_path, text_value(raw_path))
    result: dict[str, Any] = {
        "path": str(resolved) if resolved else "",
        "exists": False,
        "valid": False,
        "details": [],
    }

    if not resolved:
        result["details"].append("path missing")
        return result

    if not resolved.exists():
        result["details"].append("file missing")
        return result

    result["exists"] = True

    if not required_keys:
        result["valid"] = True
        return result

    try:
        payload = json.loads(resolved.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        result["details"].append("invalid json")
        return result

    missing_keys = [key for key in required_keys if not text_value(payload.get(key))]
    if missing_keys:
        result["details"].append(f"missing keys: {', '.join(missing_keys)}")
        return result

    result["valid"] = True
    return result


def mask_login(value: str) -> str:
    trimmed = text_value(value)
    if not trimmed:
        return ""
    if "@" in trimmed:
        name, domain = trimmed.split("@", 1)
        prefix = name[:2] if len(name) > 2 else name
        return f"{prefix}***@{domain}"
    prefix = trimmed[:2] if len(trimmed) > 2 else trimmed
    return f"{prefix}***"


def evaluate_browser_account(config_path: Path, account_name: str, payload: dict[str, Any]) -> dict[str, Any]:
    username = text_value(payload.get("username"))
    password = text_value(payload.get("password"))
    mode = text_value(payload.get("two_factor_mode")).lower() or "manual_code"
    totp_secret_path = text_value(payload.get("totp_secret_path"))
    totp_secret = evaluate_file_presence(config_path, totp_secret_path) if totp_secret_path else {
        "path": "",
        "exists": False,
        "valid": False,
        "details": [],
    }

    status = "blocked"
    reason = "missing username or password"

    if username and password:
        if mode in {"", "none"}:
            status = "ready"
            reason = "browser login can proceed without an extra checkpoint"
        elif mode == "totp":
            if totp_secret["exists"]:
                status = "ready"
                reason = "browser login can proceed with a local TOTP secret"
            else:
                status = "blocked"
                reason = "TOTP mode selected but the secret file is missing"
        elif mode in {"manual_code", "app_prompt", "security_key"}:
            status = "ready_with_manual_checkpoint"
            reason = f"login is possible but a {mode} checkpoint will need user input"
        else:
            status = "ready_with_manual_checkpoint"
            reason = f"login is possible but two_factor_mode={mode} should be handled manually"

    return {
        "account_name": account_name,
        "label": text_value(payload.get("label")) or account_name,
        "login_url": text_value(payload.get("login_url")),
        "username_masked": mask_login(username),
        "password_present": bool(password),
        "two_factor_mode": mode,
        "totp_secret_path": totp_secret["path"],
        "status": status,
        "reason": reason,
    }


def evaluate_assets(config_path: Path, payload: dict[str, Any]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key in ("screenshots_dir", "videos_dir", "presskit_dir"):
        raw_path = text_value(payload.get(key))
        resolved = resolve_path(config_path, raw_path)
        exists = bool(resolved and resolved.exists() and resolved.is_dir())
        file_count = 0
        if exists:
            file_count = sum(1 for child in resolved.iterdir() if child.is_file())
        result[key] = {
            "path": str(resolved) if resolved else "",
            "exists": exists,
            "file_count": file_count,
            "status": "ready" if exists and file_count > 0 else "blocked",
        }
    return result


def task(name: str, mode: str, status: str, reason: str) -> dict[str, str]:
    return {"name": name, "mode": mode, "status": status, "reason": reason}


def worst_status(statuses: list[str]) -> str:
    order = {"ready": 0, "ready_with_manual_checkpoint": 1, "blocked": 2}
    return max(statuses, key=lambda item: order[item])


def build_report(config_path: Path, config: dict[str, Any]) -> dict[str, Any]:
    root = app_root()
    repo = detect_repo_facts(root)
    listing_source_file = root / "marketing/01-overseas-promotion/play-store-listings.json"
    custom_listing_source_file = root / "marketing/01-overseas-promotion/play-custom-store-listings.json"

    signing = config.get("android_signing", {})
    play_api = config.get("google_play_api", {})
    browser_accounts = config.get("browser_accounts", {})
    assets = config.get("assets", {})
    contact = config.get("contact", {})

    keystore_file = evaluate_file_presence(config_path, text_value(signing.get("keystore_path")))
    keystore_password = evaluate_secret(
        text_value(signing.get("keystore_password")),
        text_value(signing.get("keystore_password_env")),
    )
    key_password = evaluate_secret(
        text_value(signing.get("key_password")),
        text_value(signing.get("key_password_env")),
    )
    key_alias = text_value(signing.get("key_alias"))
    signing_ready = all(
        [
            keystore_file["exists"],
            bool(key_alias),
            keystore_password["ready"],
            key_password["ready"],
        ]
    )

    google_play_credentials = evaluate_file_presence(
        config_path,
        text_value(play_api.get("service_account_json_path")),
        required_keys=["client_email", "private_key", "type"],
    )
    play_api_ready = google_play_credentials["valid"] and repo["has_gradle_play_publisher"]
    listing_source = {
        "path": str(listing_source_file),
        "exists": listing_source_file.exists(),
        "status": "ready" if listing_source_file.exists() else "blocked",
    }
    custom_listing_source = {
        "path": str(custom_listing_source_file),
        "exists": custom_listing_source_file.exists(),
        "status": "ready" if custom_listing_source_file.exists() else "blocked",
    }

    audited_browser_accounts = {
        name: evaluate_browser_account(config_path, name, payload)
        for name, payload in browser_accounts.items()
        if isinstance(payload, dict)
    }

    google_browser = audited_browser_accounts.get("google_primary")
    google_browser_status = google_browser["status"] if google_browser else "blocked"

    asset_state = evaluate_assets(config_path, assets if isinstance(assets, dict) else {})

    support_email = text_value(contact.get("support_email"))
    mailbox = contact.get("mailbox", {}) if isinstance(contact.get("mailbox", {}), dict) else {}
    mailbox_ready = all(
        [
            text_value(mailbox.get("smtp_host")),
            text_value(mailbox.get("username")),
            text_value(mailbox.get("password")),
        ]
    )

    creative_status = worst_status(
        [asset_state["screenshots_dir"]["status"], asset_state["videos_dir"]["status"]]
    )

    tasks = [
        task(
            "signed_release_bundle",
            "local",
            "ready" if signing_ready else "blocked",
            (
                "keystore file, alias, and signing secrets are all present"
                if signing_ready
                else "release signing inputs are incomplete"
            ),
        ),
        task(
            "google_play_api_publish",
            "api",
            "ready" if signing_ready and play_api_ready else "blocked",
            (
                "signed bundle upload can run through the existing Gradle Play Publisher flow"
                if signing_ready and play_api_ready
                else "service account or signing prerequisites are missing"
            ),
        ),
        task(
            "google_play_listing_publish",
            "api",
            "ready" if play_api_ready and listing_source["exists"] else "blocked",
            (
                "listing metadata can be generated and published with Gradle Play Publisher"
                if play_api_ready and listing_source["exists"]
                else "service account or listing source metadata is missing"
            ),
        ),
        task(
            "google_play_custom_listing_prep",
            "local",
            "ready" if custom_listing_source["exists"] else "blocked",
            (
                "custom store listing definitions are ready for Play Console input"
                if custom_listing_source["exists"]
                else "custom store listing source metadata is missing"
            ),
        ),
        task(
            "play_console_browser_ops",
            "browser",
            google_browser_status,
            google_browser["reason"] if google_browser else "Google browser account is missing",
        ),
        task(
            "google_ads_campaign_setup",
            "browser",
            google_browser_status,
            google_browser["reason"] if google_browser else "Google browser account is missing",
        ),
        task(
            "firebase_analytics_review",
            "browser",
            google_browser_status,
            google_browser["reason"] if google_browser else "Google browser account is missing",
        ),
        task(
            "meta_campaign_draft",
            "browser",
            audited_browser_accounts.get("meta_business", {}).get("status", "blocked"),
            audited_browser_accounts.get("meta_business", {}).get(
                "reason",
                "Meta Business credentials are missing",
            ),
        ),
        task(
            "tiktok_campaign_draft",
            "browser",
            audited_browser_accounts.get("tiktok_ads", {}).get("status", "blocked"),
            audited_browser_accounts.get("tiktok_ads", {}).get(
                "reason",
                "TikTok Ads credentials are missing",
            ),
        ),
        task(
            "creative_asset_preflight",
            "local",
            creative_status,
            (
                "screenshot and video asset folders are populated"
                if creative_status == "ready"
                else "screenshot or video assets are still missing"
            ),
        ),
        task(
            "outreach_mailbox_send",
            "api",
            "ready" if mailbox_ready else "blocked",
            (
                "SMTP host and mailbox credentials are present"
                if mailbox_ready
                else "mailbox sending credentials are missing"
            ),
        ),
        task(
            "support_contact_ready",
            "local",
            "ready" if support_email else "blocked",
            "support email is set" if support_email else "support email is missing",
        ),
    ]

    manual_boundaries = [
        "Google 2FA, CAPTCHA, or account recovery challenges can still pause browser automation.",
        "Google Play can still require a manual Console checkpoint such as Send for review depending on the account state.",
        "Community group approval, influencer outreach acceptance, and native-language review remain human-sensitive tasks.",
    ]

    summary_counts = {"ready": 0, "ready_with_manual_checkpoint": 0, "blocked": 0}
    for item in tasks:
        summary_counts[item["status"]] += 1

    return {
        "generated_at_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "config_path": str(config_path.resolve()),
        "repo": repo,
        "project": config.get("project", {}),
        "inputs": {
            "android_signing": {
                "keystore_path": keystore_file["path"],
                "keystore_exists": keystore_file["exists"],
                "key_alias_present": bool(key_alias),
                "keystore_password_source": keystore_password["source"],
                "key_password_source": key_password["source"],
                "status": "ready" if signing_ready else "blocked",
            },
            "google_play_api": {
                "service_account_json_path": google_play_credentials["path"],
                "service_account_exists": google_play_credentials["exists"],
                "service_account_valid": google_play_credentials["valid"],
                "track": text_value(play_api.get("track")) or "production",
                "status": "ready" if play_api_ready else "blocked",
            },
            "google_play_listing_source": listing_source,
            "google_play_custom_listing_source": custom_listing_source,
            "browser_accounts": audited_browser_accounts,
            "assets": asset_state,
            "contact": {
                "support_email_present": bool(support_email),
                "mailbox_ready": bool(mailbox_ready),
            },
        },
        "tasks": tasks,
        "summary_counts": summary_counts,
        "manual_boundaries": manual_boundaries,
    }


def markdown_report(report: dict[str, Any]) -> str:
    project = report.get("project", {})
    repo = report.get("repo", {})
    inputs = report.get("inputs", {})
    browser_accounts = inputs.get("browser_accounts", {})
    assets = inputs.get("assets", {})
    tasks = report.get("tasks", [])
    summary = report.get("summary_counts", {})

    lines: list[str] = []
    lines.append("# Promotion Capability Audit")
    lines.append("")
    lines.append(f"* Generated at: `{report['generated_at_utc']}`")
    lines.append(f"* Config: `{report['config_path']}`")
    lines.append(f"* Repo root: `{repo.get('root', '')}`")
    lines.append("")
    lines.append("## Repo Facts")
    lines.append("")
    lines.append(f"* Application ID: `{repo.get('application_id', '') or 'unknown'}`")
    lines.append(f"* Version name: `{repo.get('version_name', '') or 'unknown'}`")
    lines.append(
        f"* Gradle Play Publisher detected: `{bool_status(repo.get('has_gradle_play_publisher', False), 'yes', 'no')}`"
    )
    lines.append("")
    lines.append("## Project Scope")
    lines.append("")
    lines.append(f"* Countries: `{', '.join(project.get('countries', [])) or 'not set'}`")
    lines.append(f"* Languages: `{', '.join(project.get('languages', [])) or 'not set'}`")
    lines.append("")
    lines.append("## Input Readiness")
    lines.append("")

    signing = inputs.get("android_signing", {})
    lines.append(
        "* Android signing: "
        f"`{signing.get('status', 'blocked')}` "
        f"(keystore exists={signing.get('keystore_exists', False)}, "
        f"alias present={signing.get('key_alias_present', False)}, "
        f"keystore password source={signing.get('keystore_password_source', 'missing')}, "
        f"key password source={signing.get('key_password_source', 'missing')})"
    )

    play_api = inputs.get("google_play_api", {})
    lines.append(
        "* Google Play API: "
        f"`{play_api.get('status', 'blocked')}` "
        f"(service account exists={play_api.get('service_account_exists', False)}, "
        f"valid={play_api.get('service_account_valid', False)}, "
        f"track={play_api.get('track', 'production')})"
    )
    listing_source = inputs.get("google_play_listing_source", {})
    lines.append(
        "* Google Play listing source: "
        f"`{listing_source.get('status', 'blocked')}` "
        f"(path={listing_source.get('path', 'missing')}, exists={listing_source.get('exists', False)})"
    )
    custom_listing_source = inputs.get("google_play_custom_listing_source", {})
    lines.append(
        "* Google Play custom listing source: "
        f"`{custom_listing_source.get('status', 'blocked')}` "
        f"(path={custom_listing_source.get('path', 'missing')}, exists={custom_listing_source.get('exists', False)})"
    )

    if browser_accounts:
        for name, account in browser_accounts.items():
            lines.append(
                "* Browser account "
                f"`{name}`: `{account.get('status', 'blocked')}` "
                f"({account.get('reason', '')}; user={account.get('username_masked', 'missing') or 'missing'})"
            )
    else:
        lines.append("* Browser accounts: `blocked` (none configured)")

    for key, asset in assets.items():
        lines.append(
            f"* Asset `{key}`: `{asset.get('status', 'blocked')}` "
            f"(files={asset.get('file_count', 0)}, path={asset.get('path', '') or 'missing'})"
        )

    contact = inputs.get("contact", {})
    lines.append(
        "* Support contact: "
        f"`{'ready' if contact.get('support_email_present') else 'blocked'}` "
        f"(support email present={contact.get('support_email_present', False)}, "
        f"mailbox ready={contact.get('mailbox_ready', False)})"
    )

    lines.append("")
    lines.append("## Task Matrix")
    lines.append("")
    lines.append(
        f"* Summary: ready={summary.get('ready', 0)}, "
        f"ready_with_manual_checkpoint={summary.get('ready_with_manual_checkpoint', 0)}, "
        f"blocked={summary.get('blocked', 0)}"
    )

    for item in tasks:
        lines.append(
            f"* `{item['name']}` [{item['mode']}] -> `{item['status']}`: {item['reason']}"
        )

    lines.append("")
    lines.append("## Manual Boundaries")
    lines.append("")
    for boundary in report.get("manual_boundaries", []):
        lines.append(f"* {boundary}")

    lines.append("")
    lines.append("## Next Actions")
    lines.append("")

    ready_tasks = [item for item in tasks if item["status"] == "ready"]
    manual_checkpoint_tasks = [
        item for item in tasks if item["status"] == "ready_with_manual_checkpoint"
    ]
    blocked_tasks = [item for item in tasks if item["status"] == "blocked"]

    if ready_tasks:
        lines.append(
            "* Immediate direct work: "
            + ", ".join(f"`{item['name']}`" for item in ready_tasks)
        )
    else:
        lines.append("* Immediate direct work: none yet")

    if manual_checkpoint_tasks:
        lines.append(
            "* Manual checkpoint work: "
            + ", ".join(f"`{item['name']}`" for item in manual_checkpoint_tasks)
        )
    else:
        lines.append("* Manual checkpoint work: none")

    if blocked_tasks:
        lines.append(
            "* Missing inputs first: "
            + ", ".join(f"`{item['name']}`" for item in blocked_tasks)
        )
    else:
        lines.append("* Missing inputs first: none")

    lines.append("")
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    config_path = Path(args.config).expanduser().resolve()
    output_json = Path(args.output_json).expanduser().resolve()
    output_md = Path(args.output_md).expanduser().resolve()

    config = load_json(config_path)
    report = build_report(config_path, config)

    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_md.parent.mkdir(parents=True, exist_ok=True)

    output_json.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    output_md.write_text(markdown_report(report) + "\n", encoding="utf-8")

    print(f"wrote json report: {output_json}")
    print(f"wrote markdown report: {output_md}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
