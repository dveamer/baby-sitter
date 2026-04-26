# Overseas Promotion Execution Pack

이 폴더는 `plan.md`를 실제 집행 단계로 옮기기 위한 초안 묶음입니다.

## 지금 만든 것

* `plan.md`: 원본 전략 문서 + 사용자가 제공해야 할 항목 추가
* `store-listing-drafts.md`: 영어, 인도네시아어, 브라질 포르투갈어, LATAM 스페인어 스토어 문구 초안
* `play-console-paste-ready.md`: Google Play 문자 수 기준에 맞춘 붙여넣기용 제목/짧은 설명/전체 설명
* `play-store-listings.json`: Google Play API / Gradle Play Publisher 용 구조화된 스토어 문구 소스
* `play-custom-store-listings.json`: 국가별 custom store listing 정의 소스
* `play-custom-store-listings.generated.md`: Play Console에 옮길 custom store listing 입력 키트
* `play-custom-store-listings.generated.csv`: custom store listing 요약표
* `country-launch-briefs.md`: 인도네시아, 필리핀, 브라질 1차 테스트용 국가별 운영 브리프
* `campaign-ops-checklist.md`: 출시 전/주간 운영/리뷰 기준 체크리스트
* `landing-links.md`: `?lang=` 과 UTM 규칙, 공유 링크 예시
* `outreach-drafts.md`: 커뮤니티 관리자 승인 요청, 체험단 모집글, 마이크로 인플루언서 DM 초안
* `video-hooks.md`: 15초 숏폼 3종 훅과 장면 구성
* `tester-safe-setup-guide.md`: 부모/테스터에게 전달할 수 있는 안전 설치 가이드 초안
* `screenshot-generator.html`: 로컬에서 열어 Play 스크린샷 카피 시안을 캡처할 수 있는 HTML
* `tracker-template.csv`: 국가별 실험과 반응을 기록할 템플릿
* `ops/`: 자격 증명 입력 후 자동화 가능 범위를 점검하고 직접 집행하기 위한 로컬 운영 폴더

## 권장 실행 순서

1. `plan.md` 하단의 사용자 제공 항목을 채웁니다.
2. `country-launch-briefs.md`에서 1차 가정을 `인도네시아 + 필리핀 + 브라질`로 맞춰 실행 순서를 확인합니다.
3. `play-console-paste-ready.md`를 기준으로 Play Console 문구를 바로 붙여 넣습니다.
4. `play-store-listings.json`과 `scripts/write_play_store_listings.py`로 `app/src/main/play` 메타데이터를 생성합니다.
5. `play-custom-store-listings.json`과 `scripts/write_play_custom_store_listing_guide.py`로 country별 custom store listing 입력 키트를 생성합니다.
6. 홈페이지는 `landing-links.md`의 `?lang=` + UTM 규칙을 따라 광고/커뮤니티 링크를 만듭니다.
7. `video-hooks.md`와 `screenshot-generator.html`을 바탕으로 숏폼과 스크린샷 시안을 만듭니다.
8. `outreach-drafts.md` 문구로 관리자 승인 요청과 체험단 모집을 시작합니다.
9. `tracker-template.csv`에 채널별 반응과 전환을 기록합니다.
10. 서비스 계정 JSON 이 준비되면 `bash scripts/publish_play_listing.sh`로 기본 listing 업데이트를 진행합니다.
11. custom store listing은 `play-custom-store-listings.generated.md`를 기준으로 Play Console에서 생성하고 country/url/ad group targeting을 연결합니다.
12. 앱 업데이트 배포는 `bash scripts/publish_play_release.sh`, 둘을 연속 실행하려면 `PLAY_INCLUDE_RELEASE=true bash scripts/publish_play_presence.sh`를 사용합니다.
13. `$baby-sitter-release` 흐름으로 버전 갱신, AAB 생성, 릴리즈 노트 작성까지 진행한 뒤에는 `bash scripts/release_and_publish_play_presence.sh <release-notes-file>`로 listing + 앱 배포를 이어갈 수 있습니다.
14. 계정, 비밀번호, 서비스 계정 JSON 이 준비되면 `ops/run-capability-audit.sh`로 자동화 가능 범위를 먼저 점검합니다.

## 주의

* 비영어권 문구는 바로 사용하지 말고 원어민 검수를 거치는 편이 안전합니다.
* 설치 안전 문구와 `not a medical device / not a substitute for supervision` 성격의 고지는 랜딩과 스토어 모두에 유지하는 편이 좋습니다.
