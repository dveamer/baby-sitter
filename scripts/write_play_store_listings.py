#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path
from typing import Any

TITLE_LIMIT = 30
SHORT_DESCRIPTION_LIMIT = 80
FULL_DESCRIPTION_LIMIT = 4000


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate Google Play listing metadata files for Gradle Play Publisher."
    )
    parser.add_argument(
        "--source",
        default="marketing/01-overseas-promotion/play-store-listings.json",
        help="Structured JSON source for Play listing metadata.",
    )
    parser.add_argument(
        "--output-dir",
        default="app/src/main/play",
        help="Destination metadata directory for Gradle Play Publisher.",
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate the source without writing files.",
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


def normalize_multiline(value: Any, field_name: str) -> str:
    if isinstance(value, str):
        text = value.strip()
    elif isinstance(value, list):
        text = "\n".join("" if item is None else str(item).rstrip() for item in value).strip()
    else:
        raise SystemExit(f"field must be a string or list of lines: {field_name}")

    if not text:
        raise SystemExit(f"missing required text field: {field_name}")
    return text


def optional_text(value: Any) -> str:
    if isinstance(value, str):
        return value.strip()
    return ""


def validate_listing(locale: str, listing: dict[str, Any]) -> dict[str, str]:
    title = require_text(listing.get("title"), f"listings.{locale}.title")
    short_description = require_text(
        listing.get("short_description"),
        f"listings.{locale}.short_description",
    )
    full_description = normalize_multiline(
        listing.get("full_description"),
        f"listings.{locale}.full_description",
    )
    video_url = optional_text(listing.get("video_url"))

    if len(title) > TITLE_LIMIT:
        raise SystemExit(
            f"listings.{locale}.title exceeds {TITLE_LIMIT} characters: {len(title)}"
        )
    if len(short_description) > SHORT_DESCRIPTION_LIMIT:
        raise SystemExit(
            f"listings.{locale}.short_description exceeds {SHORT_DESCRIPTION_LIMIT} characters: "
            f"{len(short_description)}"
        )
    if len(full_description) > FULL_DESCRIPTION_LIMIT:
        raise SystemExit(
            f"listings.{locale}.full_description exceeds {FULL_DESCRIPTION_LIMIT} characters: "
            f"{len(full_description)}"
        )

    return {
        "title": title,
        "short_description": short_description,
        "full_description": full_description,
        "video_url": video_url,
    }


def write_text_file(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    source_path = Path(args.source).expanduser().resolve()
    output_dir = Path(args.output_dir).expanduser().resolve()
    payload = load_json(source_path)

    default_language = require_text(payload.get("default_language"), "default_language")
    contact = payload.get("contact", {})
    if not isinstance(contact, dict):
        raise SystemExit("contact must be an object")

    listings = payload.get("listings", {})
    if not isinstance(listings, dict) or not listings:
        raise SystemExit("listings must be a non-empty object")

    validated_listings: dict[str, dict[str, str]] = {}
    for locale, listing in listings.items():
        if not isinstance(listing, dict):
            raise SystemExit(f"listings.{locale} must be an object")
        validated_listings[locale] = validate_listing(locale, listing)

    if default_language not in validated_listings:
        raise SystemExit(
            f"default_language {default_language} is missing from the listings object"
        )

    if not args.validate_only:
        write_text_file(output_dir / "default-language.txt", default_language)

        listings_root = output_dir / "listings"
        if listings_root.exists():
            for child in listings_root.iterdir():
                if child.is_dir() and child.name not in validated_listings:
                    shutil.rmtree(child)

        contact_email = optional_text(contact.get("email"))
        contact_phone = optional_text(contact.get("phone"))
        contact_website = optional_text(contact.get("website"))

        if contact_email:
            write_text_file(output_dir / "contact-email.txt", contact_email)
        if contact_phone:
            write_text_file(output_dir / "contact-phone.txt", contact_phone)
        if contact_website:
            write_text_file(output_dir / "contact-website.txt", contact_website)

        for locale, listing in validated_listings.items():
            listing_dir = output_dir / "listings" / locale
            write_text_file(listing_dir / "title.txt", listing["title"])
            write_text_file(
                listing_dir / "short-description.txt",
                listing["short_description"],
            )
            write_text_file(
                listing_dir / "full-description.txt",
                listing["full_description"],
            )
            if listing["video_url"]:
                write_text_file(listing_dir / "video-url.txt", listing["video_url"])

    print(f"source: {source_path}")
    print(f"output_dir: {output_dir}")
    print(f"default_language: {default_language}")
    for locale, listing in validated_listings.items():
        print(
            f"{locale}: title={len(listing['title'])}, "
            f"short={len(listing['short_description'])}, "
            f"full={len(listing['full_description'])}"
        )
    if args.validate_only:
        print("validation: ok")
    else:
        print("write: ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
