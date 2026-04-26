# Promotion Ops Automation

이 폴더는 나중에 계정, 비밀번호, 서비스 계정 JSON, keystore 정보가 들어왔을 때 Codex가 바로 자동화 범위를 판별하고 가능한 작업을 직접 진행하도록 준비한 운영 폴더입니다.

## 구성

* `promotion-ops.config.template.json`: 로컬 자격 증명 파일 템플릿
* `run-capability-audit.sh`: 현재 입력된 자격 증명으로 어디까지 자동화 가능한지 점검하는 실행 래퍼
* `../play-store-listings.json`: Google Play listing API 업로드용 구조화된 메타데이터 소스
* `../play-custom-store-listings.json`: country별 custom store listing 정의
* `../play-custom-store-listings.generated.md`: Play Console 입력용 custom store listing 키트
* `credentials/`: service account JSON, keystore 같은 민감 파일을 둘 로컬 폴더. git ignore 처리됨
* `reports/`: capability audit 결과가 쌓이는 로컬 폴더. git ignore 처리됨

## 권장 사용 방식

1. `promotion-ops.config.template.json`을 복사해 `promotion-ops.config.local.json`을 만듭니다.
2. Play 서비스 계정 JSON, Android keystore 같은 민감 파일은 `credentials/` 아래에 두거나 절대 경로로 지정합니다.
3. 비밀번호는 가능하면 환경변수로 넣고, 꼭 필요할 때만 `promotion-ops.config.local.json`에 넣습니다.
4. 아래 명령으로 capability audit 를 실행합니다.

```bash
bash marketing/01-overseas-promotion/ops/run-capability-audit.sh
```

5. 생성되는 `reports/capability-report-latest.md`를 보면 아래 세 구간으로 나뉩니다.
   * API 또는 로컬 스크립트만으로 바로 가능한 작업
   * 브라우저 로그인으로 진행 가능하지만 2FA 같은 수동 체크포인트가 남는 작업
   * 정책, 승인, 원어민 검수 때문에 사람 확인이 필요한 작업
6. Google Play Service Account JSON 이 준비되면 아래 명령으로 listing 을 API로 반영할 수 있습니다.

```bash
bash scripts/publish_play_listing.sh
```

7. country별 custom store listing 입력 키트는 아래 명령으로 다시 생성할 수 있습니다.

```bash
python3 scripts/write_play_custom_store_listing_guide.py
```

8. `$baby-sitter-release` 기준으로 버전 갱신과 AAB 생성이 끝났고 릴리즈 노트까지 준비됐다면 아래 명령으로 listing + 앱 배포를 한 번에 진행할 수 있습니다.

```bash
bash scripts/release_and_publish_play_presence.sh app/build/outputs/bundle/release/release-notes.txt
```

## 지금 기준으로 기대할 수 있는 자동화 범위

* `PLAY_SERVICE_ACCOUNT_JSON_PATH` 수준의 권한이 있으면 Play listing 반영과 signed AAB 업로드/track 배포는 대부분 직접 진행 가능합니다.
* 릴리즈 준비와 앱 서명 조건은 `$baby-sitter-release`가 이미 전제로 두는 환경 변수와 산출물에 맞춰 이어지도록 해뒀습니다.
* custom store listing의 country/url/ad group 타게팅은 현재 준비된 입력 키트를 기준으로 Play Console에서 마무리하는 흐름을 전제로 둡니다.
* Google 계정 로그인 정보가 있으면 Play Console, Google Ads, Firebase/Analytics 쪽은 브라우저 보조 자동화 시도가 가능합니다.
* Google 2FA, CAPTCHA, Play Console의 `Send for review` 예외 상태, 커뮤니티 관리자 승인, 원어민 최종 검수는 사람 체크포인트가 남을 수 있습니다.

## 주의

* 이 폴더의 로컬 config 와 `credentials/`, `reports/` 내용은 git 에 올라가지 않도록 설정했습니다.
* 비밀번호가 담긴 값을 커밋하지 말고, 가능하면 환경변수 또는 로컬 파일만 사용하세요.
