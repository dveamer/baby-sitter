# Google Play Publish

## 목적

기존 `baby-sitter-release` 흐름에서 만든 AAB와 한영 출시 노트를 그대로 사용해 로컬 환경에서 Google Play 업로드까지 진행할 때 참고한다.

## 전제 조건

- 이 앱은 Play Console에 이미 최초 등록과 최초 업로드가 끝나 있어야 한다.
- Google Cloud Project에서 Google Play Developer API 를 활성화해야 한다.
- Service Account 를 만들고 Play Console `Users & permissions` 에 추가해야 한다.
- 필요한 권한은 최소한 대상 트랙 배포 권한과 스토어 등록 수정 권한을 포함해야 한다.

## 로컬 인증

기본 경로는 Service Account JSON 파일이다.

```bash
export PLAY_SERVICE_ACCOUNT_JSON_PATH=/absolute/path/to/service-account.json
```

스크립트는 위 파일을 읽어 `ANDROID_PUBLISHER_CREDENTIALS` 환경변수로 넘긴다. 이미 `ANDROID_PUBLISHER_CREDENTIALS`를 직접 export 했다면 그대로 재사용한다.

## 릴리즈 노트 파일 형식

출시 노트는 기존 skill 형식을 유지한다.

```text
<en-US>
English release notes
</en-US>
<ko-KR>
한국어 릴리즈 노트
</ko-KR>
```

기본 저장 경로는 `app/build/outputs/bundle/release/release-notes.txt` 이다.

`bash scripts/write_play_release_notes.sh` 는 위 파일을 읽어 아래 track 전용 파일을 만든다.

- `app/src/main/play/release-notes/en-US/<track>.txt`
- `app/src/main/play/release-notes/ko-KR/<track>.txt`

같은 내용을 `app/build/outputs/bundle/release/play-release-notes/` 아래에도 보관한다.

## 권장 실행 순서

1. `bash scripts/release_bundle.sh`
2. `app/build/outputs/bundle/release/release-info.txt` 를 읽고 출시 노트를 작성한다.
3. 출시 노트를 `app/build/outputs/bundle/release/release-notes.txt` 에 저장한다.
4. 아래 예시처럼 배포한다.

전체 배포:

```bash
export PLAY_SERVICE_ACCOUNT_JSON_PATH=/absolute/path/to/service-account.json
PLAY_TRACK=production \
PLAY_RELEASE_STATUS=completed \
bash scripts/publish_play_release.sh
```

staged rollout:

```bash
export PLAY_SERVICE_ACCOUNT_JSON_PATH=/absolute/path/to/service-account.json
PLAY_TRACK=production \
PLAY_RELEASE_STATUS=inProgress \
PLAY_USER_FRACTION=0.1 \
bash scripts/publish_play_release.sh
```

내부 테스트:

```bash
export PLAY_SERVICE_ACCOUNT_JSON_PATH=/absolute/path/to/service-account.json
PLAY_TRACK=internal \
PLAY_RELEASE_STATUS=completed \
bash scripts/publish_play_release.sh
```

## 환경변수

- `PLAY_TRACK`: 기본값 `production`
- `PLAY_RELEASE_STATUS`: 기본값 `completed`
- `PLAY_USER_FRACTION`: staged rollout 일 때만 설정
- `PLAY_UPDATE_PRIORITY`: optional, 정수 0-5
- `PLAY_COMMIT`: optional, `true` 또는 `false`
- `PLAY_METADATA_FILE`: 기본값 `app/build/outputs/bundle/release/release-info.txt`
- `PLAY_RELEASE_NOTES_FILE`: 기본값 `app/build/outputs/bundle/release/release-notes.txt`

## 검토 제출 제한

일반적인 상태에서는 `publishReleaseBundle` commit 이 업로드와 함께 검토 대상으로 넘어간다. 다만 현재 이 저장소에서 검증한 GPP 3.12.1 task 옵션에는 `changesNotSentForReview` 가 노출되지 않는다.

그래서 Google Play 가 `Changes are not sent for review automatically` 상태를 요구하면 이 자동화는 거기서 멈추고, 이후 `Send for review` 단계는 Play Console UI 에서 마무리해야 한다. 이 제한은 skill 문제가 아니라 Google Play 검토 플로우와 현재 플러그인 옵션 범위 제약이다.
