#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import re
from pathlib import Path
from typing import Any

URL_PARAMETER_PATTERN = re.compile(r"^[a-z0-9._~-]+$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate a manual Play Console custom store listing kit from structured JSON."
    )
    parser.add_argument(
        "--base-source",
        default="marketing/01-overseas-promotion/play-store-listings.json",
        help="Source JSON for the default Play listing text.",
    )
    parser.add_argument(
        "--custom-source",
        default="marketing/01-overseas-promotion/play-custom-store-listings.json",
        help="Source JSON for Play custom store listing definitions.",
    )
    parser.add_argument(
        "--output-md",
        default="marketing/01-overseas-promotion/play-custom-store-listings.generated.md",
        help="Markdown guide output path.",
    )
    parser.add_argument(
        "--output-csv",
        default="marketing/01-overseas-promotion/play-custom-store-listings.generated.csv",
        help="CSV summary output path.",
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate the JSON inputs without writing files.",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SystemExit(f"source file not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise SystemExit(f"failed to parse JSON source {path}: {exc}") from exc


def require_text(value: Any, field_name: str) -> str:
    if isinstance(value, str):
        text = value.strip()
        if text:
            return text
    raise SystemExit(f"missing required text field: {field_name}")


def optional_text(value: Any) -> str:
    if isinstance(value, str):
        return value.strip()
    return ""


def normalize_lines(value: Any, field_name: str) -> str:
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, list):
        text = "\n".join("" if item is None else str(item).rstrip() for item in value).strip()
        if text:
            return text
    raise SystemExit(f"missing required multiline text field: {field_name}")


def validate_url_parameter(value: str, field_name: str) -> str:
    if not URL_PARAMETER_PATTERN.fullmatch(value):
        raise SystemExit(
            f"{field_name} must match {URL_PARAMETER_PATTERN.pattern}, got: {value}"
        )
    return value


def merge_listing(
    package_name: str,
    base_listings: dict[str, Any],
    payload: dict[str, Any],
) -> dict[str, Any]:
    listing_id = require_text(payload.get("listing_id"), "listing_id")
    display_name = require_text(payload.get("display_name"), f"{listing_id}.display_name")
    default_language = require_text(
        payload.get("default_language"),
        f"{listing_id}.default_language",
    )
    base_locale = require_text(payload.get("base_locale"), f"{listing_id}.base_locale")
    base_listing = base_listings.get(base_locale)
    if not isinstance(base_listing, dict):
        raise SystemExit(f"{listing_id}.base_locale not found in base listing source: {base_locale}")

    countries = payload.get("target_countries")
    if not isinstance(countries, list) or not countries:
        raise SystemExit(f"{listing_id}.target_countries must be a non-empty list")
    countries = [require_text(item, f"{listing_id}.target_countries") for item in countries]

    url_parameter = validate_url_parameter(
        require_text(payload.get("url_parameter"), f"{listing_id}.url_parameter"),
        f"{listing_id}.url_parameter",
    )

    ad_group_ids = payload.get("google_ads_ad_group_ids", [])
    if not isinstance(ad_group_ids, list):
        raise SystemExit(f"{listing_id}.google_ads_ad_group_ids must be a list")
    ad_group_ids = [optional_text(item) for item in ad_group_ids if optional_text(item)]

    title = optional_text(payload.get("title")) or require_text(
        base_listing.get("title"),
        f"base listing {base_locale}.title",
    )
    short_description = optional_text(payload.get("short_description")) or require_text(
        base_listing.get("short_description"),
        f"base listing {base_locale}.short_description",
    )
    full_description = (
        normalize_lines(payload.get("full_description"), f"{listing_id}.full_description")
        if payload.get("full_description") is not None
        else normalize_lines(
            base_listing.get("full_description"),
            f"base listing {base_locale}.full_description",
        )
    )

    notes = payload.get("notes", [])
    if not isinstance(notes, list):
        raise SystemExit(f"{listing_id}.notes must be a list")
    notes = [optional_text(item) for item in notes if optional_text(item)]

    landing_url = optional_text(payload.get("landing_url"))
    play_url = f"https://play.google.com/store/apps/details?id={package_name}&listing={url_parameter}"

    return {
        "listing_id": listing_id,
        "display_name": display_name,
        "default_language": default_language,
        "base_locale": base_locale,
        "target_countries": countries,
        "url_parameter": url_parameter,
        "google_ads_ad_group_ids": ad_group_ids,
        "landing_url": landing_url,
        "play_url": play_url,
        "title": title,
        "short_description": short_description,
        "full_description": full_description,
        "notes": notes,
    }


