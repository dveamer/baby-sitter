---
name: baby-sitter-overseas-promotion-ops
description: 이 저장소에서 해외 홍보 운영을 직접 진행하거나, 사용자가 계정/비밀번호/서비스 계정 JSON 을 제공한 뒤 어디까지 자동화할 수 있는지 판별할 때 사용하는 skill이다. `marketing/01-overseas-promotion/ops/promotion-ops.config.local.json` 또는 template 를 읽고 capability audit 를 실행해 API 직행 가능 작업, 브라우저 보조 자동화 가능 작업, 사람 체크포인트가 남는 작업을 나눈 뒤, 가능한 범위는 기존 릴리즈/배포 스크립트와 마케팅 문서를 이용해 바로 진행할 때 사용한다.
---

# 해외 홍보 운영 자동화

이 skill은 계정과 비밀번호가 들어온 뒤 "지금 Codex가 어디까지 직접 할 수 있는지"를 먼저 판별하고, 가능한 경로는 바로 실행하는 데 초점을 둔다.

## 시작 순서

1. `marketing/01-overseas-promotion/ops/README.md`를 읽고 로컬 자격 증명 구조를 확인한다.
2. 우선 `bash marketing/01-overseas-promotion/ops/run-capability-audit.sh`를 실행한다.
3. `marketing/01-overseas-promotion/ops/reports/capability-report-latest.md`를 읽고 작업을 세 가지로 나눈다.
   - 로컬 또는 API만으로 바로 가능한 작업
   - 브라우저 자동화가 가능하지만 2FA 같은 체크포인트가 남는 작업
   - 정책, 승인, 원어민 검수 때문에 사람이 직접 마무리해야 하는 작업

## 기본 운영 원칙

* 비밀정보는 `promotion-ops.config.local.json`, `credentials/`, 환경변수에만 둔다. 추적 파일에는 절대 쓰지 않는다.
* 먼저 자동화 가능한 범위를 끝까지 진행하고, 막히는 지점만 짧게 정리한다.
* Google Play API 준비가 되면 기존 `baby-sitter-release` skill을 릴리즈 준비의 기준으로 삼고, `scripts/publish_play_release.sh`, `scripts/release_and_publish_play_presence.sh` 흐름으로 이어간다.
* Google Play listing 텍스트는 `marketing/01-overseas-promotion/play-store-listings.json`을 source of truth 로 보고 `scripts/write_play_store_listings.py`, `scripts/publish_play_listing.sh`로 반영한다.
* country별 custom store listing은 `marketing/01-overseas-promotion/play-custom-store-listings.json`을 source of truth 로 보고 `scripts/write_play_custom_store_listing_guide.py`로 Console 입력 키트를 생성한다.
* Google 계정 로그인만 있고 Service Account 가 없으면, 업로드 API보다는 Play Console / Ads / Firebase 브라우저 보조 자동화를 우선 검토한다.
* `marketing/01-overseas-promotion/` 아래의 스토어 문구, 랜딩 링크, 아웃리치 초안을 재사용한다.

## 이 skill이 판별해야 할 대표 범위

* signed AAB 빌드 가능 여부
* Google Play Developer API 업로드 가능 여부
* Google Play listing publish 가능 여부
* Google Play custom store listing 준비 여부
* Play Console / Google Ads / Firebase 브라우저 보조 자동화 가능 여부
* Meta Business / TikTok Ads 로그인 가능 여부
* 스크린샷/영상/프레스킷 같은 크리에이티브 자산 준비 여부
* 원어민 검수, 관리자 승인, `Send for review`, 2FA 같은 사람 체크포인트

## 브라우저 자동화 해석

* `two_factor_mode=none`이면 바로 진행 가능한 상태로 본다.
* `two_factor_mode=totp` 이고 `totp_secret_path`가 유효하면 진행 가능한 상태로 본다.
* `manual_code`, `app_prompt`, `security_key`는 브라우저 자동화 시도는 가능하지만 사람 체크포인트가 남는 상태로 본다.

## 완료 기준

* capability audit 보고서가 최신으로 생성되어야 한다.
* 보고서 안에 `ready`, `ready_with_manual_checkpoint`, `blocked` 구분이 있어야 한다.
* 자동화 가능한 작업은 보고서만 남기지 말고 실제 진행 가능한 단계까지 이어간다.
* 막힌 지점은 "무엇이 없어서", "어떤 수동 체크포인트 때문에" 멈췄는지 구체적으로 남긴다.