def build_rows(base_payload: dict[str, Any], custom_payload: dict[str, Any]) -> list[dict[str, Any]]:
    package_name = require_text(custom_payload.get("package_name"), "package_name")

    base_listings = base_payload.get("listings", {})
    if not isinstance(base_listings, dict) or not base_listings:
        raise SystemExit("base-source listings must be a non-empty object")

    listings = custom_payload.get("listings", [])
    if not isinstance(listings, list) or not listings:
        raise SystemExit("custom-source listings must be a non-empty list")

    rows = [merge_listing(package_name, base_listings, payload) for payload in listings]

    seen_ids: set[str] = set()
    seen_url_parameters: set[str] = set()
    seen_countries: dict[str, str] = {}

    for row in rows:
        listing_id = row["listing_id"]
        if listing_id in seen_ids:
            raise SystemExit(f"duplicate listing_id: {listing_id}")
        seen_ids.add(listing_id)

        url_parameter = row["url_parameter"]
        if url_parameter in seen_url_parameters:
            raise SystemExit(f"duplicate url_parameter: {url_parameter}")
        seen_url_parameters.add(url_parameter)

        for country in row["target_countries"]:
            previous = seen_countries.get(country)
            if previous:
                raise SystemExit(
                    f"country {country} is targeted by multiple listings: {previous}, {listing_id}"
                )
            seen_countries[country] = listing_id

    return rows


def markdown(rows: list[dict[str, Any]]) -> str:
    lines: list[str] = []
    lines.append("# Play Custom Store Listings Kit")
    lines.append("")
    lines.append("기본 listing 텍스트 자동화와 별도로, 이 문서는 Play Console의 `Custom store listings` 화면에 바로 옮길 수 있게 준비한 입력 키트입니다.")
    lines.append("")
    lines.append("## Console Rules")
    lines.append("")
    lines.append("* Custom store listing은 최대 50개까지 만들 수 있습니다.")
    lines.append("* `listing` URL 파라미터는 전체 custom listing 사이에서 유일해야 합니다.")
    lines.append("* 한 국가는 한 custom listing에만 연결할 수 있습니다.")
    lines.append("* custom listing은 자동 번역되지 않으므로 기본 언어와 필요한 번역을 직접 넣어야 합니다.")
    lines.append("")

    for row in rows:
        lines.append(f"## {row['display_name']}")
        lines.append("")
        lines.append(f"* Internal ID: `{row['listing_id']}`")
        lines.append(f"* Default language: `{row['default_language']}`")
        lines.append(f"* Base locale source: `{row['base_locale']}`")
        lines.append(f"* Target countries: `{', '.join(row['target_countries'])}`")
        lines.append(f"* URL parameter: `{row['url_parameter']}`")
        lines.append(
            "* Google Ads ad group IDs: "
            + (f"`{', '.join(row['google_ads_ad_group_ids'])}`" if row["google_ads_ad_group_ids"] else "`to be filled later`")
        )
        lines.append(f"* Landing URL: `{row['landing_url'] or 'to be filled later'}`")
        lines.append(f"* Play URL after creation: `{row['play_url']}`")
        lines.append("")
        lines.append("### Copy")
        lines.append("")
        lines.append(f"* Title: `{row['title']}`")
        lines.append(f"* Short description: `{row['short_description']}`")
        lines.append("* Full description:")
        lines.append("")
        lines.append("```text")
        lines.append(row["full_description"])
        lines.append("```")
        if row["notes"]:
            lines.append("")
            lines.append("### Notes")
            lines.append("")
            for note in row["notes"]:
                lines.append(f"* {note}")
        lines.append("")

    return "\n".join(lines)


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(
            [
                "listing_id",
                "display_name",
                "default_language",
                "base_locale",
                "target_countries",
                "url_parameter",
                "google_ads_ad_group_ids",
                "landing_url",
                "play_url",
                "title",
                "short_description",
            ]
        )
        for row in rows:
            writer.writerow(
                [
                    row["listing_id"],
                    row["display_name"],
                    row["default_language"],
                    row["base_locale"],
                    ",".join(row["target_countries"]),
                    row["url_parameter"],
                    ",".join(row["google_ads_ad_group_ids"]),
                    row["landing_url"],
                    row["play_url"],
                    row["title"],
                    row["short_description"],
                ]
            )


def main() -> int:
    args = parse_args()
    base_source = Path(args.base_source).expanduser().resolve()
    custom_source = Path(args.custom_source).expanduser().resolve()
    output_md = Path(args.output_md).expanduser().resolve()
    output_csv = Path(args.output_csv).expanduser().resolve()

    base_payload = load_json(base_source)
    custom_payload = load_json(custom_source)
    rows = build_rows(base_payload, custom_payload)

    if not args.validate_only:
        output_md.parent.mkdir(parents=True, exist_ok=True)
        output_md.write_text(markdown(rows) + "\n", encoding="utf-8")
        write_csv(output_csv, rows)

    print(f"base_source: {base_source}")
    print(f"custom_source: {custom_source}")
    print(f"listing_count: {len(rows)}")
    for row in rows:
        print(
            f"{row['listing_id']}: countries={','.join(row['target_countries'])}, "
            f"url_parameter={row['url_parameter']}, "
            f"title={len(row['title'])}, short={len(row['short_description'])}, "
            f"full={len(row['full_description'])}"
        )
    if args.validate_only:
        print("validation: ok")
    else:
        print(f"output_md: {output_md}")
        print(f"output_csv: {output_csv}")
        print("write: ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
